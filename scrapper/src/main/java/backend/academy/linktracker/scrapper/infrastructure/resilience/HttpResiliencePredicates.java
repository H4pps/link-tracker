package backend.academy.linktracker.scrapper.infrastructure.resilience;

import backend.academy.linktracker.scrapper.properties.ResilienceProperties;
import java.util.Set;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Failure classification for external HTTP calls.
 */
public final class HttpResiliencePredicates {

    private HttpResiliencePredicates() {}

    public static boolean isRetryableFailure(Throwable throwable, ResilienceProperties properties) {
        return isRetryableHttpStatus(throwable, properties.retry().getRetryableHttpStatuses())
                || throwable instanceof ResourceAccessException;
    }

    public static boolean isCircuitBreakerFailure(Throwable throwable, ResilienceProperties properties) {
        return isRetryableFailure(throwable, properties);
    }

    private static boolean isRetryableHttpStatus(Throwable throwable, Set<Integer> retryableHttpStatuses) {
        if (throwable instanceof RestClientResponseException responseException) {
            return retryableHttpStatuses.contains(
                    responseException.getStatusCode().value());
        }
        return false;
    }
}
