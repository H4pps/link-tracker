package backend.academy.linktracker.bot.telegram.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import backend.academy.linktracker.bot.application.track.TrackDialogService;
import backend.academy.linktracker.bot.telegram.command.handlers.TelegramCommandHandler;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TelegramCommandProcessorTest {

    private TelegramCommandProcessor processor;

    @Mock
    private TrackDialogService trackDialogService;

    @BeforeEach
    void setUp() {
        processor = new TelegramCommandProcessor(
                new TelegramCommandRegistry(List.of(new StartHandler(), new HelpHandler(), new TrackHandler())),
                new CommandTextParser(),
                trackDialogService);
    }

    @Test
    void resolvesKnownCommand() {
        TelegramCommandProcessor.CommandProcessingResult result = processor.process(1L, "/start");

        assertThat(result.reply()).isEqualTo("start-reply");
        assertThat(result.commandForLog()).isEqualTo("start");
        assertThat(result.inputCommand()).isEqualTo("/start");
    }

    @Test
    void resolvesUnknownCommand() {
        TelegramCommandProcessor.CommandProcessingResult result = processor.process(1L, "/missing");

        assertThat(result.reply()).isEqualTo(TelegramCommandProcessor.UNKNOWN_REPLY);
        assertThat(result.commandForLog()).isEqualTo(TelegramCommandProcessor.UNKNOWN_COMMAND_NAME);
        assertThat(result.inputCommand()).isEqualTo("/missing");
    }

    @Test
    void delegatesNonCommandMessageToDialogWhenDialogIsActive() {
        when(trackDialogService.hasActiveDialog(1L)).thenReturn(true);
        when(trackDialogService.handleDialogInput(1L, "https://github.com/a/b")).thenReturn("dialog-reply");

        TelegramCommandProcessor.CommandProcessingResult result = processor.process(1L, "https://github.com/a/b");

        assertThat(result.reply()).isEqualTo("dialog-reply");
        assertThat(result.commandForLog()).isEqualTo("track-dialog");
        verify(trackDialogService, never()).cancel(1L);
    }

    @Test
    void cancelsAndRoutesAnotherCommandWhenDialogIsActive() {
        when(trackDialogService.hasActiveDialog(1L)).thenReturn(true);
        when(trackDialogService.cancel(1L)).thenReturn("cancelled");

        TelegramCommandProcessor.CommandProcessingResult result = processor.process(1L, "/help");

        assertThat(result.reply()).isEqualTo("help-reply");
        assertThat(result.commandForLog()).isEqualTo("help");
        verify(trackDialogService).cancel(1L);
    }

    @Test
    void cancelCommandReturnsCancelReplyWhenDialogIsActive() {
        when(trackDialogService.hasActiveDialog(1L)).thenReturn(true);
        when(trackDialogService.cancel(1L)).thenReturn("cancelled");

        TelegramCommandProcessor.CommandProcessingResult result = processor.process(1L, "/cancel");

        assertThat(result.reply()).isEqualTo("cancelled");
        assertThat(result.commandForLog()).isEqualTo("cancel");
    }

    @TelegramBotCommand(name = "start", description = "начало работы")
    private static class StartHandler implements TelegramCommandHandler {
        @Override
        public String handle(CommandContext context) {
            return "start-reply";
        }
    }

    @TelegramBotCommand(name = "help", description = "список команд")
    private static class HelpHandler implements TelegramCommandHandler {
        @Override
        public String handle(CommandContext context) {
            return "help-reply";
        }
    }

    @TelegramBotCommand(name = "track", description = "начать отслеживание ссылки")
    private static class TrackHandler implements TelegramCommandHandler {
        @Override
        public String handle(CommandContext context) {
            return "track-reply";
        }
    }
}
