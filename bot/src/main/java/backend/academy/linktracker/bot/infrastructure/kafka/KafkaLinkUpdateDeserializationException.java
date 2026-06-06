package backend.academy.linktracker.bot.infrastructure.kafka;

/**
 * Non-retryable Kafka link update deserialization failure.
 */
class KafkaLinkUpdateDeserializationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    KafkaLinkUpdateDeserializationException(String message) {
        super(message);
    }

    KafkaLinkUpdateDeserializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
