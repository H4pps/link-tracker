package backend.academy.linktracker.scrapper.application.update;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import backend.academy.linktracker.scrapper.application.external.ExternalSourceException;
import backend.academy.linktracker.scrapper.application.external.ExternalSourceReader;
import backend.academy.linktracker.scrapper.application.external.GithubLinkSource;
import backend.academy.linktracker.scrapper.application.external.LinkSourceResolver;
import backend.academy.linktracker.scrapper.application.repository.RepositoryPageRequest;
import backend.academy.linktracker.scrapper.application.repository.ScrapperLinkRepository;
import backend.academy.linktracker.scrapper.domain.model.TrackedLinkSnapshot;
import backend.academy.linktracker.scrapper.properties.SchedulerProperties;
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
    private LinkSourceResolver linkSourceResolver;

    @Mock
    private ExternalSourceReader reader;

    @Mock
    private LinkUpdateCheckpointRepository checkpointRepository;

    @Mock
    private BotNotificationSender notificationSender;

    @Mock
    private ScrapperLogger scrapperLogger;

    private SchedulerProperties schedulerProperties;
    private LinkUpdateSchedulerUseCase useCase;

    @BeforeEach
    void setUp() {
        lenient().when(reader.supports(any())).thenReturn(true);
        schedulerProperties = new SchedulerProperties();
        schedulerProperties.setLinkPageSize(2);
        useCase = new LinkUpdateSchedulerUseCase(
                linkRepository,
                linkSourceResolver,
                List.of(reader),
                checkpointRepository,
                notificationSender,
                scrapperLogger,
                schedulerProperties);
    }

    @Test
    void firstSeenSeedsCheckpointWithoutNotification() {
        TrackedLinkSnapshot snapshot = new TrackedLinkSnapshot(1L, "https://github.com/a/b", List.of(10L));
        when(linkRepository.findAllTrackedLinks(new RepositoryPageRequest(2, 0))).thenReturn(List.of(snapshot));
        when(linkSourceResolver.resolve(snapshot.url())).thenReturn(Optional.of(new GithubLinkSource("a", "b")));
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
        when(linkRepository.findAllTrackedLinks(new RepositoryPageRequest(2, 0))).thenReturn(List.of(snapshot));
        when(linkSourceResolver.resolve(snapshot.url())).thenReturn(Optional.of(new GithubLinkSource("a", "b")));
        when(reader.fetchLastUpdated(any())).thenReturn(now);
        when(checkpointRepository.findByUrl(snapshot.url())).thenReturn(Optional.of(now));

        useCase.checkUpdates();

        verify(notificationSender, never()).send(any());
        verify(checkpointRepository, never()).save(snapshot.url(), now);
    }

    @Test
    void changedTimestampSendsNotificationAndUpdatesCheckpoint() {
        TrackedLinkSnapshot snapshot = new TrackedLinkSnapshot(1L, "https://github.com/a/b", List.of(10L, 20L));
        when(linkRepository.findAllTrackedLinks(new RepositoryPageRequest(2, 0))).thenReturn(List.of(snapshot));
        when(linkSourceResolver.resolve(snapshot.url())).thenReturn(Optional.of(new GithubLinkSource("a", "b")));
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
        when(linkRepository.findAllTrackedLinks(new RepositoryPageRequest(2, 0))).thenReturn(List.of(first, second));
        when(linkRepository.findAllTrackedLinks(new RepositoryPageRequest(2, 2))).thenReturn(List.of());
        when(linkSourceResolver.resolve(first.url())).thenReturn(Optional.of(new GithubLinkSource("a", "b")));
        when(linkSourceResolver.resolve(second.url())).thenReturn(Optional.of(new GithubLinkSource("c", "d")));
        when(reader.fetchLastUpdated(new GithubLinkSource("a", "b")))
                .thenThrow(new ExternalSourceException("boom", null));
        when(reader.fetchLastUpdated(new GithubLinkSource("c", "d"))).thenReturn(Instant.parse("2024-03-01T00:00:00Z"));
        when(checkpointRepository.findByUrl(second.url())).thenReturn(Optional.empty());

        useCase.checkUpdates();

        verify(checkpointRepository, times(1)).save(second.url(), Instant.parse("2024-03-01T00:00:00Z"));
    }

    @Test
    void checkUpdatesProcessesPagesUntilShortPage() {
        TrackedLinkSnapshot first = new TrackedLinkSnapshot(1L, "https://github.com/a/b", List.of(10L));
        TrackedLinkSnapshot second = new TrackedLinkSnapshot(2L, "https://github.com/c/d", List.of(20L));
        TrackedLinkSnapshot third = new TrackedLinkSnapshot(3L, "https://github.com/e/f", List.of(30L));

        when(linkRepository.findAllTrackedLinks(new RepositoryPageRequest(2, 0))).thenReturn(List.of(first, second));
        when(linkRepository.findAllTrackedLinks(new RepositoryPageRequest(2, 2))).thenReturn(List.of(third));
        when(linkSourceResolver.resolve(any())).thenReturn(Optional.of(new GithubLinkSource("a", "b")));
        when(reader.fetchLastUpdated(any())).thenReturn(Instant.parse("2024-05-01T00:00:00Z"));
        when(checkpointRepository.findByUrl(any())).thenReturn(Optional.empty());

        useCase.checkUpdates();

        verify(linkRepository).findAllTrackedLinks(new RepositoryPageRequest(2, 0));
        verify(linkRepository).findAllTrackedLinks(new RepositoryPageRequest(2, 2));
        verify(linkRepository, never()).findAllTrackedLinks(new RepositoryPageRequest(2, 4));
        verify(checkpointRepository, times(3)).save(any(), eq(Instant.parse("2024-05-01T00:00:00Z")));
    }

    @Test
    void checkUpdatesStopsWhenFirstPageIsEmpty() {
        when(linkRepository.findAllTrackedLinks(new RepositoryPageRequest(2, 0))).thenReturn(List.of());

        useCase.checkUpdates();

        verify(linkRepository).findAllTrackedLinks(new RepositoryPageRequest(2, 0));
        verify(linkSourceResolver, never()).resolve(any());
    }
}
