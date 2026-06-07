package backend.academy.linktracker.scrapper.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DurationUnit;
import org.springframework.validation.annotation.Validated;

/**
 * Typed properties for outbound-call resilience and REST rate limiting.
 */
@ConfigurationProperties(prefix = "app.resilience")
@Validated
@Getter
@Setter
@EqualsAndHashCode
@NoArgsConstructor
public class ResilienceProperties {

    @Valid
    private Retry retry = new Retry();

    @Valid
    private CircuitBreaker circuitBreaker = new CircuitBreaker();

    @Valid
    private RateLimit rateLimit = new RateLimit();

    public Retry retry() {
        return retry;
    }

    public CircuitBreaker circuitBreaker() {
        return circuitBreaker;
    }

    public RateLimit rateLimit() {
        return rateLimit;
    }

    /**
     * Retry settings with constant backoff.
     */
    @Getter
    @Setter
    @EqualsAndHashCode
    @NoArgsConstructor
    public static class Retry {

        @Min(1)
        private int maxAttempts = 3;

        @DurationUnit(ChronoUnit.MILLIS)
        @DurationMin(millis = 1)
        private Duration backoff = Duration.ofMillis(200);

        @NotEmpty
        private Set<@Min(100) @Max(599) Integer> retryableHttpStatuses = Set.of(500, 502, 503, 504);
    }

    /**
     * Count-based circuit breaker settings.
     */
    @Getter
    @Setter
    @EqualsAndHashCode
    @NoArgsConstructor
    public static class CircuitBreaker {

        @Min(1)
        @Max(100)
        private int failureRateThreshold = 50;

        @Min(1)
        private int slidingWindowSize = 10;

        @Min(1)
        private int minimumNumberOfCalls = 5;

        @Min(1)
        private int permittedCallsInHalfOpenState = 2;

        @DurationUnit(ChronoUnit.MILLIS)
        @DurationMin(millis = 1)
        private Duration openStateDuration = Duration.ofSeconds(5);
    }

    /**
     * Resilience4J rate limiter settings.
     */
    @Getter
    @Setter
    @EqualsAndHashCode
    @NoArgsConstructor
    public static class RateLimit {

        @Min(1)
        private int limitForPeriod = 60;

        @DurationUnit(ChronoUnit.MILLIS)
        @DurationMin(millis = 1)
        private Duration limitRefreshPeriod = Duration.ofMinutes(1);

        @DurationUnit(ChronoUnit.MILLIS)
        @DurationMin(millis = 0)
        private Duration timeoutDuration = Duration.ZERO;
    }
}
