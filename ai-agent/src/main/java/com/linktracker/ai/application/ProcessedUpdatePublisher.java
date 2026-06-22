package com.linktracker.ai.application;

/**
 * Port for publishing processed updates downstream (to {@code link.processed-updates}).
 */
public interface ProcessedUpdatePublisher {

    /**
     * Publishes a processed update.
     *
     * @param update processed update payload
     * @param messageId stable idempotency key propagated from the incoming event (nullable)
     */
    void publish(ProcessedUpdate update, String messageId);
}
