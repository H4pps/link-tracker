package com.linktracker.bot.telegram.command;

import com.linktracker.bot.telegram.command.handlers.TelegramCommandHandler;
import com.pengrad.telegrambot.model.BotCommand;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Stores Telegram command handlers and exposes derived menu/help representations.
 */
@Component
public final class TelegramCommandRegistry {

    private static final String HELP_HEADER = "Available commands:";

    private final List<RegisteredCommand> commands;
    private final Map<String, TelegramCommandHandler> handlersByName;

    /**
     * Creates registry from all discovered command handlers.
     *
     * @param handlers command handler beans discovered by Spring
     */
    TelegramCommandRegistry(List<TelegramCommandHandler> handlers) {
        List<RegisteredCommand> sortedCommands = new ArrayList<>(handlers.size());
        for (TelegramCommandHandler handler : handlers) {
            sortedCommands.add(mapToRegisteredCommand(handler));
        }
        sortedCommands.sort(Comparator.comparing(RegisteredCommand::name));

        var handlersByNameMutable = new LinkedHashMap<String, TelegramCommandHandler>(sortedCommands.size());
        for (RegisteredCommand command : sortedCommands) {
            TelegramCommandHandler previousHandler =
                    handlersByNameMutable.putIfAbsent(command.name(), command.handler());
            if (previousHandler != null) {
                throw new IllegalStateException("Duplicate Telegram command: " + command.name());
            }
        }

        this.commands = List.copyOf(sortedCommands);
        this.handlersByName = Map.copyOf(handlersByNameMutable);
    }

    /**
     * Finds handler by normalized command name.
     *
     * @param commandName normalized command name
     * @return handler when command is known, otherwise empty
     */
    Optional<TelegramCommandHandler> handlerByName(String commandName) {
        if (commandName == null || commandName.isBlank()) {
            return Optional.empty();
        }

        return Optional.ofNullable(handlersByName.get(commandName.toLowerCase(Locale.ROOT)));
    }

    /**
     * Builds Telegram menu commands from registered handlers.
     */
    public BotCommand[] menuCommands() {
        return commands.stream()
                .map(command -> new BotCommand(command.name(), command.description()))
                .toArray(BotCommand[]::new);
    }

    /**
     * Builds `/help` response text from registered handlers.
     */
    public String helpMessage() {
        String commandsList = commands.stream()
                .map(command -> "/" + command.name() + " - " + command.description())
                .collect(Collectors.joining(System.lineSeparator()));
        return HELP_HEADER + System.lineSeparator() + commandsList;
    }

    /**
     * Maps handler class metadata and instance into internal registration model.
     *
     * @param handler command handler bean
     * @return registered command descriptor
     */
    private RegisteredCommand mapToRegisteredCommand(TelegramCommandHandler handler) {
        TelegramBotCommand commandMetadata = handler.getClass().getAnnotation(TelegramBotCommand.class);
        if (commandMetadata == null) {
            throw new IllegalStateException("Telegram command handler must be annotated with @TelegramBotCommand: "
                    + handler.getClass().getName());
        }

        String normalizedName = normalizeName(commandMetadata.name());
        if (normalizedName.isBlank()) {
            throw new IllegalStateException("Telegram command name must not be blank");
        }

        return new RegisteredCommand(normalizedName, commandMetadata.description(), handler);
    }

    /**
     * Normalizes configured command name to lowercase.
     *
     * @param commandName configured command name from annotation
     * @return normalized command name
     */
    private String normalizeName(String commandName) {
        return commandName.strip().toLowerCase(Locale.ROOT);
    }

    /**
     * Internal immutable representation of a registered command.
     *
     * @param name normalized command name
     * @param description command description for menu/help
     * @param handler command handler instance
     */
    private record RegisteredCommand(String name, String description, TelegramCommandHandler handler) {}
}
