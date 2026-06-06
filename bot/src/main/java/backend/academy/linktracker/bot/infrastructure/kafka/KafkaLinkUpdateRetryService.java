package backend.academy.linktracker.bot.infrastructure.kafka;

import backend.academy.linktracker.bot.properties.KafkaProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Retries bot update delivery according to Kafka consumer retry settings.
 */
@Component
@RequiredArgsConstructor
class KafkaLinkUpdateRetryService {

    private final KafkaProperties kafkaProperties;
    private final RetryDelayService retryDelayService;

    boolean execute(Runnable action, RetryFailurePublisher failurePublisher) {
        int maxAttempts = kafkaProperties.getMaxAttempts();
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                action.run();
                return true;
            } catch (IllegalArgumentException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                if (attempt == maxAttempts) {
                    failurePublisher.publish(exception, attempt);
                    return false;
                }
                if (!retryDelayService.delay(kafkaProperties.getRetryBackoff())) {
                    failurePublisher.publish(exception, attempt);
                    return false;
                }
            }
        }
        return false;
    }

    @FunctionalInterface
    interface RetryFailurePublisher {
        void publish(Exception exception, int attempt);
    }
}
