package backend.academy.linktracker.bot.infrastructure.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import backend.academy.linktracker.bot.BotApplication;
import backend.academy.linktracker.bot.application.update.BotUpdateUseCase;
import backend.academy.linktracker.bot.application.update.LinkUpdateCommand;
import backend.academy.linktracker.bot.support.KafkaTestCluster;
import backend.academy.linktracker.messaging.LinkUpdateEvent;
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Testcontainers integration test for the Bot Kafka consumer: success, validation-to-DLQ, deserialization-to-DLQ, and
 * retry-then-DLQ behavior with a real Kafka broker and Schema Registry.
 */
@Testcontainers
@ActiveProfiles("kafka-it")
@SpringBootTest(classes = BotApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class KafkaConsumerIntegrationTest {

    private static final String LINK_UPDATES_TOPIC = "link-updates";
    private static final String LINK_UPDATES_DLQ_TOPIC = "link-updates-dlq";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18-alpine")
            .withDatabaseName("link_tracker")
            .withUsername("link_tracker")
            .withPassword("link_tracker");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("app.kafka.bootstrap-servers", KafkaTestCluster::bootstrapServers);
        registry.add("app.kafka.schema-registry-url", KafkaTestCluster::schemaRegistryUrl);
        registry.add("spring.kafka.bootstrap-servers", KafkaTestCluster::bootstrapServers);
        registry.add("spring.kafka.producer.properties.schema.registry.url", KafkaTestCluster::schemaRegistryUrl);
        registry.add("spring.kafka.properties.schema.registry.url", KafkaTestCluster::schemaRegistryUrl);
    }

    @MockitoBean
    private BotUpdateUseCase botUpdateUseCase;

    @BeforeAll
    static void createTopics() throws Exception {
        Map<String, Object> adminConfig = Map.of("bootstrap.servers", KafkaTestCluster.bootstrapServers());
        try (Admin admin = Admin.create(adminConfig)) {
            admin.createTopics(List.of(
                            new NewTopic(LINK_UPDATES_TOPIC, 1, (short) 1),
                            new NewTopic(LINK_UPDATES_DLQ_TOPIC, 1, (short) 1)))
                    .all()
                    .get();
        }
    }

    @Test
    void validEventIsProcessedByBotUpdateUseCase() {
        long id = 1001L;
        publishAvro(
                "valid-" + id,
                new LinkUpdateEvent(id, "https://github.com/acme/repo", "changed: new issue", List.of(10L, 20L)));

        Awaitility.await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> verify(botUpdateUseCase, atLeastOnce())
                .processLinkUpdate(argThat(command -> command != null && command.id() == id)));

        verify(botUpdateUseCase, atLeastOnce())
                .processLinkUpdate(argThat(command -> command.id() == id
                        && "https://github.com/acme/repo".equals(command.url())
                        && "changed: new issue".equals(command.description())
                        && List.of(10L, 20L).equals(command.tgChatIds())));
    }

    @Test
    void malformedPayloadIsRoutedToDlqWithoutProcessing() {
        String key = "deser-" + UUID.randomUUID();
        publishRawBytes(key, new byte[] {(byte) 0xFF, 0x01, 0x02, 0x03, 0x04});

        ConsumerRecord<String, byte[]> dlqRecord = awaitDlqRecord(key);
        assertThat(errorType(dlqRecord)).isEqualTo("deserialization");
    }

    @Test
    void invalidEventIsRoutedToDlqAsValidationFailure() {
        long invalidId = 0L;
        String key = "validation-" + UUID.randomUUID();
        publishAvro(key, new LinkUpdateEvent(invalidId, "https://github.com/acme/repo", "desc", List.of(10L)));

        ConsumerRecord<String, byte[]> dlqRecord = awaitDlqRecord(key);
        assertThat(errorType(dlqRecord)).isEqualTo("validation");
        verify(botUpdateUseCase, never()).processLinkUpdate(argThat(command -> command.id() == invalidId));
    }

    @Test
    void processingFailureIsRetriedThenRoutedToDlq() {
        long id = 1004L;
        String key = "processing-" + UUID.randomUUID();
        doAnswer(invocation -> {
                    LinkUpdateCommand command = invocation.getArgument(0);
                    if (command.id() == id) {
                        throw new IllegalStateException("boom");
                    }
                    return null;
                })
                .when(botUpdateUseCase)
                .processLinkUpdate(any());

        publishAvro(key, new LinkUpdateEvent(id, "https://github.com/acme/repo", "desc", List.of(10L)));

        ConsumerRecord<String, byte[]> dlqRecord = awaitDlqRecord(key);
        assertThat(errorType(dlqRecord)).isEqualTo("processing");
        assertThat(header(dlqRecord, "error-attempt")).isEqualTo("2");
        verify(botUpdateUseCase, times(2)).processLinkUpdate(argThat(command -> command.id() == id));
    }

    @Test
    void duplicateMessageIdIsProcessedOnlyOnce() {
        long id = 2002L;
        String messageId = UUID.randomUUID().toString();
        LinkUpdateEvent event = new LinkUpdateEvent(id, "https://github.com/acme/repo", "changed", List.of(10L));

        publishAvro("dup-a-" + id, event, messageId);
        Awaitility.await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> verify(botUpdateUseCase, times(1))
                .processLinkUpdate(argThat(command -> command.id() == id)));

        // Redeliver the same logical event (same message-id) under a different Kafka key.
        publishAvro("dup-b-" + id, event, messageId);
        Awaitility.await()
                .pollDelay(Duration.ofSeconds(3))
                .atMost(Duration.ofSeconds(6))
                .untilAsserted(() ->
                        verify(botUpdateUseCase, times(1)).processLinkUpdate(argThat(command -> command.id() == id)));
    }

    private void publishAvro(String key, LinkUpdateEvent event) {
        publishAvro(key, event, UUID.randomUUID().toString());
    }

    private void publishAvro(String key, LinkUpdateEvent event, String messageId) {
        Map<String, Object> config = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                KafkaTestCluster.bootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                KafkaAvroSerializer.class,
                AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG,
                KafkaTestCluster.schemaRegistryUrl());
        try (KafkaProducer<String, LinkUpdateEvent> producer = new KafkaProducer<>(config)) {
            ProducerRecord<String, LinkUpdateEvent> record = new ProducerRecord<>(LINK_UPDATES_TOPIC, key, event);
            record.headers().add(new RecordHeader("message-id", messageId.getBytes(StandardCharsets.UTF_8)));
            producer.send(record);
            producer.flush();
        }
    }

    private void publishRawBytes(String key, byte[] payload) {
        Map<String, Object> config = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KafkaTestCluster.bootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        try (KafkaProducer<String, byte[]> producer = new KafkaProducer<>(config)) {
            producer.send(new ProducerRecord<>(LINK_UPDATES_TOPIC, key, payload));
            producer.flush();
        }
    }

    private ConsumerRecord<String, byte[]> awaitDlqRecord(String expectedKey) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                KafkaTestCluster.bootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG,
                "dlq-it-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                ByteArrayDeserializer.class);
        try (KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(config)) {
            consumer.subscribe(List.of(LINK_UPDATES_DLQ_TOPIC));
            return Awaitility.await()
                    .atMost(Duration.ofSeconds(20))
                    .until(
                            () -> {
                                ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofMillis(500));
                                for (ConsumerRecord<String, byte[]> record : records) {
                                    if (expectedKey.equals(record.key())) {
                                        return record;
                                    }
                                }
                                return null;
                            },
                            Objects::nonNull);
        }
    }

    private String errorType(ConsumerRecord<String, byte[]> record) {
        return header(record, "error-type");
    }

    private String header(ConsumerRecord<String, byte[]> record, String name) {
        org.apache.kafka.common.header.Header header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
