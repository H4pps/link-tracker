package backend.academy.linktracker.scrapper.application.update;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Outbox row payload and delivery metadata for Kafka notifications.
 *
 * @param outboxId outbox row identifier
 * @param messageId stable idempotency key propagated to the consumer
 * @param id link update identifier
 * @param url tracked URL
 * @param description update description
 * @param tgChatIds target Telegram chat identifiers
 * @param status delivery status
 * @param attempts delivery attempt counter
 * @param lastError latest delivery error details
 * @param nextAttemptAt next delivery attempt time
 * @param createdAt creation timestamp
 * @param updatedAt update timestamp
 * @param sentAt successful delivery timestamp
 */
public record LinkUpdateOutboxEvent(
        Long outboxId,
        UUID messageId,
        long id,
        String url,
        String description,
        List<Long> tgChatIds,
        Status status,
        int attempts,
        String lastError,
        Instant nextAttemptAt,
        Instant createdAt,
        Instant updatedAt,
        Instant sentAt) {

    /**
     * Outbox delivery states.
     */
    public enum Status {
        PENDING,
        SENT
    }

    /**
     * Canonical constructor normalizing nullable collections and optional text fields.
     */
    public LinkUpdateOutboxEvent {
        tgChatIds = tgChatIds == null ? List.of() : List.copyOf(tgChatIds);
        description = description == null ? "" : description;
    }

    /**
     * Factory for a newly created pending outbox event.
     *
     * @param id link update identifier
     * @param url tracked URL
     * @param description update description
     * @param tgChatIds target Telegram chat identifiers
     * @return pending outbox event ready for persistence
     */
    public static LinkUpdateOutboxEvent pending(long id, String url, String description, List<Long> tgChatIds) {
        return new LinkUpdateOutboxEvent(
                null,
                UUID.randomUUID(),
                id,
                url,
                description,
                tgChatIds,
                Status.PENDING,
                0,
                null,
                Instant.now(),
                null,
                null,
                null);
    }
}
