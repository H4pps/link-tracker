package backend.academy.linktracker.bot.telegram.command;

/**
 * Parsed representation of a command token extracted from a Telegram message.
 *
 * @param inputCommand raw first token from the message text
 * @param normalizedCommand normalized command name used for routing
 */
record ParsedCommand(String inputCommand, String normalizedCommand) {}
