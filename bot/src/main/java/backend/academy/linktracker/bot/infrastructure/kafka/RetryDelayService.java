package backend.academy.linktracker.bot.infrastructure.kafka;

import java.time.Duration;

/**
 * Delay abstraction used between Kafka consumer retry attempts.
 */
@FunctionalInterface
interface RetryDelayService {

    boolean delay(Duration backoff);
}
