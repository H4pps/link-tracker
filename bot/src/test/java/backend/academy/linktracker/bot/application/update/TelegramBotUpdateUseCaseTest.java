package backend.academy.linktracker.bot.application.update;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import backend.academy.linktracker.bot.application.telegram.TelegramOutboundSender;
import backend.academy.linktracker.bot.logging.BotLogger;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TelegramBotUpdateUseCaseTest {

    @Mock
    private TelegramOutboundSender outboundSender;

    @Mock
    private BotLogger botLogger;

    private TelegramBotUpdateUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new TelegramBotUpdateUseCase(outboundSender, botLogger);
    }

    @Test
    void sendsNotificationToAllChats() {
        when(outboundSender.sendMessage(10L, "Обновление по ссылке: https://github.com/a/b\nchanged"))
                .thenReturn(true);
        when(outboundSender.sendMessage(20L, "Обновление по ссылке: https://github.com/a/b\nchanged"))
                .thenReturn(true);

        useCase.processLinkUpdate(new LinkUpdateCommand(1L, "https://github.com/a/b", "changed", List.of(10L, 20L)));

        verify(outboundSender, times(2))
                .sendMessage(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void oneFailedSendDoesNotStopOtherChats() {
        doThrow(new RuntimeException("boom"))
                .when(outboundSender)
                .sendMessage(10L, "Обновление по ссылке: https://github.com/a/b");

        assertThatThrownBy(() -> useCase.processLinkUpdate(
                        new LinkUpdateCommand(1L, "https://github.com/a/b", null, List.of(10L, 20L))))
                .isInstanceOf(RuntimeException.class);

        verify(outboundSender).sendMessage(20L, "Обновление по ссылке: https://github.com/a/b");
    }

    @Test
    void falseSendResultTriggersFailureAfterFanout() {
        when(outboundSender.sendMessage(10L, "Обновление по ссылке: https://github.com/a/b"))
                .thenReturn(false);
        when(outboundSender.sendMessage(20L, "Обновление по ссылке: https://github.com/a/b"))
                .thenReturn(true);

        assertThatThrownBy(() -> useCase.processLinkUpdate(
                        new LinkUpdateCommand(1L, "https://github.com/a/b", null, List.of(10L, 20L))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to deliver update to chat 10");

        verify(outboundSender).sendMessage(20L, "Обновление по ссылке: https://github.com/a/b");
    }
}
