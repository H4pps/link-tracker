package backend.academy.linktracker.scrapper.infrastructure.memory;

import backend.academy.linktracker.scrapper.application.repository.ScrapperLinkRepository;
import backend.academy.linktracker.scrapper.domain.model.TrackedSubscription;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * In-memory implementation of link repository.
 */
@Repository
@RequiredArgsConstructor
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
}
