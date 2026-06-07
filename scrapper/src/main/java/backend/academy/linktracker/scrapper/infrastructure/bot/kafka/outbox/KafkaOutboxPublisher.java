package backend.academy.linktracker.scrapper.infrastructure.bot.kafka.outbox;

import backend.academy.linktracker.messaging.LinkUpdateEvent;
import backend.academy.linktracker.scrapper.application.update.LinkUpdateOutboxEvent;
import backend.academy.linktracker.scrapper.application.update.LinkUpdateOutboxRepository;
import backend.academy.linktracker.scrapper.logging.ScrapperLogger;
import backend.academy.linktracker.scrapper.properties.KafkaProperties;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutionException;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled best-effort publisher for Kafka outbox records.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnExpression("'${app.bot.mode:kafka}' == 'kafka' || '${app.bot.mode:kafka}' == 'grpc'")
public class KafkaOutboxPublisher {

    private final LinkUpdateOutboxRepository outboxRepository;
    private final KafkaTemplate<String, LinkUpdateEvent> kafkaTemplate;
    private final KafkaProperties kafkaProperties;
    private final ScrapperLogger scrapperLogger;
    private final LinkUpdateOutboxEventMapper mapper;

    @Scheduled(fixedDelayString = "${app.kafka.outbox-publish-interval:5s}")
    public void publishDueEvents() {
        List<LinkUpdateOutboxEvent> pendingEvents =
                outboxRepository.findPending(Instant.now(), kafkaProperties.getOutboxBatchSize());
        for (LinkUpdateOutboxEvent pendingEvent : pendingEvents) {
            publishEvent(pendingEvent);
        }
    }

    private void publishEvent(LinkUpdateOutboxEvent outboxEvent) {
        if (outboxEvent.outboxId() == null) {
            return;
        }
        try {
            ProducerRecord<String, LinkUpdateEvent> record = new ProducerRecord<>(
                    kafkaProperties.getLinkUpdatesTopic(), outboxEvent.url(), mapper.toEvent(outboxEvent));
            record.headers()
                    .add(new RecordHeader(
                            "message-id", outboxEvent.messageId().toString().getBytes(StandardCharsets.UTF_8)));
            kafkaTemplate.send(record).get();
            outboxRepository.markSent(outboxEvent.outboxId());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            markFailed(outboxEvent, exception);
        } catch (ExecutionException exception) {
            markFailed(outboxEvent, exception.getCause() == null ? exception : exception.getCause());
        } catch (RuntimeException exception) {
            markFailed(outboxEvent, exception);
        }
    }

    private void markFailed(LinkUpdateOutboxEvent outboxEvent, Throwable throwable) {
        String errorMessage = formatError(throwable);
        outboxRepository.markFailed(
                outboxEvent.outboxId(), errorMessage, Instant.now().plus(kafkaProperties.getRetryBackoff()));
        scrapperLogger.logExternalFetchFailed("bot-kafka-publisher", outboxEvent.url(), errorMessage);
    }

    private String formatError(Throwable throwable) {
        if (throwable == null) {
            return "unknown";
        }
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            return throwable.getClass().getSimpleName();
        }
        return throwable.getClass().getSimpleName() + ": " + message;
    }
}
