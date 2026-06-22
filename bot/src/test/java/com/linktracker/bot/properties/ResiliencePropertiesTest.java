package com.linktracker.bot.properties;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class ResiliencePropertiesTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(TestConfiguration.class);

    @Test
    void bindsDefaultValues() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(ResilienceProperties.class);

            ResilienceProperties properties = context.getBean(ResilienceProperties.class);

            assertThat(properties.retry().getMaxAttempts()).isEqualTo(3);
            assertThat(properties.retry().getBackoff()).isEqualTo(Duration.ofMillis(200));
            assertThat(properties.retry().getRetryableHttpStatuses()).containsExactlyInAnyOrder(500, 502, 503, 504);
            assertThat(properties.circuitBreaker().getFailureRateThreshold()).isEqualTo(50);
            assertThat(properties.circuitBreaker().getSlidingWindowSize()).isEqualTo(10);
            assertThat(properties.circuitBreaker().getMinimumNumberOfCalls()).isEqualTo(5);
            assertThat(properties.circuitBreaker().getPermittedCallsInHalfOpenState())
                    .isEqualTo(2);
            assertThat(properties.circuitBreaker().getOpenStateDuration()).isEqualTo(Duration.ofSeconds(5));
            assertThat(properties.rateLimit().getLimitForPeriod()).isEqualTo(60);
            assertThat(properties.rateLimit().getLimitRefreshPeriod()).isEqualTo(Duration.ofMinutes(1));
            assertThat(properties.rateLimit().getTimeoutDuration()).isEqualTo(Duration.ZERO);
        });
    }

    @Test
    void bindsOverrideValues() {
        contextRunner
                .withPropertyValues(
                        "app.resilience.retry.max-attempts=4",
                        "app.resilience.retry.backoff=150ms",
                        "app.resilience.retry.retryable-http-statuses=429,500",
                        "app.resilience.circuit-breaker.failure-rate-threshold=75",
                        "app.resilience.circuit-breaker.sliding-window-size=12",
                        "app.resilience.circuit-breaker.minimum-number-of-calls=6",
                        "app.resilience.circuit-breaker.permitted-calls-in-half-open-state=3",
                        "app.resilience.circuit-breaker.open-state-duration=7s",
                        "app.resilience.rate-limit.limit-for-period=11",
                        "app.resilience.rate-limit.limit-refresh-period=30s",
                        "app.resilience.rate-limit.timeout-duration=25ms")
                .run(context -> {
                    ResilienceProperties properties = context.getBean(ResilienceProperties.class);

                    assertThat(properties.retry().getMaxAttempts()).isEqualTo(4);
                    assertThat(properties.retry().getBackoff()).isEqualTo(Duration.ofMillis(150));
                    assertThat(properties.retry().getRetryableHttpStatuses()).containsExactlyInAnyOrder(429, 500);
                    assertThat(properties.circuitBreaker().getFailureRateThreshold())
                            .isEqualTo(75);
                    assertThat(properties.circuitBreaker().getSlidingWindowSize())
                            .isEqualTo(12);
                    assertThat(properties.circuitBreaker().getMinimumNumberOfCalls())
                            .isEqualTo(6);
                    assertThat(properties.circuitBreaker().getPermittedCallsInHalfOpenState())
                            .isEqualTo(3);
                    assertThat(properties.circuitBreaker().getOpenStateDuration())
                            .isEqualTo(Duration.ofSeconds(7));
                    assertThat(properties.rateLimit().getLimitForPeriod()).isEqualTo(11);
                    assertThat(properties.rateLimit().getLimitRefreshPeriod()).isEqualTo(Duration.ofSeconds(30));
                    assertThat(properties.rateLimit().getTimeoutDuration()).isEqualTo(Duration.ofMillis(25));
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ResilienceProperties.class)
    static class TestConfiguration {}
}
