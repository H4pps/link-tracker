package backend.academy.linktracker.bot.telegram.command;

/**
 * Contract for a single Telegram command handler.
 */
public interface TelegramCommandHandler {

    /**
     * Handles a Telegram command and returns text that should be sent back to the user.
     */
    String handle(String messageText);
}
