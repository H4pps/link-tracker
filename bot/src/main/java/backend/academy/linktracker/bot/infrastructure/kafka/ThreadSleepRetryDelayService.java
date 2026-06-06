package backend.academy.linktracker.bot.infrastructure.kafka;

import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * Production retry delay backed by {@link Thread#sleep(long)}.
 */
@Component
class ThreadSleepRetryDelayService implements RetryDelayService {

    @Override
    public boolean delay(Duration backoff) {
        long backoffMillis = backoff.toMillis();
        if (backoffMillis <= 0) {
            return true;
        }
        try {
            Thread.sleep(backoffMillis);
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
