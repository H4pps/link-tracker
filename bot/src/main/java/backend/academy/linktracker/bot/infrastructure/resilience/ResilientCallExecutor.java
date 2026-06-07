package backend.academy.linktracker.bot.infrastructure.resilience;

import backend.academy.linktracker.bot.properties.ResilienceProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * Executes outbound calls with constant-backoff retry and a named circuit breaker.
 */
@Component
public class ResilientCallExecutor {

    private final ResilienceProperties resilienceProperties;
    private final Map<String, CircuitBreaker> circuitBreakers = new ConcurrentHashMap<>();

    public ResilientCallExecutor(ResilienceProperties resilienceProperties) {
        this.resilienceProperties = resilienceProperties;
    }

    public <T> T execute(
            String circuitName,
            Supplier<T> call,
            Predicate<Throwable> retryableFailure,
            Predicate<Throwable> circuitBreakerFailure) {
        Retry retry = Retry.of(circuitName + "-retry", retryConfig(retryableFailure));
        Supplier<T> retryingCall = Retry.decorateSupplier(retry, call);
        return CircuitBreaker.decorateSupplier(circuitBreaker(circuitName, circuitBreakerFailure), retryingCall)
                .get();
    }

    private RetryConfig retryConfig(Predicate<Throwable> retryableFailure) {
        return RetryConfig.custom()
                .maxAttempts(resilienceProperties.retry().getMaxAttempts())
                .waitDuration(resilienceProperties.retry().getBackoff())
                .retryOnException(retryableFailure)
                .build();
    }

    private CircuitBreaker circuitBreaker(String circuitName, Predicate<Throwable> circuitBreakerFailure) {
        return circuitBreakers.computeIfAbsent(
                circuitName, ignored -> CircuitBreaker.of(circuitName, circuitBreakerConfig(circuitBreakerFailure)));
    }

    private CircuitBreakerConfig circuitBreakerConfig(Predicate<Throwable> circuitBreakerFailure) {
        return CircuitBreakerConfig.custom()
                .failureRateThreshold(resilienceProperties.circuitBreaker().getFailureRateThreshold())
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(resilienceProperties.circuitBreaker().getSlidingWindowSize())
                .minimumNumberOfCalls(resilienceProperties.circuitBreaker().getMinimumNumberOfCalls())
                .permittedNumberOfCallsInHalfOpenState(
                        resilienceProperties.circuitBreaker().getPermittedCallsInHalfOpenState())
                .waitDurationInOpenState(resilienceProperties.circuitBreaker().getOpenStateDuration())
                .recordException(circuitBreakerFailure)
                .build();
    }
}
