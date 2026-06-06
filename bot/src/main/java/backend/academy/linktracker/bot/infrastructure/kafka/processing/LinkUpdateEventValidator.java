package backend.academy.linktracker.bot.infrastructure.kafka.processing;

import backend.academy.linktracker.messaging.LinkUpdateEvent;
import org.springframework.stereotype.Component;

/**
 * Validates Avro link update events before bot delivery.
 */
@Component
public class LinkUpdateEventValidator {

    void validate(LinkUpdateEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("event must not be null");
        }
        if (event.getId() <= 0) {
            throw new IllegalArgumentException("id must be positive");
        }
        if (String.valueOf(event.getUrl()).isBlank()) {
            throw new IllegalArgumentException("url must not be blank");
        }
        if (event.getTgChatIds() == null || event.getTgChatIds().isEmpty()) {
            throw new IllegalArgumentException("tgChatIds must not be empty");
        }
        if (event.getTgChatIds().stream().anyMatch(chatId -> chatId == null || chatId <= 0)) {
            throw new IllegalArgumentException("tgChatIds must contain only positive values");
        }
    }
}
