package backend.academy.linktracker.bot.infrastructure.kafka;

import backend.academy.linktracker.bot.application.update.BotUpdateUseCase;
import backend.academy.linktracker.bot.application.update.LinkUpdateCommand;
import backend.academy.linktracker.bot.properties.KafkaProperties;
import backend.academy.linktracker.messaging.LinkUpdateEvent;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Headers;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for link update events.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.kafka", name = "enabled", havingValue = "true", matchIfMissing = true)
public class KafkaLinkUpdateConsumer {

    private static final String ERROR_TYPE_HEADER = "error-type";
    private static final String ERROR_CLASS_HEADER = "error-class";
    private static final String ERROR_MESSAGE_HEADER = "error-message";
    private static final String ERROR_ATTEMPT_HEADER = "error-attempt";

    private final BotUpdateUseCase botUpdateUseCase;
    private final KafkaProperties kafkaProperties;
    private final KafkaAvroDeserializer kafkaAvroDeserializer;
    private final KafkaTemplate<String, LinkUpdateEvent> kafkaTemplate;
    private final KafkaTemplate<String, byte[]> kafkaBytesTemplate;

    @KafkaListener(
            topics = "${app.kafka.link-updates-topic:link-updates}",
            groupId = "${app.kafka.consumer-group:link-tracker-bot}",
            containerFactory = "kafkaRawListenerContainerFactory")
    public void listen(
            @Payload byte[] payload, @Header(name = KafkaHeaders.RECEIVED_KEY, required = false) String key) {
        LinkUpdateEvent event;
        try {
            event = deserialize(payload);
        } catch (RuntimeException exception) {
            publishDeserializationFailure(key, payload, exception);
            return;
        }

        try {
            validate(event);
        } catch (IllegalArgumentException exception) {
            publishValidationFailure(key, event, exception);
            return;
        }

        consumeWithRetry(key, event);
    }

    public void consume(LinkUpdateEvent event) {
        validate(event);
        botUpdateUseCase.processLinkUpdate(toCommand(event));
    }

    public LinkUpdateCommand toCommand(LinkUpdateEvent event) {
        return new LinkUpdateCommand(
                event.getId(),
                String.valueOf(event.getUrl()),
                String.valueOf(event.getDescription()),
                List.copyOf(event.getTgChatIds()));
    }

    public void validate(LinkUpdateEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("event must not be null");
        }
        if (event.getId() <= 0) {
            throw new IllegalArgumentException("id must be positive");
        }
        if (String.valueOf(event.getUrl()).isBlank()) {
            throw new IllegalArgumentException("url must not be blank");
        }
        if (event.getTgChatIds() == null || event.getTgChatIds().isEmpty()) {
            throw new IllegalArgumentException("tgChatIds must not be empty");
        }
        if (event.getTgChatIds().stream().anyMatch(chatId -> chatId == null || chatId <= 0)) {
            throw new IllegalArgumentException("tgChatIds must contain only positive values");
        }
    }

    private void consumeWithRetry(String key, LinkUpdateEvent event) {
        int maxAttempts = kafkaProperties.getMaxAttempts();
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                consume(event);
                return;
            } catch (IllegalArgumentException exception) {
                publishValidationFailure(key, event, exception);
                return;
            } catch (RuntimeException exception) {
                if (attempt == maxAttempts) {
                    publishProcessingFailure(key, event, exception, attempt);
                    return;
                }
                if (!sleepBeforeRetry()) {
                    publishProcessingFailure(key, event, exception, attempt);
                    return;
                }
            }
        }
    }

    private boolean sleepBeforeRetry() {
        long backoffMillis = kafkaProperties.getRetryBackoff().toMillis();
        if (backoffMillis <= 0) {
            return true;
        }
        try {
            Thread.sleep(backoffMillis);
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private LinkUpdateEvent deserialize(byte[] payload) {
        Object decoded = kafkaAvroDeserializer.deserialize(kafkaProperties.getLinkUpdatesTopic(), payload);
        if (decoded instanceof LinkUpdateEvent linkUpdateEvent) {
            return linkUpdateEvent;
        }
        throw new IllegalArgumentException("Unsupported event payload type: " + decoded);
    }

    private void publishDeserializationFailure(String key, byte[] payload, Exception exception) {
        ProducerRecord<String, byte[]> dlqRecord =
                new ProducerRecord<>(kafkaProperties.getLinkUpdatesDlqTopic(), key, payload);
        addErrorHeaders(dlqRecord.headers(), "deserialization", exception, null);
        kafkaBytesTemplate.send(dlqRecord);
    }

    private void publishValidationFailure(String key, LinkUpdateEvent event, Exception exception) {
        ProducerRecord<String, LinkUpdateEvent> dlqRecord =
                new ProducerRecord<>(kafkaProperties.getLinkUpdatesDlqTopic(), key, event);
        addErrorHeaders(dlqRecord.headers(), "validation", exception, null);
        kafkaTemplate.send(dlqRecord);
    }

    private void publishProcessingFailure(String key, LinkUpdateEvent event, Exception exception, int attempt) {
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
