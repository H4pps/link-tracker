package com.linktracker.scrapper.support;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Singleton Kafka + Schema Registry test cluster shared by all Kafka integration tests in this module.
 *
 * <p>The containers are started once per JVM (singleton-container pattern) and reused across test classes so the
 * whole suite pays the slow Kafka startup cost a single time, as required by the homework.
 */
public final class KafkaTestCluster {

    private static final String KAFKA_NETWORK_ALIAS = "kafka";
    private static final int KAFKA_INTERNAL_PORT = 19092;
    private static final int SCHEMA_REGISTRY_PORT = 8081;

    private static final Network NETWORK = Network.newNetwork();

    private static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("apache/kafka-native:4.1.1"))
            .withNetwork(NETWORK)
            .withListener(KAFKA_NETWORK_ALIAS + ":" + KAFKA_INTERNAL_PORT);

    private static final GenericContainer<?> SCHEMA_REGISTRY = new GenericContainer<>(
                    DockerImageName.parse("confluentinc/cp-schema-registry:8.1.0"))
            .withNetwork(NETWORK)
            .withExposedPorts(SCHEMA_REGISTRY_PORT)
            .withEnv("SCHEMA_REGISTRY_HOST_NAME", "schema-registry")
            .withEnv("SCHEMA_REGISTRY_LISTENERS", "http://0.0.0.0:" + SCHEMA_REGISTRY_PORT)
            .withEnv(
                    "SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS",
                    "PLAINTEXT://" + KAFKA_NETWORK_ALIAS + ":" + KAFKA_INTERNAL_PORT)
            .waitingFor(Wait.forHttp("/subjects").forStatusCode(200));

    static {
        KAFKA.start();
        SCHEMA_REGISTRY.start();
    }

    private KafkaTestCluster() {}

    /**
     * Returns the host-mapped Kafka bootstrap servers.
     *
     * @return bootstrap servers connection string
     */
    public static String bootstrapServers() {
        return KAFKA.getBootstrapServers();
    }

    /**
     * Returns the host-mapped Schema Registry URL.
     *
     * @return schema registry HTTP URL
     */
    public static String schemaRegistryUrl() {
        return "http://" + SCHEMA_REGISTRY.getHost() + ":" + SCHEMA_REGISTRY.getMappedPort(SCHEMA_REGISTRY_PORT);
    }
}
