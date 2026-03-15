package backend.academy.linktracker.bot.telegram.command;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Routes incoming Telegram messages to registered command handlers.
 */
@Component
@RequiredArgsConstructor
public class TelegramCommandProcessor {

    public static final String UNKNOWN_COMMAND_NAME = "unknown";
    public static final String UNKNOWN_REPLY =
            "Неизвестная команда. Воспользуйтесь /help, чтобы посмотреть список доступных команд.";

    private final TelegramCommandRegistry commandRegistry;
    private final CommandTextParser commandTextParser;

    /**
     * Processes raw incoming message text and resolves reply plus logging metadata.
     */
    public CommandProcessingResult process(String messageText) {
        ParsedCommand parsedCommand = commandTextParser.parse(messageText);
        return commandRegistry
                .handlerByName(parsedCommand.normalizedCommand())
                .map(handler -> new CommandProcessingResult(
                        handler.handle(messageText), parsedCommand.normalizedCommand(), parsedCommand.inputCommand()))
                .orElseGet(() ->
                        new CommandProcessingResult(UNKNOWN_REPLY, UNKNOWN_COMMAND_NAME, parsedCommand.inputCommand()));
    }

    /**
     * Result of command processing used for outgoing reply and structured logging.
     *
     * @param reply text to send back to the chat
     * @param commandForLog normalized command value for logs
     * @param inputCommand raw first token from the incoming message
     */
    public record CommandProcessingResult(String reply, String commandForLog, String inputCommand) {}
}
