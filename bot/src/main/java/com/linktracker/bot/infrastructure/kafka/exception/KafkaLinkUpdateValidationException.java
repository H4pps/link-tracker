package com.linktracker.bot.infrastructure.kafka.exception;

/**
 * Non-retryable Kafka link update validation failure.
 */
public class KafkaLinkUpdateValidationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public KafkaLinkUpdateValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
