package backend.academy.linktracker.bot.infrastructure.kafka;

import backend.academy.linktracker.bot.properties.KafkaProperties;
import backend.academy.linktracker.messaging.LinkUpdateEvent;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Handles raw Kafka listener inputs for link update events.
 */
@Component
@RequiredArgsConstructor
class KafkaLinkUpdateMessageHandler {

    private final KafkaProperties kafkaProperties;
    private final KafkaAvroDeserializer kafkaAvroDeserializer;
    private final LinkUpdateEventProcessingService processingService;
    private final KafkaLinkUpdateDlqPublisher dlqPublisher;

    void handle(byte[] payload, String key, byte[] messageIdHeader) {
        LinkUpdateEvent event;
        try {
            event = deserialize(payload);
        } catch (RuntimeException exception) {
            dlqPublisher.publishDeserializationFailure(key, payload, exception);
            return;
        }

        processingService.process(key, event, parseMessageId(messageIdHeader));
    }

    private UUID parseMessageId(byte[] messageIdHeader) {
        if (messageIdHeader == null || messageIdHeader.length == 0) {
            return null;
        }
        try {
            return UUID.fromString(new String(messageIdHeader, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private LinkUpdateEvent deserialize(byte[] payload) {
        Object decoded = kafkaAvroDeserializer.deserialize(kafkaProperties.getLinkUpdatesTopic(), payload);
        if (decoded instanceof LinkUpdateEvent linkUpdateEvent) {
            return linkUpdateEvent;
        }
        throw new IllegalArgumentException("Unsupported event payload type: " + decoded);
    }
}
