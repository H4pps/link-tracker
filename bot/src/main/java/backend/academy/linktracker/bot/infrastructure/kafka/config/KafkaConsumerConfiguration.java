package backend.academy.linktracker.bot.infrastructure.kafka.config;

import backend.academy.linktracker.bot.infrastructure.kafka.exception.KafkaLinkUpdateDeserializationException;
import backend.academy.linktracker.bot.infrastructure.kafka.exception.KafkaLinkUpdateValidationException;
import backend.academy.linktracker.bot.properties.KafkaProperties;
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

@Configuration(proxyBeanMethods = false)
class KafkaConsumerConfiguration {

    private static final String ERROR_TYPE_HEADER = "error-type";
    private static final String ERROR_CLASS_HEADER = "error-class";
    private static final String ERROR_MESSAGE_HEADER = "error-message";
    private static final String ERROR_ATTEMPT_HEADER = "error-attempt";

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
            ConsumerFactory<String, byte[]> rawKafkaConsumerFactory, CommonErrorHandler kafkaLinkUpdateErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, byte[]> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(rawKafkaConsumerFactory);
        factory.setCommonErrorHandler(kafkaLinkUpdateErrorHandler);
        return factory;
    }

    @Bean
    DeadLetterPublishingRecoverer linkUpdateDeadLetterPublishingRecoverer(
            KafkaProperties kafkaProperties, KafkaTemplate<String, byte[]> kafkaBytesTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaBytesTemplate,
                (record, exception) -> new TopicPartition(kafkaProperties.getProcessedUpdatesDlqTopic(), -1));
        recoverer.setHeadersFunction((record, exception) -> dlqHeaders(kafkaProperties, exception));
        return recoverer;
    }

    @Bean
    DefaultErrorHandler kafkaLinkUpdateErrorHandler(
            KafkaProperties kafkaProperties, DeadLetterPublishingRecoverer linkUpdateDeadLetterPublishingRecoverer) {
        FixedBackOff backOff = new FixedBackOff(
                kafkaProperties.getRetryBackoff().toMillis(), Math.max(0, kafkaProperties.getMaxAttempts() - 1L));
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(linkUpdateDeadLetterPublishingRecoverer, backOff);
        errorHandler.addNotRetryableExceptions(
                KafkaLinkUpdateDeserializationException.class, KafkaLinkUpdateValidationException.class);
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
    ProducerFactory<String, ProcessedLinkUpdateEvent> kafkaAvroProducerFactory(KafkaProperties kafkaProperties) {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.getBootstrapServers());
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class);
        config.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, kafkaProperties.getSchemaRegistryUrl());
        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    KafkaTemplate<String, ProcessedLinkUpdateEvent> kafkaTemplate(
            ProducerFactory<String, ProcessedLinkUpdateEvent> kafkaAvroProducerFactory) {
        return new KafkaTemplate<>(kafkaAvroProducerFactory);
    }

    private Headers dlqHeaders(KafkaProperties kafkaProperties, Exception exception) {
        Throwable classifiedException = unwrapListenerException(exception);
        String errorType = errorType(classifiedException);
        Throwable headerException = headerException(classifiedException);

        RecordHeaders headers = new RecordHeaders();
        addHeader(headers, ERROR_TYPE_HEADER, errorType);
        addHeader(headers, ERROR_CLASS_HEADER, headerException.getClass().getSimpleName());
        addHeader(
                headers,
                ERROR_MESSAGE_HEADER,
                headerException.getMessage() == null ? "" : headerException.getMessage());
        if ("processing".equals(errorType)) {
            addHeader(headers, ERROR_ATTEMPT_HEADER, String.valueOf(kafkaProperties.getMaxAttempts()));
        }
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
        if (exception instanceof KafkaLinkUpdateDeserializationException) {
            return "deserialization";
        }
        if (exception instanceof KafkaLinkUpdateValidationException) {
            return "validation";
        }
        return "processing";
    }

    private Throwable headerException(Throwable exception) {
        if ((exception instanceof KafkaLinkUpdateDeserializationException
                        || exception instanceof KafkaLinkUpdateValidationException)
                && exception.getCause() != null) {
            return exception.getCause();
        }
        return exception;
    }

    private void addHeader(Headers headers, String name, String value) {
        headers.add(new RecordHeader(name, value.getBytes(StandardCharsets.UTF_8)));
    }
}
