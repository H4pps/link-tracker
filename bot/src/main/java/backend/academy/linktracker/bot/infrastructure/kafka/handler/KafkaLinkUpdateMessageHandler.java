package backend.academy.linktracker.bot.infrastructure.kafka.handler;

import backend.academy.linktracker.bot.infrastructure.kafka.exception.KafkaLinkUpdateDeserializationException;
import backend.academy.linktracker.bot.infrastructure.kafka.processing.LinkUpdateEventProcessingService;
import backend.academy.linktracker.bot.properties.KafkaProperties;
import backend.academy.linktracker.messaging.ProcessedLinkUpdateEvent;
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
public class KafkaLinkUpdateMessageHandler {

    private final KafkaProperties kafkaProperties;
    private final KafkaAvroDeserializer kafkaAvroDeserializer;
    private final LinkUpdateEventProcessingService processingService;

    public void handle(byte[] payload, String key, byte[] messageIdHeader) {
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

    private ProcessedLinkUpdateEvent deserialize(byte[] payload) {
        try {
            Object decoded = kafkaAvroDeserializer.deserialize(kafkaProperties.getProcessedUpdatesTopic(), payload);
            if (decoded instanceof ProcessedLinkUpdateEvent processedLinkUpdateEvent) {
                return processedLinkUpdateEvent;
            }
            throw new KafkaLinkUpdateDeserializationException("Unsupported event payload type: " + decoded);
        } catch (KafkaLinkUpdateDeserializationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new KafkaLinkUpdateDeserializationException("Failed to deserialize link update event", exception);
        }
    }
}
