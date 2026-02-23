package backend.academy.linktracker.bot.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import backend.academy.linktracker.bot.properties.TelegramProperties;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.request.SetMyCommands;
import com.pengrad.telegrambot.response.BaseResponse;
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
    BaseResponse setMyCommandsResponse;

    TelegramProperties telegramProperties;
    TelegramBotUpdateListener updateListener;

    @BeforeEach
    void setUp() {
        telegramProperties = new TelegramProperties();
        telegramProperties.setPollingEnabled(true);

        when(setMyCommandsResponse.isOk()).thenReturn(true);
        when(telegramBot.execute(any(SetMyCommands.class))).thenReturn(setMyCommandsResponse);

        updateListener = new TelegramBotUpdateListener(telegramBot, telegramProperties, new TelegramCommandService());
    }

    @Test
    void startupRegistersCommandsAndStartsUpdatesPolling() {
        updateListener.start();

        verify(telegramBot).execute(any(SetMyCommands.class));
        verify(telegramBot).setUpdatesListener(any(UpdatesListener.class));
    }

    @Test
    void startCommandSendsGreeting() {
        String responseText = processUpdateAndGetReply("/start");

        assertThat(responseText).isEqualTo(TelegramCommandService.START_REPLY);
    }

    @Test
    void helpCommandSendsCommandsList() {
        String responseText = processUpdateAndGetReply("/help");

        assertThat(responseText).isEqualTo(TelegramCommandService.HELP_REPLY);
    }

    @Test
    void unknownCommandSendsError() {
        String responseText = processUpdateAndGetReply("/something-else");

        assertThat(responseText).isEqualTo(TelegramCommandService.UNKNOWN_REPLY);
    }

    @Test
    void disabledPollingSkipsListenerRegistration() {
        telegramProperties.setPollingEnabled(false);

        updateListener.start();

        verify(telegramBot).execute(any(SetMyCommands.class));
        verify(telegramBot, never()).setUpdatesListener(any(UpdatesListener.class));
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
