package com.linktracker.bot.infrastructure.kafka.exception;

/**
 * Non-retryable Kafka link update deserialization failure.
 */
public class KafkaLinkUpdateDeserializationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public KafkaLinkUpdateDeserializationException(String message) {
        super(message);
    }

    public KafkaLinkUpdateDeserializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
