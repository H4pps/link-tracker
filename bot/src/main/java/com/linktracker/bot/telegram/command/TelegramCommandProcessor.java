package com.linktracker.bot.telegram.command;

import com.linktracker.bot.application.track.service.TrackDialogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Routes incoming Telegram messages to registered command handlers.
 */
@Component
@RequiredArgsConstructor
public class TelegramCommandProcessor {

    public static final String UNKNOWN_COMMAND_NAME = "unknown";
    public static final String UNKNOWN_REPLY = "Unknown command. Use /help to see available commands.";
    private static final String CANCEL_COMMAND = "cancel";

    private final TelegramCommandRegistry commandRegistry;
    private final CommandTextParser commandTextParser;
    private final TrackDialogService trackDialogService;

    /**
     * Processes raw incoming message text and resolves reply plus logging metadata.
     */
    public CommandProcessingResult process(long chatId, String messageText) {
        ParsedCommand parsedCommand = commandTextParser.parse(messageText);
        if (trackDialogService.hasActiveDialog(chatId)) {
            if (CANCEL_COMMAND.equals(parsedCommand.normalizedCommand())) {
                return new CommandProcessingResult(
                        trackDialogService.cancel(chatId), CANCEL_COMMAND, parsedCommand.inputCommand());
            }

            if (!isSlashCommandToken(parsedCommand.inputCommand())) {
                return new CommandProcessingResult(
                        trackDialogService.handleDialogInput(chatId, messageText),
                        "track-dialog",
                        parsedCommand.inputCommand());
            }

            trackDialogService.cancel(chatId);
        }

        CommandContext context = new CommandContext(chatId, messageText, parsedCommand);
        return commandRegistry
                .handlerByName(parsedCommand.normalizedCommand())
                .map(handler -> new CommandProcessingResult(
                        handler.handle(context), parsedCommand.normalizedCommand(), parsedCommand.inputCommand()))
                .orElseGet(() ->
                        new CommandProcessingResult(UNKNOWN_REPLY, UNKNOWN_COMMAND_NAME, parsedCommand.inputCommand()));
    }

    /**
     * Determines whether input token starts with slash and should be treated as a command token.
     *
     * @param inputCommand first token extracted from incoming message
     * @return true when token starts with '/'
     */
    private boolean isSlashCommandToken(String inputCommand) {
        return !inputCommand.isBlank() && inputCommand.charAt(0) == '/';
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
