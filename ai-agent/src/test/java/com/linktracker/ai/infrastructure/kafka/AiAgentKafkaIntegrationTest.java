package com.linktracker.ai.infrastructure.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import com.linktracker.ai.AiAgentApplication;
import com.linktracker.ai.support.KafkaTestCluster;
import com.linktracker.messaging.ProcessedLinkUpdateEvent;
import com.linktracker.messaging.RawLinkUpdateEvent;
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Testcontainers integration test for the AI Agent: consuming {@code link.raw-updates}, filtering and summarizing,
 * and republishing to {@code link.processed-updates} (TC-1.1), plus malformed-message routing to the DLQ (TC-1.2).
 */
@Testcontainers
@SpringBootTest(
        classes = AiAgentApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
            "ai-agent.filtering.min-length=5",
            "ai-agent.filtering.stop-words=spam",
            "ai-agent.summarization.mode=stub",
            "ai-agent.summarization.threshold=20"
        })
class AiAgentKafkaIntegrationTest {

    private static final String RAW_TOPIC = "link.raw-updates";
    private static final String PROCESSED_TOPIC = "link.processed-updates";
    private static final String RAW_DLQ_TOPIC = "link.raw-updates-dlq";

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("app.kafka.bootstrap-servers", KafkaTestCluster::bootstrapServers);
        registry.add("app.kafka.schema-registry-url", KafkaTestCluster::schemaRegistryUrl);
        registry.add("spring.kafka.bootstrap-servers", KafkaTestCluster::bootstrapServers);
        registry.add("spring.kafka.producer.properties.schema.registry.url", KafkaTestCluster::schemaRegistryUrl);
        registry.add("spring.kafka.properties.schema.registry.url", KafkaTestCluster::schemaRegistryUrl);
    }

    @BeforeAll
    static void createTopics() throws Exception {
        Map<String, Object> adminConfig = Map.of("bootstrap.servers", KafkaTestCluster.bootstrapServers());
        try (Admin admin = Admin.create(adminConfig)) {
            admin.createTopics(List.of(
                            new NewTopic(RAW_TOPIC, 1, (short) 1),
                            new NewTopic(PROCESSED_TOPIC, 1, (short) 1),
                            new NewTopic(RAW_DLQ_TOPIC, 1, (short) 1)))
                    .all()
                    .get();
        }
    }

    @Test
    void validUpdateIsFilteredSummarizedAndRepublished() {
        long id = 5001L;
        String longDescription = "This update description is definitely longer than the summarization threshold";
        publishRaw(
                "valid-" + id,
                new RawLinkUpdateEvent(id, "https://github.com/acme/repo", longDescription, "alice", List.of(10L, 20L)),
                UUID.randomUUID().toString());

        try (KafkaConsumer<String, ProcessedLinkUpdateEvent> consumer = avroConsumer()) {
            ConsumerRecord<String, ProcessedLinkUpdateEvent> record = awaitProcessed(consumer, id);
            ProcessedLinkUpdateEvent event = record.value();

            assertThat(String.valueOf(event.getUrl())).isEqualTo("https://github.com/acme/repo");
            assertThat(event.getTgChatIds()).containsExactly(10L, 20L);
            assertThat(String.valueOf(event.getPriority())).isEqualTo("NORMAL");
            // Stub summarizer truncates to threshold (20) and appends an ellipsis.
            assertThat(String.valueOf(event.getDescription())).hasSize(23).endsWith("...");
            assertThat(String.valueOf(event.getDescription())).isNotEqualTo(longDescription);
        }
    }

    @Test
    void updateWithStopWordIsDroppedAndNotRepublished() {
        long id = 5002L;
        publishRaw(
                "spam-" + id,
                new RawLinkUpdateEvent(
                        id,
                        "https://github.com/acme/repo",
                        "this is spam content please ignore",
                        "alice",
                        List.of(10L)),
                UUID.randomUUID().toString());

        try (KafkaConsumer<String, ProcessedLinkUpdateEvent> consumer = avroConsumer()) {
            assertThat(noProcessedWithin(consumer, id, Duration.ofSeconds(5)))
                    .as("filtered update must not be republished")
                    .isTrue();
        }
    }

    @Test
    void malformedMessageIsRoutedToDlqAndServiceKeepsRunning() {
        String malformedKey = "deser-" + UUID.randomUUID();
        publishRawBytes(malformedKey, new byte[] {(byte) 0xFF, 0x01, 0x02, 0x03, 0x04});

        ConsumerRecord<String, byte[]> dlqRecord = awaitDlq(malformedKey);
        assertThat(headerValue(dlqRecord, "error-type")).isEqualTo("deserialization");

        // The service survives the poison message and still processes a subsequent valid update.
        long id = 5003L;
        publishRaw(
                "after-" + id,
                new RawLinkUpdateEvent(
                        id,
                        "https://github.com/acme/repo",
                        "a valid update after the poison message",
                        "bob",
                        List.of(30L)),
                UUID.randomUUID().toString());
        try (KafkaConsumer<String, ProcessedLinkUpdateEvent> consumer = avroConsumer()) {
            assertThat(awaitProcessed(consumer, id).value().getId()).isEqualTo(id);
        }
    }

    private void publishRaw(String key, RawLinkUpdateEvent event, String messageId) {
        Map<String, Object> config = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                KafkaTestCluster.bootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                KafkaAvroSerializer.class,
                AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG,
                KafkaTestCluster.schemaRegistryUrl());
        try (KafkaProducer<String, RawLinkUpdateEvent> producer = new KafkaProducer<>(config)) {
            ProducerRecord<String, RawLinkUpdateEvent> record = new ProducerRecord<>(RAW_TOPIC, key, event);
            record.headers()
                    .add(new org.apache.kafka.common.header.internals.RecordHeader(
                            "message-id", messageId.getBytes(StandardCharsets.UTF_8)));
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
            producer.send(new ProducerRecord<>(RAW_TOPIC, key, payload));
            producer.flush();
        }
    }

    private KafkaConsumer<String, ProcessedLinkUpdateEvent> avroConsumer() {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KafkaTestCluster.bootstrapServers());
        config.put(ConsumerConfig.GROUP_ID_CONFIG, "processed-it-" + UUID.randomUUID());
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaAvroDeserializer.class);
        config.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, KafkaTestCluster.schemaRegistryUrl());
        config.put(KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG, true);
        KafkaConsumer<String, ProcessedLinkUpdateEvent> consumer = new KafkaConsumer<>(config);
        consumer.subscribe(List.of(PROCESSED_TOPIC));
        return consumer;
    }

    private ConsumerRecord<String, ProcessedLinkUpdateEvent> awaitProcessed(
            KafkaConsumer<String, ProcessedLinkUpdateEvent> consumer, long id) {
        return Awaitility.await()
                .atMost(Duration.ofSeconds(20))
                .until(
                        () -> {
                            ConsumerRecords<String, ProcessedLinkUpdateEvent> records =
                                    consumer.poll(Duration.ofMillis(500));
                            for (ConsumerRecord<String, ProcessedLinkUpdateEvent> record : records) {
                                if (record.value() != null && record.value().getId() == id) {
                                    return record;
                                }
                            }
                            return null;
                        },
                        record -> record != null);
    }

    private boolean noProcessedWithin(
            KafkaConsumer<String, ProcessedLinkUpdateEvent> consumer, long id, Duration window) {
        long deadline = System.nanoTime() + window.toNanos();
        while (System.nanoTime() < deadline) {
            ConsumerRecords<String, ProcessedLinkUpdateEvent> records = consumer.poll(Duration.ofMillis(500));
            for (ConsumerRecord<String, ProcessedLinkUpdateEvent> record : records) {
                if (record.value() != null && record.value().getId() == id) {
                    return false;
                }
            }
        }
        return true;
    }

    private ConsumerRecord<String, byte[]> awaitDlq(String expectedKey) {
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
            consumer.subscribe(List.of(RAW_DLQ_TOPIC));
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
                            record -> record != null);
        }
    }

    private String headerValue(ConsumerRecord<String, byte[]> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
