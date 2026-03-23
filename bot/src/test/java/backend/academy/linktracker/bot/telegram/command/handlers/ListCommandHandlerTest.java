package backend.academy.linktracker.bot.telegram.command.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import backend.academy.linktracker.bot.application.scrapper.ScrapperGateway;
import backend.academy.linktracker.bot.application.scrapper.ScrapperLinkView;
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
    private ScrapperGateway scrapperGateway;

    private ListCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ListCommandHandler(scrapperGateway);
    }

    @Test
    void returnsEmptyMessageWhenNoLinks() {
        when(scrapperGateway.listLinks(1L)).thenReturn(List.of());

        String reply = handler.handle(context(1L, "/list", ""));

        assertThat(reply).isEqualTo("Список отслеживаемых ссылок пока пуст.");
    }

    @Test
    void returnsFilteredLinksByTagCaseInsensitive() {
        when(scrapperGateway.listLinks(1L))
                .thenReturn(List.of(
                        new ScrapperLinkView(1L, "https://a", List.of("Work"), List.of()),
                        new ScrapperLinkView(2L, "https://b", List.of("other"), List.of())));

        String reply = handler.handle(context(1L, "/list", " work "));

        assertThat(reply).contains("https://a");
        assertThat(reply).doesNotContain("https://b");
    }

    private CommandContext context(long chatId, String inputCommand, String argument) {
        return new CommandContext(
                chatId, inputCommand, new ParsedCommand(inputCommand, inputCommand.substring(1), argument));
    }
}
