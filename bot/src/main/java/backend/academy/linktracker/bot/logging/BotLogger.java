package backend.academy.linktracker.bot.logging;

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
     * Logs incoming bot API update payload metadata.
     *
     * @param endpoint API endpoint path
     * @param updateId update identifier
     * @param url update URL
     * @param chatCount number of chats in payload
     */
    public void logApiUpdateReceived(String endpoint, Long updateId, String url, int chatCount) {
        LOGGER.atInfo()
                .addKeyValue("endpoint", endpoint)
                .addKeyValue("updateId", updateId)
                .addKeyValue("url", url)
                .addKeyValue("chatCount", chatCount)
                .log("Bot API update request accepted");
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
}
