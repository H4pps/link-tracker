package backend.academy.linktracker.scrapper.api.rest.ratelimit;

import backend.academy.linktracker.scrapper.api.rest.errors.RateLimitExceededException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * REST interceptor that rate-limits requests by client IP.
 */
@Component
@RequiredArgsConstructor
public class IpRateLimitInterceptor implements HandlerInterceptor {

    private static final String X_FORWARDED_FOR_HEADER = "X-Forwarded-For";

    private final Resilience4jIpRateLimiter rateLimiter;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String ipAddress = resolveIpAddress(request);
        if (!rateLimiter.tryAcquire(ipAddress)) {
            throw new RateLimitExceededException("Too many requests from " + ipAddress);
        }
        return true;
    }

    private String resolveIpAddress(HttpServletRequest request) {
        String forwardedFor = request.getHeader(X_FORWARDED_FOR_HEADER);
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            String firstForwardedAddress = forwardedFor.split(",", 2)[0].trim();
            if (!firstForwardedAddress.isBlank()) {
                return firstForwardedAddress;
            }
        }
        return request.getRemoteAddr();
    }
}
