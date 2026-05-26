package backend.academy.linktracker.bot.api.rest.interceptors;

import backend.academy.linktracker.bot.logging.BotLogger;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Interceptor for bot REST request lifecycle logging.
 */
@Component
@RequiredArgsConstructor
public class BotApiLoggingInterceptor implements HandlerInterceptor {

    private final BotLogger botLogger;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        botLogger.logApiRequestReceived(resolveEndpoint(request));
        return true;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request, HttpServletResponse response, Object handler, Exception exception) {
        int status = response.getStatus();
        if (exception == null && status >= 200 && status < 300) {
            botLogger.logApiRequestSucceeded(resolveEndpoint(request), status);
        }
    }

    private String resolveEndpoint(HttpServletRequest request) {
        Object bestMatchingPattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (bestMatchingPattern instanceof String endpoint) {
            return endpoint;
        }
        return request.getRequestURI();
    }
}
