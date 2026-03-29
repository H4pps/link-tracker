package backend.academy.linktracker.bot.telegram.command;

import static org.assertj.core.api.Assertions.assertThat;

import backend.academy.linktracker.bot.telegram.command.handlers.TelegramCommandHandler;
import java.util.List;
import org.junit.jupiter.api.Test;

class TelegramCommandProcessorTest {

    private final TelegramCommandProcessor processor = new TelegramCommandProcessor(
            new TelegramCommandRegistry(List.of(new StartHandler(), new HelpHandler())), new CommandTextParser());

    @Test
    void resolvesKnownCommand() {
        TelegramCommandProcessor.CommandProcessingResult result = processor.process("/start");

        assertThat(result.reply()).isEqualTo("start-reply");
        assertThat(result.commandForLog()).isEqualTo("start");
        assertThat(result.inputCommand()).isEqualTo("/start");
    }

    @Test
    void resolvesUnknownCommand() {
        TelegramCommandProcessor.CommandProcessingResult result = processor.process("/missing");

        assertThat(result.reply()).isEqualTo(TelegramCommandProcessor.UNKNOWN_REPLY);
        assertThat(result.commandForLog()).isEqualTo(TelegramCommandProcessor.UNKNOWN_COMMAND_NAME);
        assertThat(result.inputCommand()).isEqualTo("/missing");
    }

    @Test
    void resolvesEmptyCommandAsUnknown() {
        TelegramCommandProcessor.CommandProcessingResult result = processor.process("");

        assertThat(result.reply()).isEqualTo(TelegramCommandProcessor.UNKNOWN_REPLY);
        assertThat(result.commandForLog()).isEqualTo(TelegramCommandProcessor.UNKNOWN_COMMAND_NAME);
        assertThat(result.inputCommand()).isEmpty();
    }

    @TelegramBotCommand(name = "start", description = "начало работы")
    private static class StartHandler implements TelegramCommandHandler {
        @Override
        public String handle(String messageText) {
            return "start-reply";
        }
    }

    @TelegramBotCommand(name = "help", description = "список команд")
    private static class HelpHandler implements TelegramCommandHandler {
        @Override
        public String handle(String messageText) {
            return "help-reply";
        }
    }
}
