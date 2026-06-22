package com.linktracker.bot.telegram.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.linktracker.bot.telegram.command.handlers.TelegramCommandHandler;
import org.junit.jupiter.api.Test;

class TelegramCommandRegistryTest {

    @Test
    void createsMenuCommandsInDeterministicOrder() {
        TelegramCommandRegistry registry = new TelegramCommandRegistry(java.util.List.of(
                new StartHandler(), new HelpHandler(), new TrackHandler(), new UntrackHandler(), new ListHandler()));

        var menuCommands = registry.menuCommands();

        assertThat(menuCommands)
                .extracting(command -> command.command())
                .containsExactly("help", "list", "start", "track", "untrack");
    }

    @Test
    void buildsHelpMessageFromRegisteredCommands() {
        TelegramCommandRegistry registry = new TelegramCommandRegistry(java.util.List.of(
                new StartHandler(), new HelpHandler(), new TrackHandler(), new UntrackHandler(), new ListHandler()));

        assertThat(registry.helpMessage()).isEqualTo("""
                        Available commands:
                        /help - command list
                        /list - show tracked links
                        /start - start using the bot
                        /track - start tracking a link
                        /untrack - stop tracking a link""");
    }

    @Test
    void failsFastWhenDuplicateCommandNamesDetected() {
        assertThatThrownBy(() ->
                        new TelegramCommandRegistry(java.util.List.of(new StartHandler(), new DuplicateStartHandler())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate Telegram command: start");
    }

    @Test
    void failsWhenHandlerHasNoCommandAnnotation() {
        assertThatThrownBy(() -> new TelegramCommandRegistry(java.util.List.of(new MissingAnnotationHandler())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("@TelegramBotCommand");
    }

    @TelegramBotCommand(name = "start", description = "start using the bot")
    private static class StartHandler implements TelegramCommandHandler {
        @Override
        public String handle(CommandContext context) {
            return "start";
        }
    }

    @TelegramBotCommand(name = "help", description = "command list")
    private static class HelpHandler implements TelegramCommandHandler {
        @Override
        public String handle(CommandContext context) {
            return "help";
        }
    }

    @TelegramBotCommand(name = "track", description = "start tracking a link")
    private static class TrackHandler implements TelegramCommandHandler {
        @Override
        public String handle(CommandContext context) {
            return "track";
        }
    }

    @TelegramBotCommand(name = "untrack", description = "stop tracking a link")
    private static class UntrackHandler implements TelegramCommandHandler {
        @Override
        public String handle(CommandContext context) {
            return "untrack";
        }
    }

    @TelegramBotCommand(name = "list", description = "show tracked links")
    private static class ListHandler implements TelegramCommandHandler {
        @Override
        public String handle(CommandContext context) {
            return "list";
        }
    }

    @TelegramBotCommand(name = "start", description = "duplicate")
    private static class DuplicateStartHandler implements TelegramCommandHandler {
        @Override
        public String handle(CommandContext context) {
            return "duplicate";
        }
    }

    private static class MissingAnnotationHandler implements TelegramCommandHandler {
        @Override
        public String handle(CommandContext context) {
            return "missing";
        }
    }
}
