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

    void handle(byte[] payload, String key, byte[] messageIdHeader) {
        processingService.process(deserialize(payload), parseMessageId(messageIdHeader));
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
        try {
            Object decoded = kafkaAvroDeserializer.deserialize(kafkaProperties.getLinkUpdatesTopic(), payload);
            if (decoded instanceof LinkUpdateEvent linkUpdateEvent) {
                return linkUpdateEvent;
            }
            throw new KafkaLinkUpdateDeserializationException("Unsupported event payload type: " + decoded);
        } catch (KafkaLinkUpdateDeserializationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new KafkaLinkUpdateDeserializationException("Failed to deserialize link update event", exception);
        }
    }
}
