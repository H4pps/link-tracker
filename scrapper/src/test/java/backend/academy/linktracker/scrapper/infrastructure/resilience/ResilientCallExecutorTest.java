package backend.academy.linktracker.scrapper.infrastructure.resilience;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import backend.academy.linktracker.scrapper.properties.ResilienceProperties;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ResilientCallExecutorTest {

    @Test
    void circuitBreakerTransitionsFromClosedToOpen() {
        ResilientCallExecutor executor = new ResilientCallExecutor(resilienceProperties());
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> executeFailingCall(executor, calls)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> executeFailingCall(executor, calls)).isInstanceOf(IllegalStateException.class);

        assertThat(executor.circuitState("external")).isEqualTo(CircuitBreaker.State.OPEN);
        assertThatThrownBy(() -> executeSuccessfulCall(executor, calls)).isInstanceOf(CallNotPermittedException.class);
        assertThat(calls).hasValue(2);
    }

    @Test
    void circuitBreakerTransitionsFromOpenToHalfOpenToClosed() throws Exception {
        ResilientCallExecutor executor = new ResilientCallExecutor(resilienceProperties());
        AtomicInteger calls = new AtomicInteger();
        openCircuit(executor, calls);

        Thread.sleep(30);

        assertThat(executeSuccessfulCall(executor, calls)).isEqualTo("ok");
        assertThat(executor.circuitState("external")).isEqualTo(CircuitBreaker.State.HALF_OPEN);
        assertThat(executeSuccessfulCall(executor, calls)).isEqualTo("ok");
        assertThat(executor.circuitState("external")).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void circuitBreakerTransitionsFromHalfOpenToOpenOnFailedProbe() throws Exception {
        ResilientCallExecutor executor = new ResilientCallExecutor(resilienceProperties(1));
        AtomicInteger calls = new AtomicInteger();
        openCircuit(executor, calls);

        Thread.sleep(30);

        assertThatThrownBy(() -> executeFailingCall(executor, calls)).isInstanceOf(IllegalStateException.class);
        assertThat(executor.circuitState("external")).isEqualTo(CircuitBreaker.State.OPEN);
    }

    private void openCircuit(ResilientCallExecutor executor, AtomicInteger calls) {
        assertThatThrownBy(() -> executeFailingCall(executor, calls)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> executeFailingCall(executor, calls)).isInstanceOf(IllegalStateException.class);
        assertThat(executor.circuitState("external")).isEqualTo(CircuitBreaker.State.OPEN);
    }

    private String executeFailingCall(ResilientCallExecutor executor, AtomicInteger calls) {
        return executor.execute(
                "external",
                () -> {
                    calls.incrementAndGet();
                    throw new IllegalStateException("failed");
                },
                throwable -> false,
                throwable -> true);
    }

    private String executeSuccessfulCall(ResilientCallExecutor executor, AtomicInteger calls) {
        return executor.execute(
                "external",
                () -> {
                    calls.incrementAndGet();
                    return "ok";
                },
                throwable -> false,
                throwable -> true);
    }

    private ResilienceProperties resilienceProperties() {
        return resilienceProperties(2);
    }

    private ResilienceProperties resilienceProperties(int permittedCallsInHalfOpenState) {
        ResilienceProperties properties = new ResilienceProperties();
        properties.retry().setMaxAttempts(1);
        properties.retry().setBackoff(Duration.ofMillis(1));
        properties.circuitBreaker().setFailureRateThreshold(50);
        properties.circuitBreaker().setMinimumNumberOfCalls(2);
        properties.circuitBreaker().setSlidingWindowSize(2);
        properties.circuitBreaker().setOpenStateDuration(Duration.ofMillis(20));
        properties.circuitBreaker().setPermittedCallsInHalfOpenState(permittedCallsInHalfOpenState);
        return properties;
    }
}
