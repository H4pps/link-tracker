package com.linktracker.scrapper.application.link;

import com.linktracker.scrapper.application.pagination.RepositoryPageRequest;
import com.linktracker.scrapper.domain.model.TrackedLinkSnapshot;
import com.linktracker.scrapper.domain.model.TrackedSubscription;
import java.util.List;
import java.util.Optional;

/**
 * Repository boundary for tracked links state.
 */
public interface ScrapperLinkRepository {

    /**
     * Reads all links for a chat.
     *
     * @param chatId telegram chat identifier
     * @return list of tracked subscriptions or empty list when chat has no subscriptions
     */
    default List<TrackedSubscription> findAllByChatId(long chatId) {
        return findAllByChatId(chatId, RepositoryPageRequest.all());
    }

    /**
     * Reads links for a chat with optional page bounds.
     *
     * @param chatId telegram chat identifier
     * @param pageRequest page bounds, unbounded when limit is zero
     * @return list of tracked subscriptions or empty list when chat has no subscriptions
     */
    List<TrackedSubscription> findAllByChatId(long chatId, RepositoryPageRequest pageRequest);

    /**
     * Adds link for a chat if the same URL is not present yet.
     *
     * @param chatId telegram chat identifier
     * @param url tracked URL
     * @param tags tags associated with URL
     * @param filters filters associated with URL
     * @return created tracked subscription or empty optional when URL already exists or chat is missing
     */
    Optional<TrackedSubscription> addIfAbsent(long chatId, String url, List<String> tags, List<String> filters);

    /**
     * Removes tracked link for a chat.
     *
     * @param chatId telegram chat identifier
     * @param url tracked URL
     * @return removed subscription or empty optional when link is missing or chat does not exist
     */
    Optional<TrackedSubscription> remove(long chatId, String url);

    /**
     * Reads all tracked URLs with subscribed chat identifiers.
     *
     * @return deterministic global tracked-link snapshots
     */
    default List<TrackedLinkSnapshot> findAllTrackedLinks() {
        return findAllTrackedLinks(RepositoryPageRequest.all());
    }

    /**
     * Reads tracked URLs with subscribed chat identifiers with optional page bounds.
     *
     * @param pageRequest page bounds, unbounded when limit is zero
     * @return deterministic global tracked-link snapshots
     */
    List<TrackedLinkSnapshot> findAllTrackedLinks(RepositoryPageRequest pageRequest);
}
