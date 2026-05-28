package backend.academy.linktracker.scrapper.api.rest.interceptors;

import backend.academy.linktracker.scrapper.logging.ScrapperLogger;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Interceptor for scrapper REST request lifecycle logging.
 */
@Component
@RequiredArgsConstructor
public class ScrapperApiLoggingInterceptor implements HandlerInterceptor {

    private static final String CHAT_HEADER = "Tg-Chat-Id";

    private final ScrapperLogger scrapperLogger;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        scrapperLogger.logRequestReceived(resolveEndpoint(request), extractChatId(request), null);
        return true;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request, HttpServletResponse response, Object handler, Exception exception) {
        int status = response.getStatus();
        if (exception == null && status >= 200 && status < 300) {
            scrapperLogger.logRequestSucceeded(resolveEndpoint(request), status);
        }
    }

    private String resolveEndpoint(HttpServletRequest request) {
        Object bestMatchingPattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (bestMatchingPattern instanceof String endpoint) {
            return endpoint;
        }
        return request.getRequestURI();
    }

    private Long extractChatId(HttpServletRequest request) {
        String headerValue = request.getHeader(CHAT_HEADER);
        if (headerValue != null) {
            try {
                return Long.parseLong(headerValue);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }

        String[] segments = request.getRequestURI().split("/");
        if (segments.length > 0) {
            String last = segments[segments.length - 1];
            if (!last.isBlank()) {
                try {
                    return Long.parseLong(last);
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return null;
    }
}
