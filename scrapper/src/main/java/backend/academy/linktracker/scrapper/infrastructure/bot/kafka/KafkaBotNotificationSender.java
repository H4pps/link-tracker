package backend.academy.linktracker.scrapper.infrastructure.bot.kafka;

import backend.academy.linktracker.scrapper.application.update.BotNotificationSender;
import backend.academy.linktracker.scrapper.application.update.LinkUpdateNotification;
import backend.academy.linktracker.scrapper.application.update.LinkUpdateOutboxEvent;
import backend.academy.linktracker.scrapper.application.update.LinkUpdateOutboxRepository;
import backend.academy.linktracker.scrapper.logging.ScrapperLogger;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Kafka sender strategy that stores update payloads in transactional outbox.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.bot", name = "mode", havingValue = "kafka", matchIfMissing = true)
public class KafkaBotNotificationSender implements BotNotificationSender {

    private final LinkUpdateOutboxRepository outboxRepository;
    private final ScrapperLogger scrapperLogger;

    @Override
    public boolean send(LinkUpdateNotification notification) {
        try {
            outboxRepository.save(LinkUpdateOutboxEvent.pending(
                    notification.id(), notification.url(), notification.description(), notification.tgChatIds()));
            return true;
        } catch (RuntimeException exception) {
            scrapperLogger.logExternalFetchFailed(
                    "bot-kafka-outbox", notification.url(), exception.getClass().getSimpleName());
            return false;
        }
    }
}
