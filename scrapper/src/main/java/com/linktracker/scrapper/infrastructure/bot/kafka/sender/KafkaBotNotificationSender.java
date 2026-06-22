package com.linktracker.scrapper.infrastructure.bot.kafka.sender;

import com.linktracker.scrapper.application.update.BotNotificationSender;
import com.linktracker.scrapper.application.update.LinkUpdateNotification;
import com.linktracker.scrapper.application.update.LinkUpdateOutboxEvent;
import com.linktracker.scrapper.application.update.LinkUpdateOutboxRepository;
import com.linktracker.scrapper.logging.ScrapperLogger;
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
                    notification.id(),
                    notification.url(),
                    notification.description(),
                    notification.author(),
                    notification.tgChatIds()));
            return true;
        } catch (RuntimeException exception) {
            scrapperLogger.logExternalFetchFailed(
                    "bot-kafka-outbox", notification.url(), exception.getClass().getSimpleName());
            return false;
        }
    }
}
