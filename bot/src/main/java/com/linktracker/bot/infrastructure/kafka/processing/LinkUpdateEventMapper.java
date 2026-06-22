package com.linktracker.bot.infrastructure.kafka.processing;

import com.linktracker.bot.application.update.LinkUpdateCommand;
import com.linktracker.messaging.ProcessedLinkUpdateEvent;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Maps Kafka Avro processed link update events to bot application commands.
 */
@Component
public class LinkUpdateEventMapper {

    LinkUpdateCommand toCommand(ProcessedLinkUpdateEvent event) {
        return new LinkUpdateCommand(
                event.getId(),
                String.valueOf(event.getUrl()),
                String.valueOf(event.getDescription()),
                List.copyOf(event.getTgChatIds()));
    }
}
