package backend.academy.linktracker.bot.telegram.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import backend.academy.linktracker.bot.logging.BotLogger;
import backend.academy.linktracker.bot.properties.TelegramProperties;
import backend.academy.linktracker.bot.telegram.command.TelegramCommandProcessor;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.SendResponse;
import com.pengrad.telegrambot.utility.BotUtils;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TelegramBotUpdateListenerTest {

    @Mock
    TelegramBot telegramBot;

    @Mock
    TelegramCommandProcessor commandProcessor;

    @Mock
    BotLogger botLogger;

    @BeforeEach
    void setUp() {
        TelegramProperties telegramProperties = new TelegramProperties();
        telegramProperties.setPollingEnabled(true);
        telegramProperties.setUpdateListenerSleep(java.time.Duration.ofMillis(500));

        updateListener = new TelegramBotUpdateListener(telegramBot, telegramProperties, commandProcessor, botLogger);
    }

    TelegramBotUpdateListener updateListener;

    @Test
    void startupStartsUpdatesPollingAndLogs() {
        updateListener.start();

        verify(telegramBot).setUpdatesListener(any(UpdatesListener.class));
        verify(botLogger).logPollingStarted(500);
    }

    @Test
    void startCommandSendsReplyAndLogsProcessedCommand() {
        when(commandProcessor.process("/start"))
                .thenReturn(new TelegramCommandProcessor.CommandProcessingResult("reply", "start", "/start"));

        SendResponse sendResponse = org.mockito.Mockito.mock(SendResponse.class);
        when(sendResponse.isOk()).thenReturn(true);
        when(telegramBot.execute(any(SendMessage.class))).thenReturn(sendResponse);

        String responseText = processUpdateAndGetReply("/start");

        assertThat(responseText).isEqualTo("reply");
        verify(botLogger).logCommandProcessed(123L, "start", "/start", true);
    }

    @Test
    void helpCommandSendsReplyAndLogsProcessedCommand() {
        when(commandProcessor.process("/help"))
                .thenReturn(new TelegramCommandProcessor.CommandProcessingResult("help-reply", "help", "/help"));

        SendResponse sendResponse = org.mockito.Mockito.mock(SendResponse.class);
        when(sendResponse.isOk()).thenReturn(true);
        when(telegramBot.execute(any(SendMessage.class))).thenReturn(sendResponse);

        String responseText = processUpdateAndGetReply("/help");

        assertThat(responseText).isEqualTo("help-reply");
        verify(botLogger).logCommandProcessed(123L, "help", "/help", true);
    }

    @Test
    void unknownCommandWritesUnknownValueIntoLog() {
        when(commandProcessor.process("hello"))
                .thenReturn(new TelegramCommandProcessor.CommandProcessingResult(
                        TelegramCommandProcessor.UNKNOWN_REPLY,
                        TelegramCommandProcessor.UNKNOWN_COMMAND_NAME,
                        "hello"));

        SendResponse sendResponse = org.mockito.Mockito.mock(SendResponse.class);
        when(sendResponse.isOk()).thenReturn(true);
        when(telegramBot.execute(any(SendMessage.class))).thenReturn(sendResponse);

        processUpdateAndGetReply("hello");

        verify(botLogger).logCommandProcessed(123L, "unknown", "hello", true);
    }

    @Test
    void disabledPollingSkipsListenerRegistration() {
        TelegramProperties telegramProperties = new TelegramProperties();
        telegramProperties.setPollingEnabled(false);
        updateListener = new TelegramBotUpdateListener(telegramBot, telegramProperties, commandProcessor, botLogger);

        updateListener.start();

        verify(telegramBot, never()).setUpdatesListener(any(UpdatesListener.class));
        verify(botLogger).logPollingDisabled();
    }

    private String processUpdateAndGetReply(String commandText) {
        updateListener.start();

        ArgumentCaptor<UpdatesListener> updatesListenerCaptor = ArgumentCaptor.forClass(UpdatesListener.class);
        verify(telegramBot).setUpdatesListener(updatesListenerCaptor.capture());

        var update = BotUtils.parseUpdate("""
                {
                  "update_id": 1,
                  "message": {
                    "message_id": 1,
                    "chat": {
                      "id": 123,
                      "type": "private"
                    },
                    "date": 1700000000,
                    "text": "%s"
                  }
                }
                """.formatted(commandText));

        int result = updatesListenerCaptor.getValue().process(List.of(update));
        assertThat(result).isEqualTo(UpdatesListener.CONFIRMED_UPDATES_ALL);

        ArgumentCaptor<SendMessage> sendMessageCaptor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramBot).execute(sendMessageCaptor.capture());
        assertThat(sendMessageCaptor.getValue().getParameters().get("chat_id")).isEqualTo(123L);
        return sendMessageCaptor.getValue().getText();
    }
}
