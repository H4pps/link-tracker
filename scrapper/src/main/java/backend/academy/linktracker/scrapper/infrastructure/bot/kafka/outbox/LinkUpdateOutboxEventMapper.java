package backend.academy.linktracker.scrapper.infrastructure.bot.kafka.outbox;

import backend.academy.linktracker.messaging.LinkUpdateEvent;
import backend.academy.linktracker.scrapper.application.update.LinkUpdateOutboxEvent;
import org.springframework.stereotype.Component;

/**
 * Maps Kafka outbox rows to Avro link update events.
 */
@Component
class LinkUpdateOutboxEventMapper {

    LinkUpdateEvent toEvent(LinkUpdateOutboxEvent outboxEvent) {
        return new LinkUpdateEvent(
                outboxEvent.id(), outboxEvent.url(), outboxEvent.description(), outboxEvent.tgChatIds());
    }
}
