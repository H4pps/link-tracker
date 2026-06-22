package com.linktracker.bot.telegram.command.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;

import com.linktracker.bot.application.scrapper.ScrapperLinkGateway;
import com.linktracker.bot.application.scrapper.exception.ScrapperNotFoundException;
import com.linktracker.bot.telegram.command.CommandContext;
import com.linktracker.bot.telegram.command.ParsedCommand;
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

        assertThat(reply).isEqualTo("Usage: /untrack <url>");
    }

    @Test
    void returnsSuccessForValidUrl() {
        String reply = handler.handle(context("https://github.com/a/b"));

        assertThat(reply).isEqualTo("Link removed from tracking: https://github.com/a/b");
    }

    @Test
    void returnsNotFoundMessage() {
        doThrow(new ScrapperNotFoundException("not-found", null))
                .when(scrapperGateway)
                .removeLink(1L, "https://github.com/a/b");

        String reply = handler.handle(context("https://github.com/a/b"));

        assertThat(reply).isEqualTo("Link is not tracked.");
    }

    private CommandContext context(String argument) {
        return new CommandContext(1L, "/untrack", new ParsedCommand("/untrack", "untrack", argument));
    }
}
