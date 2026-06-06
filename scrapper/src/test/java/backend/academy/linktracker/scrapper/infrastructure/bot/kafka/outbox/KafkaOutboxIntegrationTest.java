package backend.academy.linktracker.scrapper.infrastructure.bot.kafka.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import backend.academy.linktracker.messaging.LinkUpdateEvent;
import backend.academy.linktracker.scrapper.ScrapperApplication;
import backend.academy.linktracker.scrapper.application.update.LinkUpdateOutboxEvent;
import backend.academy.linktracker.scrapper.application.update.LinkUpdateOutboxRepository;
import backend.academy.linktracker.scrapper.logging.ScrapperLogger;
import backend.academy.linktracker.scrapper.properties.KafkaProperties;
import backend.academy.linktracker.scrapper.support.KafkaTestCluster;
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.ArrayList;
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
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Testcontainers integration test for the Scrapper Transactional Outbox publisher producing Avro events to Kafka.
 */
@Testcontainers
@SpringBootTest(
        classes = ScrapperApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
            "server.port=0",
            "app.grpc.server.port=0",
            "app.bot.mode=kafka",
            "app.scheduler.enabled=false",
            "app.database.access-type=SQL",
            "app.github.token=test-github-token",
            "app.stackoverflow.key=test-stackoverflow-key",
            "app.stackoverflow.access-token=test-stackoverflow-access-token",
            "app.kafka.outbox-publish-interval=3600s"
        })
class KafkaOutboxIntegrationTest {

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
        registry.add("spring.kafka.bootstrap-servers", KafkaTestCluster::bootstrapServers);
        registry.add("spring.kafka.properties.schema.registry.url", KafkaTestCluster::schemaRegistryUrl);
        registry.add("app.kafka.bootstrap-servers", KafkaTestCluster::bootstrapServers);
        registry.add("app.kafka.schema-registry-url", KafkaTestCluster::schemaRegistryUrl);
    }

    @Autowired
    private LinkUpdateOutboxRepository outboxRepository;

    @Autowired
    private KafkaOutboxPublisher publisher;

    @Autowired
    private KafkaProperties kafkaProperties;

    @Autowired
    private ScrapperLogger scrapperLogger;

    @Autowired
    private LinkUpdateOutboxEventMapper outboxEventMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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

    @BeforeEach
    void cleanOutbox() {
        jdbcTemplate.execute("TRUNCATE TABLE link_update_outbox RESTART IDENTITY CASCADE");
    }

    @Test
    void publishesAvroEventToKafkaAndMarksOutboxRowSentOnlyAfterAck() {
        try (KafkaConsumer<String, LinkUpdateEvent> consumer = createAvroConsumer()) {
            consumer.poll(Duration.ofMillis(500));

            outboxRepository.save(LinkUpdateOutboxEvent.pending(
                    77L, "https://github.com/acme/repo", "changed: new issue opened", List.of(101L, 202L)));
            long outboxId = latestOutboxId();
            assertThat(statusOf(outboxId)).isEqualTo("PENDING");

            publisher.publishDueEvents();

            List<ConsumerRecord<String, LinkUpdateEvent>> records = pollRecords(consumer, Duration.ofSeconds(15));
            ConsumerRecord<String, LinkUpdateEvent> record = records.stream()
                    .filter(candidate -> candidate.value().getId() == 77L)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Avro event was not published to " + LINK_UPDATES_TOPIC));
            LinkUpdateEvent event = record.value();

            assertThat(String.valueOf(event.getUrl())).isEqualTo("https://github.com/acme/repo");
            assertThat(String.valueOf(event.getDescription())).isEqualTo("changed: new issue opened");
            assertThat(event.getTgChatIds()).containsExactly(101L, 202L);

            Header messageIdHeader = record.headers().lastHeader("message-id");
            assertThat(messageIdHeader).as("message-id header must be present").isNotNull();
            assertThat(new String(messageIdHeader.value(), StandardCharsets.UTF_8))
                    .isNotBlank();

            assertThat(statusOf(outboxId)).isEqualTo("SENT");
            assertThat(sentAtOf(outboxId)).isNotNull();
        }
    }

    @Test
    void keepsOutboxRowPendingAndRetryableWhenBrokerPublishFails() {
        outboxRepository.save(
                LinkUpdateOutboxEvent.pending(88L, "https://github.com/acme/unreachable", "changed", List.of(303L)));
        long outboxId = latestOutboxId();

        KafkaOutboxPublisher failingPublisher = new KafkaOutboxPublisher(
                outboxRepository, deadBrokerTemplate(), kafkaProperties, scrapperLogger, outboxEventMapper);
        failingPublisher.publishDueEvents();

        assertThat(statusOf(outboxId)).isEqualTo("PENDING");
        assertThat(attemptsOf(outboxId)).isEqualTo(1);
        assertThat(lastErrorOf(outboxId)).isNotBlank();
        assertThat(sentAtOf(outboxId)).isNull();
    }

    private KafkaConsumer<String, LinkUpdateEvent> createAvroConsumer() {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KafkaTestCluster.bootstrapServers());
        config.put(ConsumerConfig.GROUP_ID_CONFIG, "outbox-it-" + UUID.randomUUID());
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaAvroDeserializer.class);
        config.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, KafkaTestCluster.schemaRegistryUrl());
        config.put(KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG, true);
        KafkaConsumer<String, LinkUpdateEvent> consumer = new KafkaConsumer<>(config);
        consumer.subscribe(List.of(LINK_UPDATES_TOPIC));
        return consumer;
    }

    private List<ConsumerRecord<String, LinkUpdateEvent>> pollRecords(
            KafkaConsumer<String, LinkUpdateEvent> consumer, Duration timeout) {
        List<ConsumerRecord<String, LinkUpdateEvent>> collected = new ArrayList<>();
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline && collected.isEmpty()) {
            ConsumerRecords<String, LinkUpdateEvent> records = consumer.poll(Duration.ofMillis(500));
            for (ConsumerRecord<String, LinkUpdateEvent> record : records) {
                collected.add(record);
            }
        }
        return collected;
    }

    private KafkaTemplate<String, LinkUpdateEvent> deadBrokerTemplate() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:1");
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class);
        config.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, KafkaTestCluster.schemaRegistryUrl());
        config.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 2000);
        config.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 2000);
        config.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 1500);
        config.put(ProducerConfig.RETRIES_CONFIG, 0);
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(config));
    }

    private long latestOutboxId() {
        return jdbcTemplate.queryForObject("SELECT id FROM link_update_outbox ORDER BY id DESC LIMIT 1", Long.class);
    }

    private String statusOf(long outboxId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM link_update_outbox WHERE id = ?", String.class, outboxId);
    }

    private int attemptsOf(long outboxId) {
        return jdbcTemplate.queryForObject(
                "SELECT attempts FROM link_update_outbox WHERE id = ?", Integer.class, outboxId);
    }

    private String lastErrorOf(long outboxId) {
        return jdbcTemplate.queryForObject(
                "SELECT last_error FROM link_update_outbox WHERE id = ?", String.class, outboxId);
    }

    private Object sentAtOf(long outboxId) {
        return jdbcTemplate.queryForObject(
                "SELECT sent_at FROM link_update_outbox WHERE id = ?", Timestamp.class, outboxId);
    }
}
