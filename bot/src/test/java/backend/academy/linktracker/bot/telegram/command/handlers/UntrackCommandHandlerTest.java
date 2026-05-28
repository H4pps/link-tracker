package backend.academy.linktracker.bot.telegram.command.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;

import backend.academy.linktracker.bot.application.scrapper.ScrapperLinkGateway;
import backend.academy.linktracker.bot.application.scrapper.exception.ScrapperNotFoundException;
import backend.academy.linktracker.bot.telegram.command.CommandContext;
import backend.academy.linktracker.bot.telegram.command.ParsedCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UntrackCommandHandlerTest {

    @Mock
    private ScrapperLinkGateway scrapperGateway;

    private UntrackCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new UntrackCommandHandler(scrapperGateway);
    }

    @Test
    void returnsUsageWhenUrlMissing() {
        String reply = handler.handle(context(""));

        assertThat(reply).isEqualTo("Использование: /untrack <url>");
    }

    @Test
    void returnsSuccessForValidUrl() {
        String reply = handler.handle(context("https://github.com/a/b"));

        assertThat(reply).isEqualTo("Ссылка удалена из отслеживания: https://github.com/a/b");
    }

    @Test
    void returnsNotFoundMessage() {
        doThrow(new ScrapperNotFoundException("not-found", null))
                .when(scrapperGateway)
                .removeLink(1L, "https://github.com/a/b");

        String reply = handler.handle(context("https://github.com/a/b"));

        assertThat(reply).isEqualTo("Ссылка не найдена в отслеживании.");
    }

    private CommandContext context(String argument) {
        return new CommandContext(1L, "/untrack", new ParsedCommand("/untrack", "untrack", argument));
    }
}
