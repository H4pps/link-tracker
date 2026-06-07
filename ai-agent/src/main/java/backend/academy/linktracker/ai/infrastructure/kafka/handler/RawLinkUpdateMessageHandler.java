package backend.academy.linktracker.ai.infrastructure.kafka.handler;

import backend.academy.linktracker.ai.application.LinkUpdate;
import backend.academy.linktracker.ai.application.ProcessUpdateUseCase;
import backend.academy.linktracker.ai.infrastructure.kafka.exception.AiAgentDeserializationException;
import backend.academy.linktracker.ai.properties.KafkaProperties;
import backend.academy.linktracker.messaging.RawLinkUpdateEvent;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Decodes raw Kafka listener inputs into {@link LinkUpdate} and forwards them to the processing use case.
 */
@Component
@RequiredArgsConstructor
public class RawLinkUpdateMessageHandler {

    private final KafkaProperties kafkaProperties;
    private final KafkaAvroDeserializer kafkaAvroDeserializer;
    private final ProcessUpdateUseCase processUpdateUseCase;

    /**
     * Handles a single consumed record.
     *
     * @param payload Avro-encoded payload bytes
     * @param key record key (tracked URL)
     * @param messageIdHeader optional message-id header bytes
     */
    public void handle(byte[] payload, String key, byte[] messageIdHeader) {
        RawLinkUpdateEvent event = deserialize(payload);
        LinkUpdate update = new LinkUpdate(
                event.getId(),
                String.valueOf(event.getUrl()),
                String.valueOf(event.getDescription()),
                String.valueOf(event.getAuthor()),
                toChatIds(event.getTgChatIds()));
        processUpdateUseCase.process(update, parseMessageId(messageIdHeader));
    }

    private List<Long> toChatIds(List<Long> chatIds) {
        return chatIds == null ? List.of() : List.copyOf(chatIds);
    }

    private String parseMessageId(byte[] messageIdHeader) {
        if (messageIdHeader == null || messageIdHeader.length == 0) {
            return null;
        }
        return new String(messageIdHeader, StandardCharsets.UTF_8);
    }

    private RawLinkUpdateEvent deserialize(byte[] payload) {
        try {
            Object decoded = kafkaAvroDeserializer.deserialize(kafkaProperties.getRawUpdatesTopic(), payload);
            if (decoded instanceof RawLinkUpdateEvent rawLinkUpdateEvent) {
                return rawLinkUpdateEvent;
            }
            throw new AiAgentDeserializationException("Unsupported event payload type: " + decoded);
        } catch (AiAgentDeserializationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new AiAgentDeserializationException("Failed to deserialize raw link update event", exception);
        }
    }
}
