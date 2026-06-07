package backend.academy.linktracker.ai.infrastructure.kafka.consumer;

import backend.academy.linktracker.ai.infrastructure.kafka.handler.RawLinkUpdateMessageHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for raw link update events from {@code link.raw-updates} (FR-1).
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.kafka", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AiAgentKafkaConsumer {

    private static final String MESSAGE_ID_HEADER = "message-id";

    private final RawLinkUpdateMessageHandler messageHandler;

    @KafkaListener(
            topics = "${app.kafka.raw-updates-topic:link.raw-updates}",
            groupId = "${app.kafka.consumer-group:ai-agent}",
            containerFactory = "kafkaRawListenerContainerFactory")
    public void listen(
            @Payload byte[] payload,
            @Header(name = KafkaHeaders.RECEIVED_KEY, required = false) String key,
            @Header(name = MESSAGE_ID_HEADER, required = false) byte[] messageIdHeader) {
        messageHandler.handle(payload, key, messageIdHeader);
    }
}
