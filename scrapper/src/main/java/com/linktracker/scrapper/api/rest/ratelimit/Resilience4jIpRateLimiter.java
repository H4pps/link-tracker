package com.linktracker.scrapper.api.rest.ratelimit;

import com.linktracker.scrapper.properties.ResilienceProperties;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Resilience4J-backed rate limiter keyed by client IP.
 */
@Component
public class Resilience4jIpRateLimiter {

    private final RateLimiterConfig config;
    private final ConcurrentHashMap<String, RateLimiter> rateLimiters = new ConcurrentHashMap<>();

    public Resilience4jIpRateLimiter(ResilienceProperties resilienceProperties) {
        ResilienceProperties.RateLimit properties = resilienceProperties.rateLimit();
        this.config = RateLimiterConfig.custom()
                .limitForPeriod(properties.getLimitForPeriod())
                .limitRefreshPeriod(properties.getLimitRefreshPeriod())
                .timeoutDuration(properties.getTimeoutDuration())
                .build();
    }

    public boolean tryAcquire(String ipAddress) {
        return rateLimiters.computeIfAbsent(ipAddress, this::createRateLimiter).acquirePermission();
    }

    private RateLimiter createRateLimiter(String ipAddress) {
        return RateLimiter.of("rest-ip-" + ipAddress, config);
    }
}
