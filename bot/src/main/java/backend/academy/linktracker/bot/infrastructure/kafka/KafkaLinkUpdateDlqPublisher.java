package backend.academy.linktracker.bot.infrastructure.kafka;

import backend.academy.linktracker.bot.properties.KafkaProperties;
import backend.academy.linktracker.messaging.LinkUpdateEvent;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Headers;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes failed Kafka link update messages to the configured DLQ.
 */
@Component
@RequiredArgsConstructor
class KafkaLinkUpdateDlqPublisher {

    private static final String ERROR_TYPE_HEADER = "error-type";
    private static final String ERROR_CLASS_HEADER = "error-class";
    private static final String ERROR_MESSAGE_HEADER = "error-message";
    private static final String ERROR_ATTEMPT_HEADER = "error-attempt";

    private final KafkaProperties kafkaProperties;
    private final KafkaTemplate<String, LinkUpdateEvent> kafkaTemplate;
    private final KafkaTemplate<String, byte[]> kafkaBytesTemplate;

    void publishDeserializationFailure(String key, byte[] payload, Exception exception) {
        ProducerRecord<String, byte[]> dlqRecord =
                new ProducerRecord<>(kafkaProperties.getLinkUpdatesDlqTopic(), key, payload);
        addErrorHeaders(dlqRecord.headers(), "deserialization", exception, null);
        kafkaBytesTemplate.send(dlqRecord);
    }

    void publishValidationFailure(String key, LinkUpdateEvent event, Exception exception) {
        ProducerRecord<String, LinkUpdateEvent> dlqRecord =
                new ProducerRecord<>(kafkaProperties.getLinkUpdatesDlqTopic(), key, event);
        addErrorHeaders(dlqRecord.headers(), "validation", exception, null);
        kafkaTemplate.send(dlqRecord);
    }

    void publishProcessingFailure(String key, LinkUpdateEvent event, Exception exception, int attempt) {
        ProducerRecord<String, LinkUpdateEvent> dlqRecord =
                new ProducerRecord<>(kafkaProperties.getLinkUpdatesDlqTopic(), key, event);
        addErrorHeaders(dlqRecord.headers(), "processing", exception, attempt);
        kafkaTemplate.send(dlqRecord);
    }

    private void addErrorHeaders(Headers headers, String errorType, Exception exception, Integer attempt) {
        headers.add(ERROR_TYPE_HEADER, errorType.getBytes(StandardCharsets.UTF_8));
        headers.add(ERROR_CLASS_HEADER, exception.getClass().getSimpleName().getBytes(StandardCharsets.UTF_8));
        String message = exception.getMessage() == null ? "" : exception.getMessage();
        headers.add(ERROR_MESSAGE_HEADER, message.getBytes(StandardCharsets.UTF_8));
        if (attempt != null) {
            headers.add(ERROR_ATTEMPT_HEADER, String.valueOf(attempt).getBytes(StandardCharsets.UTF_8));
        }
    }
}
