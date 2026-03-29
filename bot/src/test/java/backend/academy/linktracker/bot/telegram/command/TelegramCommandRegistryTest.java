package backend.academy.linktracker.bot.telegram.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import backend.academy.linktracker.bot.telegram.command.handlers.TelegramCommandHandler;
import org.junit.jupiter.api.Test;

class TelegramCommandRegistryTest {

    @Test
    void createsMenuCommandsInDeterministicOrder() {
        TelegramCommandRegistry registry =
                new TelegramCommandRegistry(java.util.List.of(new StartHandler(), new HelpHandler()));

        var menuCommands = registry.menuCommands();

        assertThat(menuCommands).extracting(command -> command.command()).containsExactly("help", "start");
    }

    @Test
    void buildsHelpMessageFromRegisteredCommands() {
        TelegramCommandRegistry registry =
                new TelegramCommandRegistry(java.util.List.of(new StartHandler(), new HelpHandler()));

        assertThat(registry.helpMessage()).isEqualTo("""
                        Доступные команды:
                        /help - список команд
                        /start - начало работы""");
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
