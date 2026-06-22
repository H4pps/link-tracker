package com.linktracker.bot.telegram.lifecycle;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.linktracker.bot.logging.BotLogger;
import com.linktracker.bot.telegram.command.TelegramCommandRegistry;
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
        BotCommand[] menuCommands = {
            new BotCommand("start", "start using the bot"), new BotCommand("help", "command list")
        };
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
