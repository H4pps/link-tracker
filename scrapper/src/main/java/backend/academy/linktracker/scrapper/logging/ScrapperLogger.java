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

    /**
     * Logs list-links cache read failure.
     *
     * @param chatId telegram chat identifier
     * @param exception source exception
     */
    public void logCacheReadFailed(long chatId, Exception exception) {
        LOGGER.atWarn().setCause(exception).addKeyValue("chatId", chatId).log("List-links cache read failed");
    }

    /**
     * Logs list-links cache write failure.
     *
     * @param chatId telegram chat identifier
     * @param exception source exception
     */
    public void logCacheWriteFailed(long chatId, Exception exception) {
        LOGGER.atWarn().setCause(exception).addKeyValue("chatId", chatId).log("List-links cache write failed");
    }

    /**
     * Logs list-links cache eviction failure.
     *
     * @param chatId telegram chat identifier
     * @param exception source exception
     */
    public void logCacheEvictFailed(long chatId, Exception exception) {
        LOGGER.atWarn().setCause(exception).addKeyValue("chatId", chatId).log("List-links cache eviction failed");
    }

    /**
     * Logs external source fetch failure.
     *
     * @param source source identifier
     * @param url tracked URL
     * @param errorCode error code
     */
    public void logExternalFetchFailed(String source, String url, String errorCode) {
        LOGGER.atWarn()
                .addKeyValue("source", source)
                .addKeyValue("url", url)
                .addKeyValue("errorCode", errorCode)
                .log("External source fetch failed");
    }

    /**
     * Logs scheduler processing result per tracked URL.
     *
     * @param url tracked URL
     * @param changed true when update is detected
     */
    public void logSchedulerProcessed(String url, boolean changed) {
        LOGGER.atInfo().addKeyValue("url", url).addKeyValue("changed", changed).log("Scheduler processed tracked URL");
    }

    /**
     * Logs scheduler notification attempt to bot.
     *
     * @param url tracked URL
     * @param chatCount number of chats in update
     */
    public void logSchedulerNotifyAttempt(String url, int chatCount) {
        LOGGER.atInfo()
                .addKeyValue("url", url)
                .addKeyValue("chatCount", chatCount)
                .log("Sending update notification to bot");
    }

    /**
     * Logs scheduler notification result.
     *
     * @param url tracked URL
     * @param sent whether notification succeeded
     */
    public void logSchedulerNotifyResult(String url, boolean sent) {
        LOGGER.atInfo().addKeyValue("url", url).addKeyValue("sent", sent).log("Bot notification processed");
    }

    /**
     * Logs embedded scrapper gRPC server startup event.
     *
     * @param port gRPC server port
     */
    public void logGrpcServerStarted(int port) {
        LOGGER.atInfo()
                .addKeyValue("transport", "grpc")
                .addKeyValue("port", port)
                .log("Scrapper gRPC server started");
    }
}
