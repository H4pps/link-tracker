package backend.academy.linktracker.bot.telegram.logging;

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
}
