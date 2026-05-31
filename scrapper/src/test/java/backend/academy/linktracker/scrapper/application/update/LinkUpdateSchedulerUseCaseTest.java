package backend.academy.linktracker.scrapper.application.update;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import backend.academy.linktracker.scrapper.application.external.ExternalSourceException;
import backend.academy.linktracker.scrapper.application.external.ExternalSourceReader;
import backend.academy.linktracker.scrapper.application.external.LinkSourceResolver;
import backend.academy.linktracker.scrapper.application.external.link.LinkSource;
import backend.academy.linktracker.scrapper.application.external.link.github.GithubLinkSource;
import backend.academy.linktracker.scrapper.application.external.link.stackoverflow.StackoverflowQuestionLinkSource;
import backend.academy.linktracker.scrapper.application.external.update.ExternalUpdate;
import backend.academy.linktracker.scrapper.application.external.update.ExternalUpdateType;
import backend.academy.linktracker.scrapper.application.link.ScrapperLinkRepository;
import backend.academy.linktracker.scrapper.application.pagination.RepositoryPageRequest;
import backend.academy.linktracker.scrapper.domain.model.TrackedLinkSnapshot;
import backend.academy.linktracker.scrapper.logging.ScrapperLogger;
import backend.academy.linktracker.scrapper.properties.SchedulerProperties;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LinkUpdateSchedulerUseCaseTest {

    private static final int PREVIEW_LIMIT = 200;

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
        schedulerProperties.setWorkerCount(1);
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
        when(linkRepository.findAllTrackedLinks(new RepositoryPageRequest(2, 0)))
                .thenReturn(List.of(snapshot));
        when(linkSourceResolver.resolve(snapshot.url())).thenReturn(Optional.of(new GithubLinkSource("a", "b")));
        when(reader.fetchLatestUpdate(any())).thenReturn(externalUpdateAt(Instant.parse("2024-01-01T00:00:00Z")));
        when(checkpointRepository.findByUrl(snapshot.url())).thenReturn(Optional.empty());

        useCase.checkUpdates();

        verify(notificationSender, never()).send(any());
        verify(checkpointRepository).save(snapshot.url(), Instant.parse("2024-01-01T00:00:00Z"));
    }

    @Test
    void unchangedTimestampDoesNotNotify() {
        TrackedLinkSnapshot snapshot = new TrackedLinkSnapshot(1L, "https://github.com/a/b", List.of(10L));
        Instant now = Instant.parse("2024-01-01T00:00:00Z");
        when(linkRepository.findAllTrackedLinks(new RepositoryPageRequest(2, 0)))
                .thenReturn(List.of(snapshot));
        when(linkSourceResolver.resolve(snapshot.url())).thenReturn(Optional.of(new GithubLinkSource("a", "b")));
        when(reader.fetchLatestUpdate(any())).thenReturn(externalUpdateAt(now));
        when(checkpointRepository.findByUrl(snapshot.url())).thenReturn(Optional.of(now));

        useCase.checkUpdates();

        verify(notificationSender, never()).send(any());
        verify(checkpointRepository, never()).save(snapshot.url(), now);
    }

    @Test
    void emptyExternalUpdateSkipsLinkWithoutNotificationOrCheckpoint() {
        TrackedLinkSnapshot snapshot = new TrackedLinkSnapshot(1L, "https://github.com/a/b", List.of(10L));
        when(linkRepository.findAllTrackedLinks(new RepositoryPageRequest(2, 0)))
                .thenReturn(List.of(snapshot));
        when(linkSourceResolver.resolve(snapshot.url())).thenReturn(Optional.of(new GithubLinkSource("a", "b")));
        when(reader.fetchLatestUpdate(any())).thenReturn(Optional.empty());

        useCase.checkUpdates();

        verify(notificationSender, never()).send(any());
        verify(checkpointRepository, never()).save(any(), any());
        verify(checkpointRepository, never()).findByUrl(any());
    }

    @Test
    void changedTimestampSendsNotificationAndUpdatesCheckpoint() {
        TrackedLinkSnapshot snapshot = new TrackedLinkSnapshot(1L, "https://github.com/a/b", List.of(10L, 20L));
        when(linkRepository.findAllTrackedLinks(new RepositoryPageRequest(2, 0)))
                .thenReturn(List.of(snapshot));
        when(linkSourceResolver.resolve(snapshot.url())).thenReturn(Optional.of(new GithubLinkSource("a", "b")));
        when(reader.fetchLatestUpdate(any())).thenReturn(externalUpdateAt(Instant.parse("2024-02-01T00:00:00Z")));
        when(checkpointRepository.findByUrl(snapshot.url()))
                .thenReturn(Optional.of(Instant.parse("2024-01-01T00:00:00Z")));
        when(notificationSender.send(any())).thenReturn(true);

        useCase.checkUpdates();

        verify(notificationSender).send(any());
        verify(checkpointRepository).save(snapshot.url(), Instant.parse("2024-02-01T00:00:00Z"));
    }

    @Test
    void changedTimestampDoesNotSaveCheckpointWhenNotificationFails() {
        TrackedLinkSnapshot snapshot = new TrackedLinkSnapshot(1L, "https://github.com/a/b", List.of(10L, 20L));
        Instant previousTimestamp = Instant.parse("2024-01-01T00:00:00Z");
        Instant currentTimestamp = Instant.parse("2024-02-01T00:00:00Z");
        when(linkRepository.findAllTrackedLinks(new RepositoryPageRequest(2, 0)))
                .thenReturn(List.of(snapshot));
        when(linkSourceResolver.resolve(snapshot.url())).thenReturn(Optional.of(new GithubLinkSource("a", "b")));
        when(reader.fetchLatestUpdate(any())).thenReturn(externalUpdateAt(currentTimestamp));
        when(checkpointRepository.findByUrl(snapshot.url())).thenReturn(Optional.of(previousTimestamp));
        when(notificationSender.send(any())).thenReturn(false);

        useCase.checkUpdates();

        verify(notificationSender).send(any());
        verify(checkpointRepository, never()).save(snapshot.url(), currentTimestamp);
    }

    @Test
    void workerCountOneKeepsSequentialProcessing() throws Exception {
        schedulerProperties.setWorkerCount(1);
        TrackedLinkSnapshot first = new TrackedLinkSnapshot(1L, "https://github.com/a/b", List.of(10L));
        TrackedLinkSnapshot second = new TrackedLinkSnapshot(2L, "https://github.com/c/d", List.of(20L));
        GithubLinkSource firstSource = new GithubLinkSource("a", "b");
        GithubLinkSource secondSource = new GithubLinkSource("c", "d");
        Instant timestamp = Instant.parse("2024-06-01T00:00:00Z");
        CountDownLatch secondFetchStarted = new CountDownLatch(1);

        when(linkRepository.findAllTrackedLinks(new RepositoryPageRequest(2, 0)))
                .thenReturn(List.of(first, second));
        when(linkRepository.findAllTrackedLinks(new RepositoryPageRequest(2, 2)))
                .thenReturn(List.of());
        when(linkSourceResolver.resolve(first.url())).thenReturn(Optional.of(firstSource));
        when(linkSourceResolver.resolve(second.url())).thenReturn(Optional.of(secondSource));
        when(reader.fetchLatestUpdate(firstSource)).thenAnswer(invocation -> {
            boolean overlapped = secondFetchStarted.await(200, TimeUnit.MILLISECONDS);
            assertThat(overlapped).isFalse();
            return externalUpdateAt(timestamp);
        });
        when(reader.fetchLatestUpdate(secondSource)).thenAnswer(invocation -> {
            secondFetchStarted.countDown();
            return externalUpdateAt(timestamp);
        });
        when(checkpointRepository.findByUrl(any())).thenReturn(Optional.empty());

        useCase.checkUpdates();

        verify(checkpointRepository, times(2)).save(any(), eq(timestamp));
    }

    @Test
    void workerCountTwoProcessesSamePageConcurrently() throws Exception {
        schedulerProperties.setWorkerCount(2);
        TrackedLinkSnapshot first = new TrackedLinkSnapshot(1L, "https://github.com/a/b", List.of(10L));
        TrackedLinkSnapshot second = new TrackedLinkSnapshot(2L, "https://github.com/c/d", List.of(20L));
        Instant timestamp = Instant.parse("2024-06-01T00:00:00Z");
        CountDownLatch bothFetchesStarted = new CountDownLatch(2);
        CountDownLatch releaseFetches = new CountDownLatch(1);

        when(linkRepository.findAllTrackedLinks(new RepositoryPageRequest(2, 0)))
                .thenReturn(List.of(first, second));
        when(linkRepository.findAllTrackedLinks(new RepositoryPageRequest(2, 2)))
                .thenReturn(List.of());
        when(linkSourceResolver.resolve(first.url())).thenReturn(Optional.of(new GithubLinkSource("a", "b")));
        when(linkSourceResolver.resolve(second.url())).thenReturn(Optional.of(new GithubLinkSource("c", "d")));
        when(reader.fetchLatestUpdate(any())).thenAnswer(invocation -> {
            bothFetchesStarted.countDown();
            assertThat(bothFetchesStarted.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(releaseFetches.await(1, TimeUnit.SECONDS)).isTrue();
            return externalUpdateAt(timestamp);
        });
        when(checkpointRepository.findByUrl(any())).thenReturn(Optional.empty());

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> future = executor.submit(useCase::checkUpdates);
            assertThat(bothFetchesStarted.await(1, TimeUnit.SECONDS)).isTrue();
            releaseFetches.countDown();
            future.get(1, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        verify(checkpointRepository, times(2)).save(any(), eq(timestamp));
    }

    @Test
    void pageZeroCompletesBeforePageOneFetchWhenConcurrent() throws Exception {
        schedulerProperties.setWorkerCount(2);
        TrackedLinkSnapshot first = new TrackedLinkSnapshot(1L, "https://github.com/a/b", List.of(10L));
        TrackedLinkSnapshot second = new TrackedLinkSnapshot(2L, "https://github.com/c/d", List.of(20L));
        TrackedLinkSnapshot third = new TrackedLinkSnapshot(3L, "https://github.com/e/f", List.of(30L));
        GithubLinkSource firstSource = new GithubLinkSource("a", "b");
        GithubLinkSource secondSource = new GithubLinkSource("c", "d");
        GithubLinkSource thirdSource = new GithubLinkSource("e", "f");
        Instant timestamp = Instant.parse("2024-06-01T00:00:00Z");
        CountDownLatch firstPageStarted = new CountDownLatch(2);
        CountDownLatch releaseFirstPage = new CountDownLatch(1);
        CountDownLatch pageOneFetched = new CountDownLatch(1);

        when(linkRepository.findAllTrackedLinks(new RepositoryPageRequest(2, 0)))
                .thenReturn(List.of(first, second));
        when(linkRepository.findAllTrackedLinks(new RepositoryPageRequest(2, 2)))
                .thenAnswer(invocation -> {
                    pageOneFetched.countDown();
                    return List.of(third);
                });
        when(linkSourceResolver.resolve(first.url())).thenReturn(Optional.of(firstSource));
        when(linkSourceResolver.resolve(second.url())).thenReturn(Optional.of(secondSource));
        when(linkSourceResolver.resolve(third.url())).thenReturn(Optional.of(thirdSource));
        when(reader.fetchLatestUpdate(any())).thenAnswer(invocation -> {
            LinkSource source = invocation.getArgument(0);
            if (source.equals(firstSource) || source.equals(secondSource)) {
                firstPageStarted.countDown();
                assertThat(releaseFirstPage.await(1, TimeUnit.SECONDS)).isTrue();
            }
            return externalUpdateAt(timestamp);
        });
        when(checkpointRepository.findByUrl(any())).thenReturn(Optional.empty());

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> future = executor.submit(useCase::checkUpdates);
            assertThat(firstPageStarted.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(pageOneFetched.await(200, TimeUnit.MILLISECONDS)).isFalse();
            releaseFirstPage.countDown();
            future.get(1, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertThat(pageOneFetched.await(1, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void interruptionWhileWaitingCancelsWorkersAndPreventsNextPageFetch() throws Exception {
        schedulerProperties.setWorkerCount(2);
        TrackedLinkSnapshot first = new TrackedLinkSnapshot(1L, "https://github.com/a/b", List.of(10L));
        TrackedLinkSnapshot second = new TrackedLinkSnapshot(2L, "https://github.com/c/d", List.of(20L));
        GithubLinkSource firstSource = new GithubLinkSource("a", "b");
        GithubLinkSource secondSource = new GithubLinkSource("c", "d");
        CountDownLatch workersStarted = new CountDownLatch(2);
        CountDownLatch workersInterrupted = new CountDownLatch(2);
        CountDownLatch keepWorkersBlocked = new CountDownLatch(1);

        when(linkRepository.findAllTrackedLinks(new RepositoryPageRequest(2, 0)))
                .thenReturn(List.of(first, second));
        when(linkSourceResolver.resolve(first.url())).thenReturn(Optional.of(firstSource));
        when(linkSourceResolver.resolve(second.url())).thenReturn(Optional.of(secondSource));
        when(reader.fetchLatestUpdate(any())).thenAnswer(invocation -> {
            workersStarted.countDown();
            try {
                keepWorkersBlocked.await();
            } catch (InterruptedException exception) {
                workersInterrupted.countDown();
                throw new ExternalSourceException("interrupted", exception);
            }
            return externalUpdateAt(Instant.parse("2024-06-01T00:00:00Z"));
        });

        ExecutorService schedulerExecutor = Executors.newSingleThreadExecutor();
        try {
            Future<?> schedulerFuture = schedulerExecutor.submit(useCase::checkUpdates);
            assertThat(workersStarted.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(schedulerFuture.cancel(true)).isTrue();
            assertThat(workersInterrupted.await(1, TimeUnit.SECONDS)).isTrue();
        } finally {
            keepWorkersBlocked.countDown();
            schedulerExecutor.shutdownNow();
            schedulerExecutor.awaitTermination(1, TimeUnit.SECONDS);
        }

        verify(linkRepository, never()).findAllTrackedLinks(new RepositoryPageRequest(2, 2));
    }

    @Test
    void githubIssueDescriptionContainsUpdateTypeTitleAuthorCreationTimeAndPreview() {
        RichDescriptionExpectation expected = new RichDescriptionExpectation(
                "GitHub Issue",
                "Fix flaky scheduler retry",
                "octocat",
                Instant.parse("2024-02-10T14:15:16Z"),
                "Issue body preview with the key context");
        String description = captureDescriptionOnChangedUpdate(
                "https://github.com/acme/platform",
                new GithubLinkSource("acme", "platform"),
                externalUpdateFromExpectation(expected, ExternalUpdateType.GITHUB_ISSUE));

        assertDescriptionContains(expected, description);
    }

    @Test
    void githubPullRequestDescriptionContainsUpdateTypeTitleAuthorCreationTimeAndPreview() {
        RichDescriptionExpectation expected = new RichDescriptionExpectation(
                "GitHub Pull Request",
                "feat: ship scheduler metadata payload",
                "hubot",
                Instant.parse("2024-03-11T04:05:06Z"),
                "PR description preview with migration notes");
        String description = captureDescriptionOnChangedUpdate(
                "https://github.com/acme/platform",
                new GithubLinkSource("acme", "platform"),
                externalUpdateFromExpectation(expected, ExternalUpdateType.GITHUB_PULL_REQUEST));

        assertDescriptionContains(expected, description);
    }

    @Test
    void stackoverflowAnswerDescriptionContainsTypeTopicAuthorCreationTimeAndPreview() {
        RichDescriptionExpectation expected = new RichDescriptionExpectation(
                "StackOverflow Answer",
                "How to paginate scheduler link checks?",
                "alice",
                Instant.parse("2024-04-12T07:08:09Z"),
                "Answer preview explaining how to batch repository reads");
        String description = captureDescriptionOnChangedUpdate(
                "https://stackoverflow.com/questions/12345/scheduler-batching",
                new StackoverflowQuestionLinkSource(12345L),
                externalUpdateFromExpectation(expected, ExternalUpdateType.STACKOVERFLOW_ANSWER));

        assertDescriptionContains(expected, description);
    }

    @Test
    void stackoverflowCommentDescriptionContainsTypeTopicAuthorCreationTimeAndPreview() {
        RichDescriptionExpectation expected = new RichDescriptionExpectation(
                "StackOverflow Comment",
                "How to paginate scheduler link checks?",
                "bob",
                Instant.parse("2024-04-12T09:10:11Z"),
                "Comment preview clarifying the accepted answer");
        String description = captureDescriptionOnChangedUpdate(
                "https://stackoverflow.com/questions/12345/scheduler-batching",
                new StackoverflowQuestionLinkSource(12345L),
                externalUpdateFromExpectation(expected, ExternalUpdateType.STACKOVERFLOW_COMMENT));

        assertDescriptionContains(expected, description);
    }

    @Test
    void richDescriptionPreviewIsTruncatedToFirstTwoHundredCharacters() {
        String longPreview = "x".repeat(PREVIEW_LIMIT) + "TRUNCATE_ME_AFTER_200";
        RichDescriptionExpectation expected = new RichDescriptionExpectation(
                "GitHub Issue",
                "Preview truncation contract",
                "maintainer",
                Instant.parse("2024-05-20T01:02:03Z"),
                longPreview);
        String description = captureDescriptionOnChangedUpdate(
                "https://github.com/acme/platform",
                new GithubLinkSource("acme", "platform"),
                externalUpdateFromExpectation(expected, ExternalUpdateType.GITHUB_ISSUE));

        assertDescriptionContains(expected, description);
        assertThat(description).doesNotContain("TRUNCATE_ME_AFTER_200");
    }

    @Test
    void failedLinkDoesNotBlockSuccessfulSamePageLinkWhenConcurrent() {
        schedulerProperties.setWorkerCount(2);
        TrackedLinkSnapshot first = new TrackedLinkSnapshot(1L, "https://github.com/a/b", List.of(10L));
        TrackedLinkSnapshot second = new TrackedLinkSnapshot(2L, "https://github.com/c/d", List.of(20L));
        when(linkRepository.findAllTrackedLinks(new RepositoryPageRequest(2, 0)))
                .thenReturn(List.of(first, second));
        when(linkRepository.findAllTrackedLinks(new RepositoryPageRequest(2, 2)))
                .thenReturn(List.of());
        when(linkSourceResolver.resolve(first.url())).thenReturn(Optional.of(new GithubLinkSource("a", "b")));
        when(linkSourceResolver.resolve(second.url())).thenReturn(Optional.of(new GithubLinkSource("c", "d")));
        when(reader.fetchLatestUpdate(new GithubLinkSource("a", "b")))
                .thenThrow(new ExternalSourceException("boom", null));
        when(reader.fetchLatestUpdate(new GithubLinkSource("c", "d")))
                .thenReturn(externalUpdateAt(Instant.parse("2024-03-01T00:00:00Z")));
        when(checkpointRepository.findByUrl(second.url())).thenReturn(Optional.empty());

        useCase.checkUpdates();

        verify(checkpointRepository, times(1)).save(second.url(), Instant.parse("2024-03-01T00:00:00Z"));
    }

    @Test
    void failedLinkSendsFailureReportToTrackedChatsWithoutSavingCheckpoint() {
        schedulerProperties.setWorkerCount(2);
        TrackedLinkSnapshot failed = new TrackedLinkSnapshot(1L, "https://github.com/a/b", List.of(10L, 11L));
        TrackedLinkSnapshot successful = new TrackedLinkSnapshot(2L, "https://github.com/c/d", List.of(20L));
        Instant successfulTimestamp = Instant.parse("2024-03-01T00:00:00Z");

        when(linkRepository.findAllTrackedLinks(new RepositoryPageRequest(2, 0)))
                .thenReturn(List.of(failed, successful));
        when(linkRepository.findAllTrackedLinks(new RepositoryPageRequest(2, 2)))
                .thenReturn(List.of());
        when(linkSourceResolver.resolve(failed.url())).thenReturn(Optional.of(new GithubLinkSource("a", "b")));
        when(linkSourceResolver.resolve(successful.url())).thenReturn(Optional.of(new GithubLinkSource("c", "d")));
        when(reader.fetchLatestUpdate(new GithubLinkSource("a", "b")))
                .thenThrow(new ExternalSourceException("rate limited", null));
        when(reader.fetchLatestUpdate(new GithubLinkSource("c", "d")))
                .thenReturn(externalUpdateAt(successfulTimestamp));
        when(checkpointRepository.findByUrl(successful.url())).thenReturn(Optional.empty());
        when(notificationSender.send(any())).thenReturn(true);

        useCase.checkUpdates();

        ArgumentCaptor<LinkUpdateNotification> notificationCaptor =
                ArgumentCaptor.forClass(LinkUpdateNotification.class);
        verify(notificationSender).send(notificationCaptor.capture());
        LinkUpdateNotification failureReport = notificationCaptor.getValue();
        assertThat(failureReport.id()).isEqualTo(failed.id());
        assertThat(failureReport.url()).isEqualTo(failed.url());
        assertThat(failureReport.tgChatIds()).containsExactly(10L, 11L);
        assertThat(failureReport.description())
                .contains("Не удалось проверить ссылку")
                .contains(failed.url())
                .contains("ExternalSourceException");
        verify(checkpointRepository, never()).save(eq(failed.url()), any());
        verify(checkpointRepository).save(successful.url(), successfulTimestamp);
    }

    @Test
    void failedLinkReportSendFailureDoesNotBlockSuccessfulSamePageLink() {
        schedulerProperties.setWorkerCount(2);
        TrackedLinkSnapshot failed = new TrackedLinkSnapshot(1L, "https://github.com/a/b", List.of(10L));
        TrackedLinkSnapshot successful = new TrackedLinkSnapshot(2L, "https://github.com/c/d", List.of(20L));
        Instant successfulTimestamp = Instant.parse("2024-03-01T00:00:00Z");

        when(linkRepository.findAllTrackedLinks(new RepositoryPageRequest(2, 0)))
                .thenReturn(List.of(failed, successful));
        when(linkRepository.findAllTrackedLinks(new RepositoryPageRequest(2, 2)))
                .thenReturn(List.of());
        when(linkSourceResolver.resolve(failed.url())).thenReturn(Optional.of(new GithubLinkSource("a", "b")));
        when(linkSourceResolver.resolve(successful.url())).thenReturn(Optional.of(new GithubLinkSource("c", "d")));
        when(reader.fetchLatestUpdate(new GithubLinkSource("a", "b")))
                .thenThrow(new ExternalSourceException("rate limited", null));
        when(reader.fetchLatestUpdate(new GithubLinkSource("c", "d")))
                .thenReturn(externalUpdateAt(successfulTimestamp));
        when(checkpointRepository.findByUrl(successful.url())).thenReturn(Optional.empty());
        when(notificationSender.send(any())).thenThrow(new IllegalStateException("bot unavailable"));

        useCase.checkUpdates();

        verify(checkpointRepository).save(successful.url(), successfulTimestamp);
    }

    @Test
    void unexpectedWorkerExecutionFailureIsLoggedAndBatchContinues() {
        schedulerProperties.setWorkerCount(2);
        TrackedLinkSnapshot first = new TrackedLinkSnapshot(1L, "https://github.com/a/b", List.of(10L));
        TrackedLinkSnapshot second = new TrackedLinkSnapshot(2L, "https://github.com/c/d", List.of(20L));
        GithubLinkSource firstSource = new GithubLinkSource("a", "b");
        GithubLinkSource secondSource = new GithubLinkSource("c", "d");
        Instant secondTimestamp = Instant.parse("2024-03-01T00:00:00Z");

        when(linkRepository.findAllTrackedLinks(new RepositoryPageRequest(2, 0)))
                .thenReturn(List.of(first, second));
        when(linkRepository.findAllTrackedLinks(new RepositoryPageRequest(2, 2)))
                .thenReturn(List.of());
        when(linkSourceResolver.resolve(first.url())).thenReturn(Optional.of(firstSource));
        when(linkSourceResolver.resolve(second.url())).thenReturn(Optional.of(secondSource));
        when(reader.fetchLatestUpdate(firstSource)).thenThrow(new AssertionError("unexpected"));
        when(reader.fetchLatestUpdate(secondSource)).thenReturn(externalUpdateAt(secondTimestamp));
        when(checkpointRepository.findByUrl(second.url())).thenReturn(Optional.empty());

        useCase.checkUpdates();

        verify(checkpointRepository).save(second.url(), secondTimestamp);
        verify(scrapperLogger).logExternalFetchFailed("scheduler", "worker-pool", "AssertionError");
    }

    @Test
    void notificationFailureDoesNotSaveCheckpointUnderConcurrency() {
        schedulerProperties.setWorkerCount(2);
        TrackedLinkSnapshot first = new TrackedLinkSnapshot(1L, "https://github.com/a/b", List.of(10L, 11L));
        TrackedLinkSnapshot second = new TrackedLinkSnapshot(2L, "https://github.com/c/d", List.of(20L, 21L));
        GithubLinkSource firstSource = new GithubLinkSource("a", "b");
        GithubLinkSource secondSource = new GithubLinkSource("c", "d");
        Instant previousTimestamp = Instant.parse("2024-01-01T00:00:00Z");
        Instant firstCurrentTimestamp = Instant.parse("2024-02-01T00:00:00Z");
        Instant secondCurrentTimestamp = Instant.parse("2024-03-01T00:00:00Z");

        when(linkRepository.findAllTrackedLinks(new RepositoryPageRequest(2, 0)))
                .thenReturn(List.of(first, second));
        when(linkRepository.findAllTrackedLinks(new RepositoryPageRequest(2, 2)))
                .thenReturn(List.of());
        when(linkSourceResolver.resolve(first.url())).thenReturn(Optional.of(firstSource));
        when(linkSourceResolver.resolve(second.url())).thenReturn(Optional.of(secondSource));
        when(reader.fetchLatestUpdate(firstSource)).thenReturn(externalUpdateAt(firstCurrentTimestamp));
        when(reader.fetchLatestUpdate(secondSource)).thenReturn(externalUpdateAt(secondCurrentTimestamp));
        when(checkpointRepository.findByUrl(any())).thenReturn(Optional.of(previousTimestamp));
        when(notificationSender.send(any())).thenAnswer(invocation -> {
            LinkUpdateNotification notification = invocation.getArgument(0);
            return !notification.url().equals(first.url());
        });

        useCase.checkUpdates();

        verify(checkpointRepository, never()).save(first.url(), firstCurrentTimestamp);
        verify(checkpointRepository).save(second.url(), secondCurrentTimestamp);
    }

    @Test
    void checkUpdatesProcessesPagesUntilShortPage() {
        schedulerProperties.setWorkerCount(2);
        TrackedLinkSnapshot first = new TrackedLinkSnapshot(1L, "https://github.com/a/b", List.of(10L));
        TrackedLinkSnapshot second = new TrackedLinkSnapshot(2L, "https://github.com/c/d", List.of(20L));
        TrackedLinkSnapshot third = new TrackedLinkSnapshot(3L, "https://github.com/e/f", List.of(30L));

        when(linkRepository.findAllTrackedLinks(new RepositoryPageRequest(2, 0)))
                .thenReturn(List.of(first, second));
        when(linkRepository.findAllTrackedLinks(new RepositoryPageRequest(2, 2)))
                .thenReturn(List.of(third));
        when(linkSourceResolver.resolve(any())).thenReturn(Optional.of(new GithubLinkSource("a", "b")));
        when(reader.fetchLatestUpdate(any())).thenReturn(externalUpdateAt(Instant.parse("2024-05-01T00:00:00Z")));
        when(checkpointRepository.findByUrl(any())).thenReturn(Optional.empty());

        useCase.checkUpdates();

        verify(linkRepository).findAllTrackedLinks(new RepositoryPageRequest(2, 0));
        verify(linkRepository).findAllTrackedLinks(new RepositoryPageRequest(2, 2));
        verify(linkRepository, never()).findAllTrackedLinks(new RepositoryPageRequest(2, 4));
        verify(checkpointRepository, times(3)).save(any(), eq(Instant.parse("2024-05-01T00:00:00Z")));
    }

    @Test
    void checkUpdatesStopsWhenFirstPageIsEmpty() {
        when(linkRepository.findAllTrackedLinks(new RepositoryPageRequest(2, 0)))
                .thenReturn(List.of());

        useCase.checkUpdates();

        verify(linkRepository).findAllTrackedLinks(new RepositoryPageRequest(2, 0));
        verify(linkSourceResolver, never()).resolve(any());
    }

    private String captureDescriptionOnChangedUpdate(
            String trackedUrl, LinkSource source, ExternalUpdate latestUpdate) {
        TrackedLinkSnapshot snapshot = new TrackedLinkSnapshot(1L, trackedUrl, List.of(10L, 20L));
        Instant previousTimestamp = Instant.parse("2024-01-01T00:00:00Z");
        when(linkRepository.findAllTrackedLinks(new RepositoryPageRequest(2, 0)))
                .thenReturn(List.of(snapshot));
        when(linkSourceResolver.resolve(snapshot.url())).thenReturn(Optional.of(source));
        when(reader.fetchLatestUpdate(source)).thenReturn(Optional.of(latestUpdate));
        when(checkpointRepository.findByUrl(snapshot.url())).thenReturn(Optional.of(previousTimestamp));
        when(notificationSender.send(any())).thenReturn(true);

        useCase.checkUpdates();

        ArgumentCaptor<LinkUpdateNotification> notificationCaptor =
                ArgumentCaptor.forClass(LinkUpdateNotification.class);
        verify(notificationSender).send(notificationCaptor.capture());
        return notificationCaptor.getValue().description();
    }

    private Optional<ExternalUpdate> externalUpdateAt(Instant createdAt) {
        return Optional.of(new ExternalUpdate(ExternalUpdateType.GITHUB_ISSUE, createdAt, "title", "author", "preview"));
    }

    private ExternalUpdate externalUpdateFromExpectation(RichDescriptionExpectation expected, ExternalUpdateType type) {
        return new ExternalUpdate(
                type, expected.createdAt(), expected.titleOrTopic(), expected.author(), expected.preview());
    }

    private void assertDescriptionContains(RichDescriptionExpectation expected, String actualDescription) {
        String truncatedPreview = expected.preview()
                .substring(0, Math.min(PREVIEW_LIMIT, expected.preview().length()));
        assertThat(actualDescription)
                .contains(expected.updateType())
                .contains(expected.titleOrTopic())
                .contains(expected.author())
                .contains(expected.createdAt().toString())
                .contains(truncatedPreview);
    }

    private record RichDescriptionExpectation(
            String updateType, String titleOrTopic, String author, Instant createdAt, String preview) {}
}
