package com.linktracker.bot.infrastructure.kafka.processing;

import com.linktracker.bot.application.update.BotUpdateUseCase;
import com.linktracker.bot.application.update.ProcessedUpdateRepository;
import com.linktracker.bot.infrastructure.kafka.exception.KafkaLinkUpdateValidationException;
import com.linktracker.messaging.ProcessedLinkUpdateEvent;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Processes decoded link update events with validation and idempotency.
 */
@Component
@RequiredArgsConstructor
public class LinkUpdateEventProcessingService {

    private final BotUpdateUseCase botUpdateUseCase;
    private final ProcessedUpdateRepository processedUpdateRepository;
    private final LinkUpdateEventValidator validator;
    private final LinkUpdateEventMapper mapper;

    public void process(ProcessedLinkUpdateEvent event, UUID messageId) {
        try {
            validator.validate(event);
        } catch (IllegalArgumentException exception) {
            throw new KafkaLinkUpdateValidationException(exception.getMessage(), exception);
        }

        if (messageId != null && processedUpdateRepository.isProcessed(messageId)) {
            return;
        }

        botUpdateUseCase.processLinkUpdate(mapper.toCommand(event));
        if (messageId != null) {
            processedUpdateRepository.markProcessed(messageId);
        }
    }
}
