package com.linktracker.bot.application.update;

import java.util.UUID;

/**
 * Idempotency store that records which link-update messages have already been delivered to users.
 */
public interface ProcessedUpdateRepository {

    /**
     * Checks whether a message has already been processed.
     *
     * @param messageId stable idempotency key
     * @return {@code true} when the message was already delivered
     */
    boolean isProcessed(UUID messageId);

    /**
     * Records a message as processed. Idempotent: re-marking the same id is a no-op.
     *
     * @param messageId stable idempotency key
     */
    void markProcessed(UUID messageId);
}
