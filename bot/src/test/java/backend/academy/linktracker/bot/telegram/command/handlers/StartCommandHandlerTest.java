package backend.academy.linktracker.bot.telegram.command.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import backend.academy.linktracker.bot.application.scrapper.ScrapperChatGateway;
import backend.academy.linktracker.bot.application.scrapper.exception.ScrapperConflictException;
import backend.academy.linktracker.bot.telegram.command.CommandContext;
import backend.academy.linktracker.bot.telegram.command.ParsedCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StartCommandHandlerTest {

    @Mock
    private ScrapperChatGateway scrapperGateway;

    private StartCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new StartCommandHandler(scrapperGateway);
    }

    @Test
    void registersChatAndReturnsWelcome() {
        String reply = handler.handle(new CommandContext(1L, "/start", new ParsedCommand("/start", "start", "")));

        verify(scrapperGateway).registerChat(1L);
        assertThat(reply).isEqualTo("Добро пожаловать! Используйте /help, чтобы посмотреть доступные команды.");
    }

    @Test
    void duplicateRegistrationStillReturnsWelcome() {
        doThrow(new ScrapperConflictException("conflict", null))
                .when(scrapperGateway)
                .registerChat(1L);

        String reply = handler.handle(new CommandContext(1L, "/start", new ParsedCommand("/start", "start", "")));

        assertThat(reply).isEqualTo("Добро пожаловать! Используйте /help, чтобы посмотреть доступные команды.");
    }
}
