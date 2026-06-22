package com.linktracker.ai.infrastructure.kafka.exception;

/**
 * Non-retryable failure raised when a raw update payload cannot be deserialized.
 */
public class AiAgentDeserializationException extends RuntimeException {

    /**
     * @param message error description
     * @param cause underlying cause
     */
    public AiAgentDeserializationException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * @param message error description
     */
    public AiAgentDeserializationException(String message) {
        super(message);
    }
}
