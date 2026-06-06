package backend.academy.linktracker.bot.infrastructure.kafka.processing;

import backend.academy.linktracker.bot.application.update.LinkUpdateCommand;
import backend.academy.linktracker.messaging.LinkUpdateEvent;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Maps Kafka Avro link update events to bot application commands.
 */
@Component
public class LinkUpdateEventMapper {

    LinkUpdateCommand toCommand(LinkUpdateEvent event) {
        return new LinkUpdateCommand(
                event.getId(),
                String.valueOf(event.getUrl()),
                String.valueOf(event.getDescription()),
                List.copyOf(event.getTgChatIds()));
    }
}
