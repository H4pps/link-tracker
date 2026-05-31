package backend.academy.linktracker.scrapper.application.update;

import backend.academy.linktracker.scrapper.application.external.ExternalSourceException;
import backend.academy.linktracker.scrapper.application.external.ExternalSourceReader;
import backend.academy.linktracker.scrapper.application.external.LinkSourceResolver;
import backend.academy.linktracker.scrapper.application.external.link.LinkSource;
import backend.academy.linktracker.scrapper.application.external.update.ExternalUpdate;
import backend.academy.linktracker.scrapper.application.link.ScrapperLinkRepository;
import backend.academy.linktracker.scrapper.application.pagination.RepositoryPageRequest;
import backend.academy.linktracker.scrapper.domain.model.TrackedLinkSnapshot;
import backend.academy.linktracker.scrapper.logging.ScrapperLogger;
import backend.academy.linktracker.scrapper.properties.SchedulerProperties;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Scheduler orchestration use case that detects link updates and notifies bot.
 */
@Component
@RequiredArgsConstructor
public class LinkUpdateSchedulerUseCase {

    private final ScrapperLinkRepository linkRepository;
    private final LinkSourceResolver linkSourceResolver;
    private final List<ExternalSourceReader> readers;
    private final LinkUpdateCheckpointRepository checkpointRepository;
    private final BotNotificationSender botNotificationSender;
    private final ScrapperLogger scrapperLogger;
    private final SchedulerProperties schedulerProperties;
    private final ExternalUpdateDescriptionFormatter updateDescriptionFormatter =
            new ExternalUpdateDescriptionFormatter();

    /**
     * Executes a full update-check batch.
     */
    public void checkUpdates() {
        int pageSize = schedulerProperties.getLinkPageSize();
        ExecutorService workerPool = Executors.newFixedThreadPool(workerCount());
        long offset = 0;
        try {
            while (true) {
                List<TrackedLinkSnapshot> page =
                        linkRepository.findAllTrackedLinks(new RepositoryPageRequest(pageSize, offset));
                if (page.isEmpty()) {
                    return;
                }

                processPage(page, workerPool);
                if (page.size() < pageSize) {
                    return;
                }
                offset += page.size();
            }
        } finally {
            if (Thread.currentThread().isInterrupted()) {
                workerPool.shutdownNow();
            } else {
                workerPool.shutdown();
            }
        }
    }

    private int workerCount() {
        return Math.max(1, schedulerProperties.getWorkerCount()); // guardrail for a bad config
    }

    private void processPage(List<TrackedLinkSnapshot> page, ExecutorService workerPool) {
        List<Future<?>> futures = new ArrayList<>(page.size());
        for (TrackedLinkSnapshot trackedLink : page) {
            futures.add(workerPool.submit(() -> processTrackedLink(trackedLink)));
        }
        waitForPageCompletion(futures);
    }

    private void waitForPageCompletion(List<Future<?>> futures) {
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                cancelOutstandingFutures(futures);
                throw new IllegalStateException("Scheduler page processing interrupted", exception);
            } catch (ExecutionException exception) {
                Throwable cause = exception.getCause();
                String errorCode = cause == null
                        ? exception.getClass().getSimpleName()
                        : cause.getClass().getSimpleName();
                scrapperLogger.logExternalFetchFailed("scheduler", "worker-pool", errorCode);
            }
        }
    }

    private void cancelOutstandingFutures(List<Future<?>> futures) {
        for (Future<?> future : futures) {
            if (!future.isDone()) {
                future.cancel(true);
            }
        }
    }

    private void processTrackedLink(TrackedLinkSnapshot trackedLink) {
        try {
            LinkSource source = linkSourceResolver.resolve(trackedLink.url()).orElse(null);
            if (source == null) {
                scrapperLogger.logExternalFetchFailed("unsupported", trackedLink.url(), "UNSUPPORTED_URL");
                return;
            }

            ExternalSourceReader reader = resolveReader(source);
            if (reader == null) {
                scrapperLogger.logExternalFetchFailed(sourceName(source), trackedLink.url(), "NO_READER");
                return;
            }

            ExternalUpdate latestUpdate = reader.fetchLatestUpdate(source);
            Instant currentTimestamp = latestUpdate.createdAt();
            Instant previousTimestamp =
                    checkpointRepository.findByUrl(trackedLink.url()).orElse(null);
            if (previousTimestamp == null) {
                checkpointRepository.save(trackedLink.url(), currentTimestamp);
                scrapperLogger.logSchedulerProcessed(trackedLink.url(), false);
                return;
            }

            boolean changed = !currentTimestamp.equals(previousTimestamp);
            scrapperLogger.logSchedulerProcessed(trackedLink.url(), changed);
            if (!changed) {
                return;
            }

            LinkUpdateNotification notification = new LinkUpdateNotification(
                    trackedLink.id(),
                    trackedLink.url(),
                    updateDescriptionFormatter.format(latestUpdate),
                    trackedLink.chatIds());
            scrapperLogger.logSchedulerNotifyAttempt(
                    trackedLink.url(), trackedLink.chatIds().size());
            boolean sent = botNotificationSender.send(notification);
            scrapperLogger.logSchedulerNotifyResult(trackedLink.url(), sent);
            if (sent) {
                checkpointRepository.save(trackedLink.url(), currentTimestamp);
            }
        } catch (ExternalSourceException exception) {
            scrapperLogger.logExternalFetchFailed(
                    "external", trackedLink.url(), exception.getClass().getSimpleName());
        } catch (RuntimeException exception) {
            scrapperLogger.logExternalFetchFailed(
                    "scheduler", trackedLink.url(), exception.getClass().getSimpleName());
        }
    }

    private ExternalSourceReader resolveReader(LinkSource source) {
        return readers.stream()
                .filter(reader -> reader.supports(source))
                .findFirst()
                .orElse(null);
    }

    private String sourceName(LinkSource source) {
        return source.getClass().getSimpleName().toLowerCase(Locale.ROOT);
    }
}
