package backend.academy.linktracker.scrapper.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Structured logger for scrapper HTTP API endpoints and use-case boundaries.
 */
@Component
public class ScrapperLogger {

    private static final Logger LOGGER = LoggerFactory.getLogger(ScrapperLogger.class);

    /**
     * Logs incoming request metadata.
     *
     * @param endpoint API endpoint path
     * @param chatId telegram chat identifier when available
     * @param url link URL when available
     */
    public void logRequestReceived(String endpoint, Long chatId, String url) {
        LOGGER.atInfo()
                .addKeyValue("endpoint", endpoint)
                .addKeyValue("chatId", chatId)
                .addKeyValue("url", url)
                .log("Scrapper API request accepted");
    }

    /**
     * Logs successful request completion.
     *
     * @param endpoint API endpoint path
     * @param status HTTP status code
     */
    public void logRequestSucceeded(String endpoint, int status) {
        LOGGER.atInfo()
                .addKeyValue("endpoint", endpoint)
                .addKeyValue("status", status)
                .log("Scrapper API request completed");
    }

    /**
     * Logs request failure in structured form.
     *
     * @param endpoint API endpoint path
     * @param status HTTP status code
     * @param errorCode machine-readable error code
     * @param exception source exception
     */
    public void logRequestFailed(String endpoint, int status, String errorCode, Exception exception) {
        LOGGER.atWarn()
                .setCause(exception)
                .addKeyValue("endpoint", endpoint)
                .addKeyValue("status", status)
                .addKeyValue("errorCode", errorCode)
                .log("Scrapper API request failed");
    }

    /**
     * Logs accepted use-case operation at application boundary.
     *
     * @param operation use-case operation name
     * @param chatId telegram chat identifier
     * @param url link URL when available
     */
    public void logUseCaseAccepted(String operation, Long chatId, String url) {
        LOGGER.atInfo()
                .addKeyValue("operation", operation)
                .addKeyValue("chatId", chatId)
                .addKeyValue("url", url)
                .log("Scrapper use case accepted request");
    }
}
