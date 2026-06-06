package backend.academy.linktracker.bot.infrastructure.kafka.consumer;

import backend.academy.linktracker.bot.infrastructure.kafka.handler.KafkaLinkUpdateMessageHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
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

    private static final String MESSAGE_ID_HEADER = "message-id";

    private final KafkaLinkUpdateMessageHandler messageHandler;

    @KafkaListener(
            topics = "${app.kafka.link-updates-topic:link-updates}",
            groupId = "${app.kafka.consumer-group:link-tracker-bot}",
            containerFactory = "kafkaRawListenerContainerFactory")
    public void listen(
            @Payload byte[] payload,
            @Header(name = KafkaHeaders.RECEIVED_KEY, required = false) String key,
            @Header(name = MESSAGE_ID_HEADER, required = false) byte[] messageIdHeader) {
        messageHandler.handle(payload, key, messageIdHeader);
    }
}
