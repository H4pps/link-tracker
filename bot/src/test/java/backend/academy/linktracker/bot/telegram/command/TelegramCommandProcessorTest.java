package backend.academy.linktracker.bot.telegram.command;

import static org.assertj.core.api.Assertions.assertThat;

import backend.academy.linktracker.bot.telegram.command.handlers.TelegramCommandHandler;
import java.util.List;
import org.junit.jupiter.api.Test;

class TelegramCommandProcessorTest {

    private final TelegramCommandProcessor processor = new TelegramCommandProcessor(
            new TelegramCommandRegistry(List.of(
                    new StartHandler(),
                    new HelpHandler(),
                    new TrackHandler(),
                    new UntrackHandler(),
                    new ListHandler())),
            new CommandTextParser());

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

    @Test
    void resolvesTrackCommand() {
        TelegramCommandProcessor.CommandProcessingResult result = processor.process("/track");

        assertThat(result.reply()).isEqualTo("track-reply");
        assertThat(result.commandForLog()).isEqualTo("track");
        assertThat(result.inputCommand()).isEqualTo("/track");
    }

    @Test
    void resolvesUntrackCommand() {
        TelegramCommandProcessor.CommandProcessingResult result = processor.process("/untrack");

        assertThat(result.reply()).isEqualTo("untrack-reply");
        assertThat(result.commandForLog()).isEqualTo("untrack");
        assertThat(result.inputCommand()).isEqualTo("/untrack");
    }

    @Test
    void resolvesListCommand() {
        TelegramCommandProcessor.CommandProcessingResult result = processor.process("/list");

        assertThat(result.reply()).isEqualTo("list-reply");
        assertThat(result.commandForLog()).isEqualTo("list");
        assertThat(result.inputCommand()).isEqualTo("/list");
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

    @TelegramBotCommand(name = "track", description = "начать отслеживание ссылки")
    private static class TrackHandler implements TelegramCommandHandler {
        @Override
        public String handle(String messageText) {
            return "track-reply";
        }
    }

    @TelegramBotCommand(name = "untrack", description = "прекратить отслеживание ссылки")
    private static class UntrackHandler implements TelegramCommandHandler {
        @Override
        public String handle(String messageText) {
            return "untrack-reply";
        }
    }

    @TelegramBotCommand(name = "list", description = "показать отслеживаемые ссылки")
    private static class ListHandler implements TelegramCommandHandler {
        @Override
        public String handle(String messageText) {
            return "list-reply";
        }
    }
}
