package com.linktracker.bot.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Centralized structured logger for Telegram bot lifecycle and command events.
 */
@Component
public class BotLogger {

    private static final Logger LOGGER = LoggerFactory.getLogger(BotLogger.class);

    /**
     * Logs that polling is disabled by configuration.
     */
    public void logPollingDisabled() {
        LOGGER.atInfo().addKeyValue("pollingEnabled", false).log("Telegram updates polling is disabled");
    }

    /**
     * Logs that polling has started with configured listener interval.
     */
    public void logPollingStarted(long listenerSleepMillis) {
        LOGGER.atInfo()
                .addKeyValue("pollingEnabled", true)
                .addKeyValue("listenerSleepMillis", listenerSleepMillis)
                .log("Telegram updates polling started");
    }

    /**
     * Logs command processing result with normalized and raw command values.
     */
    public void logCommandProcessed(long chatId, String command, String inputCommand, boolean responseOk) {
        LOGGER.atInfo()
                .addKeyValue("chatId", chatId)
                .addKeyValue("command", command)
                .addKeyValue("inputCommand", inputCommand)
                .addKeyValue("responseOk", responseOk)
                .log("Telegram command processed");
    }

    /**
     * Logs Telegram menu registration request result.
     */
    public void logCommandsMenuRegistered(boolean responseOk, Integer errorCode) {
        LOGGER.atInfo()
                .addKeyValue("responseOk", responseOk)
                .addKeyValue("errorCode", errorCode)
                .log("Telegram commands menu registered");
    }

    /**
     * Logs Telegram menu registration failure with the root exception.
     */
    public void logCommandsMenuRegistrationFailed(RuntimeException exception) {
        LOGGER.atWarn()
                .setCause(exception)
                .addKeyValue("operation", "setMyCommands")
                .log("Telegram commands menu registration failed");
    }

    /**
     * Logs successful use-case acceptance of bot API update payload.
     *
     * @param updateId update identifier
     * @param url update URL
     * @param chatCount number of chats in payload
     */
    public void logApiUpdateAccepted(Long updateId, String url, int chatCount) {
        LOGGER.atInfo()
                .addKeyValue("updateId", updateId)
                .addKeyValue("url", url)
                .addKeyValue("chatCount", chatCount)
                .log("Link update processed by bot use case");
    }

    /**
     * Logs incoming bot API request lifecycle start.
     *
     * @param endpoint API endpoint path
     */
    public void logApiRequestReceived(String endpoint) {
        LOGGER.atInfo().addKeyValue("endpoint", endpoint).log("Bot API request accepted");
    }

    /**
     * Logs bot API request completion status.
     *
     * @param endpoint API endpoint path
     * @param status HTTP status code
     */
    public void logApiRequestSucceeded(String endpoint, int status) {
        LOGGER.atInfo()
                .addKeyValue("endpoint", endpoint)
                .addKeyValue("status", status)
                .log("Bot API request completed");
    }

    /**
     * Logs bot API request failure with structured details.
     *
     * @param endpoint API endpoint path
     * @param status HTTP status code
     * @param errorCode machine-readable error code
     * @param exception root exception
     */
    public void logApiRequestFailed(String endpoint, int status, String errorCode, Exception exception) {
        LOGGER.atWarn()
                .setCause(exception)
                .addKeyValue("endpoint", endpoint)
                .addKeyValue("status", status)
                .addKeyValue("errorCode", errorCode)
                .log("Bot API request failed");
    }

    /**
     * Logs outbound scrapper request start.
     *
     * @param operation scrapper operation name
     * @param chatId telegram chat identifier
     * @param url tracked URL when available
     */
    public void logScrapperRequest(String operation, long chatId, String url) {
        LOGGER.atInfo()
                .addKeyValue("operation", operation)
                .addKeyValue("chatId", chatId)
                .addKeyValue("url", url)
                .log("Scrapper request started");
    }

    /**
     * Logs outbound scrapper request failure.
     *
     * @param operation scrapper operation name
     * @param chatId telegram chat identifier
     * @param url tracked URL when available
     * @param status HTTP status code, zero for transport issues
     * @param errorCode failure code
     */
    public void logScrapperRequestFailed(String operation, long chatId, String url, int status, String errorCode) {
        LOGGER.atWarn()
                .addKeyValue("operation", operation)
                .addKeyValue("chatId", chatId)
                .addKeyValue("url", url)
                .addKeyValue("status", status)
                .addKeyValue("errorCode", errorCode)
                .log("Scrapper request failed");
    }

    /**
     * Logs transition to a new track dialog state.
     *
     * @param chatId telegram chat identifier
     * @param state new dialog state
     */
    public void logTrackDialogState(long chatId, String state) {
        LOGGER.atInfo()
                .addKeyValue("chatId", chatId)
                .addKeyValue("state", state)
                .log("Track dialog state changed");
    }

    /**
     * Logs update notification send attempt.
     *
     * @param chatId target chat identifier
     * @param url tracked URL
     */
    public void logUpdateNotificationSendAttempt(long chatId, String url) {
        LOGGER.atInfo()
                .addKeyValue("chatId", chatId)
                .addKeyValue("url", url)
                .log("Sending update notification to Telegram");
    }

    /**
     * Logs update notification send result.
     *
     * @param chatId target chat identifier
     * @param url tracked URL
     * @param sent whether sending succeeded
     */
    public void logUpdateNotificationSendResult(long chatId, String url, boolean sent) {
        LOGGER.atInfo()
                .addKeyValue("chatId", chatId)
                .addKeyValue("url", url)
                .addKeyValue("sent", sent)
                .log("Telegram update notification processed");
    }

    /**
     * Logs embedded bot gRPC server startup event.
     *
     * @param port gRPC server port
     */
    public void logGrpcServerStarted(int port) {
        LOGGER.atInfo()
                .addKeyValue("transport", "grpc")
                .addKeyValue("port", port)
                .log("Bot gRPC server started");
    }
}
