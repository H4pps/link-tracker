package backend.academy.linktracker.bot.telegram.lifecycle;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import backend.academy.linktracker.bot.telegram.command.TelegramCommandRegistry;
import backend.academy.linktracker.bot.telegram.logging.BotLogger;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.BotCommand;
import com.pengrad.telegrambot.request.SetMyCommands;
import com.pengrad.telegrambot.response.BaseResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BotCommandRegistrarTest {

    @Mock
    TelegramBot telegramBot;

    @Mock
    TelegramCommandRegistry commandRegistry;

    @Mock
    BotLogger botLogger;

    @Mock
    BaseResponse response;

    BotCommandRegistrar botCommandRegistrar;

    @BeforeEach
    void setUp() {
        botCommandRegistrar = new BotCommandRegistrar(telegramBot, commandRegistry, botLogger);
    }

    @Test
    void registersCommandsMenu() {
        BotCommand[] menuCommands = {new BotCommand("start", "начало работы"), new BotCommand("help", "список команд")};
        when(commandRegistry.menuCommands()).thenReturn(menuCommands);
        when(response.isOk()).thenReturn(true);
        when(telegramBot.execute(any(SetMyCommands.class))).thenReturn(response);

        botCommandRegistrar.registerCommandsMenu();

        verify(commandRegistry).menuCommands();
        verify(telegramBot).execute(any(SetMyCommands.class));
        verify(botLogger).logCommandsMenuRegistered(true, 0);
    }

    @Test
    void logsRegistrationFailure() {
        RuntimeException failure = new RuntimeException("boom");
        when(commandRegistry.menuCommands()).thenReturn(new BotCommand[0]);
        when(telegramBot.execute(any(SetMyCommands.class))).thenThrow(failure);

        botCommandRegistrar.registerCommandsMenu();

        verify(botLogger).logCommandsMenuRegistrationFailed(failure);
    }
}
