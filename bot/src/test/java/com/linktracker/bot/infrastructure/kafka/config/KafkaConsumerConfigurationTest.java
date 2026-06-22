package com.linktracker.bot.infrastructure.kafka.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.linktracker.bot.infrastructure.kafka.exception.KafkaLinkUpdateDeserializationException;
import com.linktracker.bot.infrastructure.kafka.exception.KafkaLinkUpdateValidationException;
import com.linktracker.bot.properties.KafkaProperties;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.SendResult;

@ExtendWith(MockitoExtension.class)
class KafkaConsumerConfigurationTest {

    @Mock
    private KafkaTemplate<String, byte[]> kafkaBytesTemplate;

    private KafkaConsumerConfiguration configuration;

    @BeforeEach
    void setUp() {
        configuration = new KafkaConsumerConfiguration();
        lenient().when(kafkaBytesTemplate.send(anyProducerRecord())).thenReturn(sendResult());
    }

    @Test
    void rawListenerContainerFactoryUsesConfiguredNativeErrorHandler() {
        KafkaProperties kafkaProperties = kafkaProperties(3, Duration.ZERO);
        DefaultErrorHandler errorHandler = errorHandler(kafkaProperties);

        var factory = configuration.kafkaRawListenerContainerFactory(
                configuration.rawKafkaConsumerFactory(kafkaProperties), errorHandler);
        var container = factory.createContainer("link.processed-updates");

        assertThat(container.getCommonErrorHandler()).isSameAs(errorHandler);
    }

    @Test
    void processingFailureIsRecoveredAfterConfiguredTotalAttempts() {
        KafkaProperties kafkaProperties = kafkaProperties(2, Duration.ZERO);
        DefaultErrorHandler errorHandler = errorHandler(kafkaProperties);
        ConsumerRecord<String, byte[]> record = consumerRecord("key-1", new byte[] {1, 2, 3});

        assertThat(errorHandler.handleOne(new IllegalStateException("telegram unavailable"), record, null, null))
                .isFalse();
        verify(kafkaBytesTemplate, never()).send(anyProducerRecord());

        assertThat(errorHandler.handleOne(new IllegalStateException("telegram unavailable"), record, null, null))
                .isTrue();
        ProducerRecord<String, byte[]> dlqRecord = capturedDlqRecord();

        assertThat(dlqRecord.topic()).isEqualTo("link.processed-updates-dlq");
        assertThat(dlqRecord.partition()).isNull();
        assertThat(dlqRecord.key()).isEqualTo("key-1");
        assertThat(dlqRecord.value()).containsExactly(1, 2, 3);
        assertThat(header(dlqRecord, "error-type")).isEqualTo("processing");
        assertThat(header(dlqRecord, "error-class")).isEqualTo("IllegalStateException");
        assertThat(header(dlqRecord, "error-message")).isEqualTo("telegram unavailable");
        assertThat(header(dlqRecord, "error-attempt")).isEqualTo("2");
    }

    @Test
    void validationFailureIsRecoveredWithoutRetryAttemptHeader() {
        DefaultErrorHandler errorHandler = errorHandler(kafkaProperties(3, Duration.ofSeconds(30)));
        ConsumerRecord<String, byte[]> record = consumerRecord("key-2", new byte[] {4, 5, 6});

        assertThat(errorHandler.handleOne(
                        new KafkaLinkUpdateValidationException(
                                "id must be positive", new IllegalArgumentException("id must be positive")),
                        record,
                        null,
                        null))
                .isTrue();
        ProducerRecord<String, byte[]> dlqRecord = capturedDlqRecord();

        assertThat(header(dlqRecord, "error-type")).isEqualTo("validation");
        assertThat(header(dlqRecord, "error-class")).isEqualTo("IllegalArgumentException");
        assertThat(header(dlqRecord, "error-message")).isEqualTo("id must be positive");
        assertThat(header(dlqRecord, "error-attempt")).isNull();
    }

    @Test
    void deserializationFailureIsRecoveredWithoutRetryAttemptHeader() {
        DefaultErrorHandler errorHandler = errorHandler(kafkaProperties(3, Duration.ofSeconds(30)));
        ConsumerRecord<String, byte[]> record = consumerRecord("key-3", new byte[] {7, 8, 9});

        assertThat(errorHandler.handleOne(
                        new KafkaLinkUpdateDeserializationException(
                                "Failed to deserialize link update event", new IllegalStateException("broken avro")),
                        record,
                        null,
                        null))
                .isTrue();
        ProducerRecord<String, byte[]> dlqRecord = capturedDlqRecord();

        assertThat(header(dlqRecord, "error-type")).isEqualTo("deserialization");
        assertThat(header(dlqRecord, "error-class")).isEqualTo("IllegalStateException");
        assertThat(header(dlqRecord, "error-message")).isEqualTo("broken avro");
        assertThat(header(dlqRecord, "error-attempt")).isNull();
    }

    private DefaultErrorHandler errorHandler(KafkaProperties kafkaProperties) {
        return configuration.kafkaLinkUpdateErrorHandler(
                kafkaProperties,
                configuration.linkUpdateDeadLetterPublishingRecoverer(kafkaProperties, kafkaBytesTemplate));
    }

    private KafkaProperties kafkaProperties(int maxAttempts, Duration retryBackoff) {
        KafkaProperties kafkaProperties = new KafkaProperties();
        kafkaProperties.setBootstrapServers("localhost:9092");
        kafkaProperties.setProcessedUpdatesDlqTopic("link.processed-updates-dlq");
        kafkaProperties.setMaxAttempts(maxAttempts);
        kafkaProperties.setRetryBackoff(retryBackoff);
        return kafkaProperties;
    }

    private ConsumerRecord<String, byte[]> consumerRecord(String key, byte[] payload) {
        return new ConsumerRecord<>("link.processed-updates", 0, 42L, key, payload);
    }

    private ProducerRecord<String, byte[]> capturedDlqRecord() {
        ArgumentCaptor<ProducerRecord<String, byte[]>> captor = producerRecordCaptor();
        verify(kafkaBytesTemplate).send(captor.capture());
        return captor.getValue();
    }

    private String header(ProducerRecord<String, byte[]> record, String name) {
        var header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    private CompletableFuture<SendResult<String, byte[]>> sendResult() {
        return CompletableFuture.completedFuture(null);
    }

    @SuppressWarnings("unchecked")
    private ProducerRecord<String, byte[]> anyProducerRecord() {
        return any(ProducerRecord.class);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private ArgumentCaptor<ProducerRecord<String, byte[]>> producerRecordCaptor() {
        return ArgumentCaptor.forClass((Class) ProducerRecord.class);
    }
}
