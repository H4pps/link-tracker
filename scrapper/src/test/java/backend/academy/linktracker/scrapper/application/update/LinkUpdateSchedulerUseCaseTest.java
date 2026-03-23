package backend.academy.linktracker.scrapper.application.update;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import backend.academy.linktracker.scrapper.application.external.ExternalSourceException;
import backend.academy.linktracker.scrapper.application.external.ExternalSourceReader;
import backend.academy.linktracker.scrapper.application.external.GithubLinkSource;
import backend.academy.linktracker.scrapper.application.external.LinkSourceResolver;
import backend.academy.linktracker.scrapper.application.repository.ScrapperLinkRepository;
import backend.academy.linktracker.scrapper.domain.model.TrackedLinkSnapshot;
import backend.academy.linktracker.scrapper.logging.ScrapperLogger;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LinkUpdateSchedulerUseCaseTest {

    @Mock
    private ScrapperLinkRepository linkRepository;

    @Mock
    private LinkSourceResolver resolver;

    @Mock
    private ExternalSourceReader reader;

    @Mock
    private LinkUpdateCheckpointRepository checkpointRepository;

    @Mock
    private BotNotificationSender notificationSender;

    @Mock
    private ScrapperLogger scrapperLogger;

    private LinkUpdateSchedulerUseCase useCase;

    @BeforeEach
    void setUp() {
        when(reader.supports(any())).thenReturn(true);
        useCase = new LinkUpdateSchedulerUseCase(
                linkRepository, resolver, List.of(reader), checkpointRepository, notificationSender, scrapperLogger);
    }

    @Test
    void firstSeenSeedsCheckpointWithoutNotification() {
        TrackedLinkSnapshot snapshot = new TrackedLinkSnapshot(1L, "https://github.com/a/b", List.of(10L));
        when(linkRepository.findAllTrackedLinks()).thenReturn(List.of(snapshot));
        when(resolver.resolve(snapshot.url())).thenReturn(Optional.of(new GithubLinkSource("a", "b")));
        when(reader.fetchLastUpdated(any())).thenReturn(Instant.parse("2024-01-01T00:00:00Z"));
        when(checkpointRepository.findByUrl(snapshot.url())).thenReturn(Optional.empty());

        useCase.checkUpdates();

        verify(notificationSender, never()).send(any());
        verify(checkpointRepository).save(snapshot.url(), Instant.parse("2024-01-01T00:00:00Z"));
    }

    @Test
    void unchangedTimestampDoesNotNotify() {
        TrackedLinkSnapshot snapshot = new TrackedLinkSnapshot(1L, "https://github.com/a/b", List.of(10L));
        Instant now = Instant.parse("2024-01-01T00:00:00Z");
        when(linkRepository.findAllTrackedLinks()).thenReturn(List.of(snapshot));
        when(resolver.resolve(snapshot.url())).thenReturn(Optional.of(new GithubLinkSource("a", "b")));
        when(reader.fetchLastUpdated(any())).thenReturn(now);
        when(checkpointRepository.findByUrl(snapshot.url())).thenReturn(Optional.of(now));

        useCase.checkUpdates();

        verify(notificationSender, never()).send(any());
        verify(checkpointRepository, never()).save(snapshot.url(), now);
    }

    @Test
    void changedTimestampSendsNotificationAndUpdatesCheckpoint() {
        TrackedLinkSnapshot snapshot = new TrackedLinkSnapshot(1L, "https://github.com/a/b", List.of(10L, 20L));
        when(linkRepository.findAllTrackedLinks()).thenReturn(List.of(snapshot));
        when(resolver.resolve(snapshot.url())).thenReturn(Optional.of(new GithubLinkSource("a", "b")));
        when(reader.fetchLastUpdated(any())).thenReturn(Instant.parse("2024-02-01T00:00:00Z"));
        when(checkpointRepository.findByUrl(snapshot.url()))
                .thenReturn(Optional.of(Instant.parse("2024-01-01T00:00:00Z")));
        when(notificationSender.send(any())).thenReturn(true);

        useCase.checkUpdates();

        verify(notificationSender).send(any());
        verify(checkpointRepository).save(snapshot.url(), Instant.parse("2024-02-01T00:00:00Z"));
    }

    @Test
    void sourceFailureIsIsolatedAndBatchContinues() {
        TrackedLinkSnapshot first = new TrackedLinkSnapshot(1L, "https://github.com/a/b", List.of(10L));
        TrackedLinkSnapshot second = new TrackedLinkSnapshot(2L, "https://github.com/c/d", List.of(20L));
        when(linkRepository.findAllTrackedLinks()).thenReturn(List.of(first, second));
        when(resolver.resolve(first.url())).thenReturn(Optional.of(new GithubLinkSource("a", "b")));
        when(resolver.resolve(second.url())).thenReturn(Optional.of(new GithubLinkSource("c", "d")));
        when(reader.fetchLastUpdated(new GithubLinkSource("a", "b")))
                .thenThrow(new ExternalSourceException("boom", null));
        when(reader.fetchLastUpdated(new GithubLinkSource("c", "d"))).thenReturn(Instant.parse("2024-03-01T00:00:00Z"));
        when(checkpointRepository.findByUrl(second.url())).thenReturn(Optional.empty());

        useCase.checkUpdates();

        verify(checkpointRepository, times(1)).save(second.url(), Instant.parse("2024-03-01T00:00:00Z"));
    }
}
