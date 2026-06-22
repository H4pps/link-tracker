package com.linktracker.scrapper.infrastructure.memory.inmemory;

import com.linktracker.scrapper.domain.model.TrackedSubscription;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Shared in-memory storage used by in-memory scrapper repositories.
 */
@Component
@ConditionalOnProperty(name = "app.database.access-type", havingValue = "MEMORY")
public class InMemoryScrapperStorage {

    private final Set<Long> registeredChats = ConcurrentHashMap.newKeySet();
    private final ConcurrentMap<Long, ConcurrentMap<String, TrackedSubscription>> subscriptionsByChat =
            new ConcurrentHashMap<>();
    private final AtomicLong linkIdSequence = new AtomicLong();

    /**
     * Registers chat and initializes its link storage.
     *
     * @param chatId telegram chat identifier
     * @return true when chat was added, false when it already existed
     */
    boolean registerChat(long chatId) {
        boolean added = registeredChats.add(chatId);
        if (added) {
            subscriptionsByChat.putIfAbsent(chatId, new ConcurrentHashMap<>());
        }
        return added;
    }

    /**
     * Deletes chat and all tracked links for that chat.
     *
     * @param chatId telegram chat identifier
     * @return true when chat existed and was removed
     */
    boolean deleteChat(long chatId) {
        boolean removed = registeredChats.remove(chatId);
        if (removed) {
            subscriptionsByChat.remove(chatId);
        }
        return removed;
    }

    /**
     * Checks whether chat is registered.
     *
     * @param chatId telegram chat identifier
     * @return true when chat is known to scrapper
     */
    boolean isChatRegistered(long chatId) {
        return registeredChats.contains(chatId);
    }

    /**
     * Returns mutable per-chat subscriptions map by URL.
     *
     * @param chatId telegram chat identifier
     * @return concurrent map for chat or null when chat has no state
     */
    ConcurrentMap<String, TrackedSubscription> subscriptionsForChat(long chatId) {
        return subscriptionsByChat.get(chatId);
    }

    /**
     * Generates next internal link identifier.
     *
     * @return positive link id
     */
    long nextLinkId() {
        return linkIdSequence.incrementAndGet();
    }

    /**
     * Returns immutable snapshot of subscriptions grouped by chat.
     *
     * @return map from chat to copied URL/subscription map
     */
    Map<Long, Map<String, TrackedSubscription>> subscriptionsByChatSnapshot() {
        return subscriptionsByChat.entrySet().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        Map.Entry::getKey, entry -> Map.copyOf(entry.getValue())));
    }
}
