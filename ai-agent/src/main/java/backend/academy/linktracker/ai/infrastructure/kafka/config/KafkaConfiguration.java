package backend.academy.linktracker.ai.infrastructure.kafka.config;

import backend.academy.linktracker.ai.infrastructure.kafka.exception.AiAgentDeserializationException;
import backend.academy.linktracker.ai.properties.KafkaProperties;
import backend.academy.linktracker.messaging.ProcessedLinkUpdateEvent;
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import org.springframework.kafka.listener.TimestampedException;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Kafka consumer/producer wiring for the AI Agent: a raw byte[] listener with manual Avro decoding, a
 * DefaultErrorHandler routing malformed messages to a DLQ, and an Avro producer for processed updates.
 */
@Configuration(proxyBeanMethods = false)
class KafkaConfiguration {

    private static final String ERROR_TYPE_HEADER = "error-type";
    private static final String ERROR_CLASS_HEADER = "error-class";
    private static final String ERROR_MESSAGE_HEADER = "error-message";

    @Bean(destroyMethod = "close")
    KafkaAvroDeserializer kafkaAvroDeserializer(KafkaProperties kafkaProperties) {
        KafkaAvroDeserializer deserializer = new KafkaAvroDeserializer();
        deserializer.configure(
                Map.of("schema.registry.url", kafkaProperties.getSchemaRegistryUrl(), "specific.avro.reader", true),
                false);
        return deserializer;
    }

    @Bean
    ConsumerFactory<String, byte[]> rawKafkaConsumerFactory(KafkaProperties kafkaProperties) {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.getBootstrapServers());
        config.put(ConsumerConfig.GROUP_ID_CONFIG, kafkaProperties.getConsumerGroup());
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return new DefaultKafkaConsumerFactory<>(config);
    }

    @Bean
    ConcurrentKafkaListenerContainerFactory<String, byte[]> kafkaRawListenerContainerFactory(
            ConsumerFactory<String, byte[]> rawKafkaConsumerFactory, CommonErrorHandler aiAgentErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, byte[]> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(rawKafkaConsumerFactory);
        factory.setCommonErrorHandler(aiAgentErrorHandler);
        return factory;
    }

    @Bean
    DeadLetterPublishingRecoverer rawUpdateDeadLetterPublishingRecoverer(
            KafkaProperties kafkaProperties, KafkaTemplate<String, byte[]> kafkaBytesTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaBytesTemplate,
                (record, exception) -> new TopicPartition(kafkaProperties.getRawUpdatesDlqTopic(), -1));
        recoverer.setHeadersFunction((record, exception) -> dlqHeaders(exception));
        return recoverer;
    }

    @Bean
    DefaultErrorHandler aiAgentErrorHandler(
            KafkaProperties kafkaProperties, DeadLetterPublishingRecoverer rawUpdateDeadLetterPublishingRecoverer) {
        FixedBackOff backOff = new FixedBackOff(
                kafkaProperties.getRetryBackoff().toMillis(), Math.max(0, kafkaProperties.getMaxAttempts() - 1L));
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(rawUpdateDeadLetterPublishingRecoverer, backOff);
        errorHandler.addNotRetryableExceptions(AiAgentDeserializationException.class);
        return errorHandler;
    }

    @Bean
    ProducerFactory<String, byte[]> kafkaBytesProducerFactory(KafkaProperties kafkaProperties) {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.getBootstrapServers());
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    KafkaTemplate<String, byte[]> kafkaBytesTemplate(ProducerFactory<String, byte[]> kafkaBytesProducerFactory) {
        return new KafkaTemplate<>(kafkaBytesProducerFactory);
    }

    @Bean
    ProducerFactory<String, ProcessedLinkUpdateEvent> processedUpdateProducerFactory(KafkaProperties kafkaProperties) {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.getBootstrapServers());
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class);
        config.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, kafkaProperties.getSchemaRegistryUrl());
        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    KafkaTemplate<String, ProcessedLinkUpdateEvent> processedUpdateKafkaTemplate(
            ProducerFactory<String, ProcessedLinkUpdateEvent> processedUpdateProducerFactory) {
        return new KafkaTemplate<>(processedUpdateProducerFactory);
    }

    private Headers dlqHeaders(Exception exception) {
        Throwable classifiedException = unwrapListenerException(exception);
        Throwable headerException = headerException(classifiedException);

        RecordHeaders headers = new RecordHeaders();
        addHeader(headers, ERROR_TYPE_HEADER, errorType(classifiedException));
        addHeader(headers, ERROR_CLASS_HEADER, headerException.getClass().getSimpleName());
        addHeader(
                headers,
                ERROR_MESSAGE_HEADER,
                headerException.getMessage() == null ? "" : headerException.getMessage());
        return headers;
    }

    private Throwable unwrapListenerException(Exception exception) {
        Throwable current = exception;
        while ((current instanceof ListenerExecutionFailedException || current instanceof TimestampedException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private String errorType(Throwable exception) {
        if (exception instanceof AiAgentDeserializationException) {
            return "deserialization";
        }
        return "processing";
    }

    private Throwable headerException(Throwable exception) {
        if (exception instanceof AiAgentDeserializationException && exception.getCause() != null) {
            return exception.getCause();
        }
        return exception;
    }

    private void addHeader(Headers headers, String name, String value) {
        headers.add(new RecordHeader(name, value.getBytes(StandardCharsets.UTF_8)));
    }
}
