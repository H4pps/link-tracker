package backend.academy.linktracker.bot.telegram.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import backend.academy.linktracker.bot.telegram.command.handlers.TelegramCommandHandler;
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
                        Доступные команды:
                        /help - список команд
                        /list - показать отслеживаемые ссылки
                        /start - начало работы
                        /track - начать отслеживание ссылки
                        /untrack - прекратить отслеживание ссылки""");
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

    @TelegramBotCommand(name = "start", description = "начало работы")
    private static class StartHandler implements TelegramCommandHandler {
        @Override
        public String handle(String messageText) {
            return "start";
        }
    }

    @TelegramBotCommand(name = "help", description = "список команд")
    private static class HelpHandler implements TelegramCommandHandler {
        @Override
        public String handle(String messageText) {
            return "help";
        }
    }

    @TelegramBotCommand(name = "track", description = "начать отслеживание ссылки")
    private static class TrackHandler implements TelegramCommandHandler {
        @Override
        public String handle(String messageText) {
            return "track";
        }
    }

    @TelegramBotCommand(name = "untrack", description = "прекратить отслеживание ссылки")
    private static class UntrackHandler implements TelegramCommandHandler {
        @Override
        public String handle(String messageText) {
            return "untrack";
        }
    }

    @TelegramBotCommand(name = "list", description = "показать отслеживаемые ссылки")
    private static class ListHandler implements TelegramCommandHandler {
        @Override
        public String handle(String messageText) {
            return "list";
        }
    }

    @TelegramBotCommand(name = "start", description = "дубль")
    private static class DuplicateStartHandler implements TelegramCommandHandler {
        @Override
        public String handle(String messageText) {
            return "duplicate";
        }
    }

    private static class MissingAnnotationHandler implements TelegramCommandHandler {
        @Override
        public String handle(String messageText) {
            return "missing";
        }
    }
}
