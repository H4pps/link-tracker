package backend.academy.linktracker.bot.telegram.command;

/**
 * Immutable context passed to command handlers.
 *
 * @param chatId telegram chat identifier
 * @param messageText raw incoming message text
 * @param parsedCommand parsed command token details
 */
public record CommandContext(long chatId, String messageText, ParsedCommand parsedCommand) {}
