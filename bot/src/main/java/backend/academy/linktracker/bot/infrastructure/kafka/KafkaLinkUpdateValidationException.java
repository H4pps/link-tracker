package backend.academy.linktracker.bot.infrastructure.kafka;

/**
 * Non-retryable Kafka link update validation failure.
 */
class KafkaLinkUpdateValidationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    KafkaLinkUpdateValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
