package backend.academy.linktracker.scrapper.application.update;

import java.time.Instant;
import java.util.List;

/**
 * Port for durable storage and status transitions of Kafka outbox events.
 */
public interface LinkUpdateOutboxRepository {

    /**
     * Stores an outbox event.
     *
     * @param event event payload and metadata
     */
    void save(LinkUpdateOutboxEvent event);

    /**
     * Loads pending events due for publishing.
     *
     * @param dueAt include events with next attempt time less than or equal to this instant
     * @param limit maximum number of events to return
     * @return pending events ordered by due time
     */
    List<LinkUpdateOutboxEvent> findPending(Instant dueAt, int limit);

    /**
     * Marks outbox event as sent.
     *
     * @param outboxId outbox row identifier
     */
    void markSent(long outboxId);

    /**
     * Marks outbox event as failed and schedules next retry.
     *
     * @param outboxId outbox row identifier
     * @param lastError latest error details
     * @param nextAttemptAt retry timestamp
     */
    void markFailed(long outboxId, String lastError, Instant nextAttemptAt);
}
