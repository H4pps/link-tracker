package backend.academy.linktracker.scrapper.application.update;

import backend.academy.linktracker.scrapper.application.external.ExternalSourceException;
import backend.academy.linktracker.scrapper.application.external.ExternalSourceReader;
import backend.academy.linktracker.scrapper.application.external.LinkSource;
import backend.academy.linktracker.scrapper.application.external.LinkSourceResolver;
import backend.academy.linktracker.scrapper.application.link.ScrapperLinkRepository;
import backend.academy.linktracker.scrapper.application.pagination.RepositoryPageRequest;
import backend.academy.linktracker.scrapper.domain.model.TrackedLinkSnapshot;
import backend.academy.linktracker.scrapper.logging.ScrapperLogger;
import backend.academy.linktracker.scrapper.properties.SchedulerProperties;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Scheduler orchestration use case that detects link updates and notifies bot.
 */
@Component
@RequiredArgsConstructor
public class LinkUpdateSchedulerUseCase {

    private static final String DEFAULT_DESCRIPTION = "Обнаружены изменения";

    private final ScrapperLinkRepository linkRepository;
    private final LinkSourceResolver linkSourceResolver;
    private final List<ExternalSourceReader> readers;
    private final LinkUpdateCheckpointRepository checkpointRepository;
    private final BotNotificationSender botNotificationSender;
    private final ScrapperLogger scrapperLogger;
    private final SchedulerProperties schedulerProperties;

    /**
     * Executes a full update-check batch.
     */
    public void checkUpdates() {
        int pageSize = schedulerProperties.getLinkPageSize();
        long offset = 0;
        while (true) {
            List<TrackedLinkSnapshot> page =
                    linkRepository.findAllTrackedLinks(new RepositoryPageRequest(pageSize, offset));
            if (page.isEmpty()) {
                return;
            }

            for (TrackedLinkSnapshot trackedLink : page) {
                processTrackedLink(trackedLink);
            }
            if (page.size() < pageSize) {
                return;
            }
            offset += page.size();
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

            Instant currentTimestamp = reader.fetchLastUpdated(source);
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
                    trackedLink.id(), trackedLink.url(), DEFAULT_DESCRIPTION, trackedLink.chatIds());
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
