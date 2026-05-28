package backend.academy.linktracker.scrapper.infrastructure.memory.inmemory;

import backend.academy.linktracker.scrapper.application.repository.ScrapperLinkRepository;
import backend.academy.linktracker.scrapper.domain.model.TrackedLinkSnapshot;
import backend.academy.linktracker.scrapper.domain.model.TrackedSubscription;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentMap;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/**
 * In-memory implementation of link repository.
 */
@Repository
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.database.access-type", havingValue = "MEMORY")
public class InMemoryScrapperLinkRepository implements ScrapperLinkRepository {

    private final InMemoryScrapperStorage storage;

    /**
     * {@inheritDoc}
     */
    @Override
    public List<TrackedSubscription> findAllByChatId(long chatId) {
        ConcurrentMap<String, TrackedSubscription> subscriptions = storage.subscriptionsForChat(chatId);
        if (subscriptions == null) {
            return List.of();
        }
        return subscriptions.values().stream().toList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<TrackedSubscription> addIfAbsent(long chatId, String url, List<String> tags, List<String> filters) {
        ConcurrentMap<String, TrackedSubscription> subscriptions = storage.subscriptionsForChat(chatId);
        if (subscriptions == null) {
            return Optional.empty();
        }

        TrackedSubscription created = new TrackedSubscription(storage.nextLinkId(), url, tags, filters);
        TrackedSubscription existing = subscriptions.putIfAbsent(url, created);
        if (existing != null) {
            return Optional.empty();
        }

        return Optional.of(created);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<TrackedSubscription> remove(long chatId, String url) {
        ConcurrentMap<String, TrackedSubscription> subscriptions = storage.subscriptionsForChat(chatId);
        if (subscriptions == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(subscriptions.remove(url));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<TrackedLinkSnapshot> findAllTrackedLinks() {
        Map<String, AggregatedByUrl> aggregatedByUrl = new HashMap<>();
        Map<Long, Map<String, TrackedSubscription>> byChat = storage.subscriptionsByChatSnapshot();
        for (Map.Entry<Long, Map<String, TrackedSubscription>> chatEntry : byChat.entrySet()) {
            long chatId = chatEntry.getKey();
            for (TrackedSubscription subscription : chatEntry.getValue().values()) {
                AggregatedByUrl aggregated = aggregatedByUrl.computeIfAbsent(
                        subscription.url(), key -> new AggregatedByUrl(subscription.id(), new TreeSet<>()));
                aggregated.representativeId = Math.min(aggregated.representativeId, subscription.id());
                aggregated.chatIds.add(chatId);
            }
        }

        List<TrackedLinkSnapshot> snapshots = new ArrayList<>(aggregatedByUrl.size());
        for (Map.Entry<String, AggregatedByUrl> entry : aggregatedByUrl.entrySet()) {
            snapshots.add(new TrackedLinkSnapshot(
                    entry.getValue().representativeId,
                    entry.getKey(),
                    entry.getValue().chatIds.stream().toList()));
        }
        snapshots.sort(Comparator.comparingLong(TrackedLinkSnapshot::id));
        return snapshots;
    }

    private static final class AggregatedByUrl {
        private long representativeId;
        private final TreeSet<Long> chatIds;

        private AggregatedByUrl(long representativeId, TreeSet<Long> chatIds) {
            this.representativeId = representativeId;
            this.chatIds = chatIds;
        }
    }
}
