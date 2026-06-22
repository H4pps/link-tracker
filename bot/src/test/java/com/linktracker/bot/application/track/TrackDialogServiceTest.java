package com.linktracker.bot.application.track;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.linktracker.bot.application.scrapper.ScrapperLinkGateway;
import com.linktracker.bot.application.scrapper.command.AddScrapperLinkCommand;
import com.linktracker.bot.application.scrapper.exception.ScrapperConflictException;
import com.linktracker.bot.application.scrapper.exception.ScrapperGatewayException;
import com.linktracker.bot.application.scrapper.exception.ScrapperNotFoundException;
import com.linktracker.bot.application.scrapper.exception.ScrapperUnavailableException;
import com.linktracker.bot.application.track.parsing.CsvTrackDialogValueParser;
import com.linktracker.bot.application.track.service.TrackDialogService;
import com.linktracker.bot.application.track.validation.SupportedTrackUrlValidator;
import com.linktracker.bot.infrastructure.memory.InMemoryTrackDialogStateRepository;
import com.linktracker.bot.logging.BotLogger;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TrackDialogServiceTest {

    @Mock
    private ScrapperLinkGateway scrapperGateway;

    @Mock
    private BotLogger botLogger;

    private TrackDialogService service;

    @BeforeEach
    void setUp() {
        service = new TrackDialogService(
                new InMemoryTrackDialogStateRepository(),
                scrapperGateway,
                new SupportedTrackUrlValidator(),
                new CsvTrackDialogValueParser(),
                botLogger);
    }

    @Test
    void trackDialogHappyPathAddsLink() {
        String start = service.start(10L);
        String afterLink = service.handleDialogInput(10L, "https://github.com/octocat/Hello-World");
        String afterTags = service.handleDialogInput(10L, "work, java");
        String afterFilters = service.handleDialogInput(10L, "f1, f2");

        assertThat(start).isEqualTo(TrackDialogService.TRACK_REPLY);
        assertThat(afterLink).isEqualTo(TrackDialogService.TAGS_REPLY);
        assertThat(afterTags).isEqualTo(TrackDialogService.FILTERS_REPLY);
        assertThat(afterFilters).isEqualTo("Link added to tracking: https://github.com/octocat/Hello-World");
        verify(scrapperGateway).addLink(anyLong(), any());
    }

    @Test
    void invalidUrlKeepsAwaitingLink() {
        service.start(10L);

        String first = service.handleDialogInput(10L, "example://invalid");
        String second = service.handleDialogInput(10L, "https://stackoverflow.com/questions/1/test");

        assertThat(first).isEqualTo(TrackDialogService.INVALID_URL_REPLY);
        assertThat(second).isEqualTo(TrackDialogService.TAGS_REPLY);
        verify(scrapperGateway, never()).addLink(anyLong(), any());
    }

    @Test
    void duplicateLinkMapsToExactMessage() {
        service.start(10L);
        service.handleDialogInput(10L, "https://github.com/octocat/Hello-World");
        service.handleDialogInput(10L, "work");
        when(scrapperGateway.addLink(anyLong(), any())).thenThrow(new ScrapperConflictException("conflict", null));

        String reply = service.handleDialogInput(10L, "");

        assertThat(reply).isEqualTo("Link is already tracked");
    }

    @Test
    void notFoundGatewayErrorMapsToChatNotRegisteredMessage() {
        service.start(10L);
        service.handleDialogInput(10L, "https://github.com/octocat/Hello-World");
        service.handleDialogInput(10L, "work");
        when(scrapperGateway.addLink(anyLong(), any())).thenThrow(new ScrapperNotFoundException("not found", null));

        String reply = service.handleDialogInput(10L, "");

        assertThat(reply).isEqualTo(TrackDialogService.CHAT_NOT_REGISTERED_REPLY);
    }

    @Test
    void unavailableGatewayErrorMapsToScrapperUnavailableMessage() {
        service.start(10L);
        service.handleDialogInput(10L, "https://github.com/octocat/Hello-World");
        service.handleDialogInput(10L, "work");
        when(scrapperGateway.addLink(anyLong(), any()))
                .thenThrow(new ScrapperUnavailableException("unavailable", null));

        String reply = service.handleDialogInput(10L, "");

        assertThat(reply).isEqualTo(TrackDialogService.SCRAPPER_UNAVAILABLE_REPLY);
    }

    @Test
    void unknownGatewayErrorMapsToScrapperUnavailableMessage() {
        service.start(10L);
        service.handleDialogInput(10L, "https://github.com/octocat/Hello-World");
        service.handleDialogInput(10L, "work");
        when(scrapperGateway.addLink(anyLong(), any())).thenThrow(new ScrapperGatewayException("gateway", null));

        String reply = service.handleDialogInput(10L, "");

        assertThat(reply).isEqualTo(TrackDialogService.SCRAPPER_UNAVAILABLE_REPLY);
    }

    @Test
    void parseEmptyTagsAndFiltersAsEmptyLists() {
        service.start(10L);
        service.handleDialogInput(10L, "https://github.com/octocat/Hello-World");
        service.handleDialogInput(10L, " ");
        service.handleDialogInput(10L, " ");

        verify(scrapperGateway)
                .addLink(
                        10L,
                        new AddScrapperLinkCommand("https://github.com/octocat/Hello-World", List.of(), List.of()));
    }

    @Test
    void cancelWorksFromAnyNonIdleState() {
        service.start(10L);
        assertThat(service.cancel(10L)).isEqualTo(TrackDialogService.CANCELLED_REPLY);

        service.start(10L);
        service.handleDialogInput(10L, "https://github.com/octocat/Hello-World");
        assertThat(service.cancel(10L)).isEqualTo(TrackDialogService.CANCELLED_REPLY);

        service.start(10L);
        service.handleDialogInput(10L, "https://github.com/octocat/Hello-World");
        service.handleDialogInput(10L, "work");
        assertThat(service.cancel(10L)).isEqualTo(TrackDialogService.CANCELLED_REPLY);
    }

    @Test
    void singleTagAdvancesToFiltersStep() {
        service.start(10L);
        service.handleDialogInput(10L, "https://github.com/octocat/Hello-World");

        String afterSingleTag = service.handleDialogInput(10L, "work");

        assertThat(afterSingleTag).isEqualTo(TrackDialogService.FILTERS_REPLY);
    }

    @Test
    void unicodeCommaIsHandledAsTagSeparator() {
        service.start(10L);
        service.handleDialogInput(10L, "https://github.com/octocat/Hello-World");
        service.handleDialogInput(10L, "work，java");
        service.handleDialogInput(10L, "");

        verify(scrapperGateway)
                .addLink(
                        10L,
                        new AddScrapperLinkCommand(
                                "https://github.com/octocat/Hello-World", List.of("work", "java"), List.of()));
    }
}
