package backend.academy.linktracker.bot.infrastructure.kafka;

import backend.academy.linktracker.bot.application.update.BotUpdateUseCase;
import backend.academy.linktracker.bot.application.update.ProcessedUpdateRepository;
import backend.academy.linktracker.messaging.LinkUpdateEvent;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Processes decoded link update events with validation, idempotency, retry, and DLQ handling.
 */
@Component
@RequiredArgsConstructor
class LinkUpdateEventProcessingService {

    private final BotUpdateUseCase botUpdateUseCase;
    private final ProcessedUpdateRepository processedUpdateRepository;
    private final LinkUpdateEventValidator validator;
    private final LinkUpdateEventMapper mapper;
    private final KafkaLinkUpdateRetryService retryService;
    private final KafkaLinkUpdateDlqPublisher dlqPublisher;

    void process(String key, LinkUpdateEvent event, UUID messageId) {
        try {
            validator.validate(event);
        } catch (IllegalArgumentException exception) {
            dlqPublisher.publishValidationFailure(key, event, exception);
            return;
        }

        if (messageId != null && processedUpdateRepository.isProcessed(messageId)) {
            return;
        }

        boolean delivered;
        try {
            delivered = retryService.execute(
                    () -> botUpdateUseCase.processLinkUpdate(mapper.toCommand(event)),
                    (exception, attempt) -> dlqPublisher.publishProcessingFailure(key, event, exception, attempt));
        } catch (IllegalArgumentException exception) {
            dlqPublisher.publishValidationFailure(key, event, exception);
            return;
        }

        if (delivered && messageId != null) {
            processedUpdateRepository.markProcessed(messageId);
        }
    }
}
