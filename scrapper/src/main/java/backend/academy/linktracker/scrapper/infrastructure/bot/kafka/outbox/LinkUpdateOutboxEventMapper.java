package backend.academy.linktracker.scrapper.infrastructure.bot.kafka.outbox;

import backend.academy.linktracker.messaging.RawLinkUpdateEvent;
import backend.academy.linktracker.scrapper.application.update.LinkUpdateOutboxEvent;
import org.springframework.stereotype.Component;

/**
 * Maps Kafka outbox rows to Avro raw link update events.
 */
@Component
class LinkUpdateOutboxEventMapper {

    RawLinkUpdateEvent toEvent(LinkUpdateOutboxEvent outboxEvent) {
        return new RawLinkUpdateEvent(
                outboxEvent.id(),
                outboxEvent.url(),
                outboxEvent.description(),
                outboxEvent.author(),
                outboxEvent.tgChatIds());
    }
}
