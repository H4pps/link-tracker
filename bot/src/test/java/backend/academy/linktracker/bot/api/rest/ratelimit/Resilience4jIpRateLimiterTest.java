package backend.academy.linktracker.bot.api.rest.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeout;

import backend.academy.linktracker.bot.properties.ResilienceProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class Resilience4jIpRateLimiterTest {

    @Test
    void keepsIndependentLimitersPerIp() {
        Resilience4jIpRateLimiter rateLimiter = new Resilience4jIpRateLimiter(rateLimitProperties());

        assertThat(rateLimiter.tryAcquire("203.0.113.1")).isTrue();
        assertThat(rateLimiter.tryAcquire("203.0.113.2")).isTrue();
    }

    @Test
    void exhaustedPermissionFailsImmediatelyWhenTimeoutIsZero() {
        Resilience4jIpRateLimiter rateLimiter = new Resilience4jIpRateLimiter(rateLimitProperties());

        assertThat(rateLimiter.tryAcquire("203.0.113.1")).isTrue();
        assertTimeout(Duration.ofMillis(100), () -> assertThat(rateLimiter.tryAcquire("203.0.113.1")).isFalse());
    }

    private ResilienceProperties rateLimitProperties() {
        ResilienceProperties properties = new ResilienceProperties();
        properties.rateLimit().setLimitForPeriod(1);
        properties.rateLimit().setLimitRefreshPeriod(Duration.ofMinutes(1));
        properties.rateLimit().setTimeoutDuration(Duration.ZERO);
        return properties;
    }
}
