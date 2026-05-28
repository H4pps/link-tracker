package backend.academy.linktracker.bot.telegram.command.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import backend.academy.linktracker.bot.application.scrapper.ScrapperLinkGateway;
import backend.academy.linktracker.bot.application.scrapper.view.ScrapperLinkView;
import backend.academy.linktracker.bot.telegram.command.CommandContext;
import backend.academy.linktracker.bot.telegram.command.ParsedCommand;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ListCommandHandlerTest {

    @Mock
    private ScrapperLinkGateway scrapperGateway;

    private ListCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ListCommandHandler(scrapperGateway);
    }

    @Test
    void returnsEmptyMessageWhenNoLinks() {
        when(scrapperGateway.listLinks(1L)).thenReturn(List.of());

        String reply = handler.handle(context(""));

        assertThat(reply).isEqualTo("Список отслеживаемых ссылок пока пуст.");
    }

    @Test
    void returnsFilteredLinksByTagCaseInsensitive() {
        when(scrapperGateway.listLinks(1L))
                .thenReturn(List.of(
                        new ScrapperLinkView(1L, "https://a", List.of("Work"), List.of()),
                        new ScrapperLinkView(2L, "https://b", List.of("other"), List.of())));

        String reply = handler.handle(context(" work "));

        assertThat(reply).contains("https://a");
        assertThat(reply).doesNotContain("https://b");
    }

    private CommandContext context(String argument) {
        return new CommandContext(1L, "/list", new ParsedCommand("/list", "list", argument));
    }
}
