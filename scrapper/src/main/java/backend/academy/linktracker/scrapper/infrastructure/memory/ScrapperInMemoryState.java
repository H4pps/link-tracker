package backend.academy.linktracker.scrapper.infrastructure.memory;

import backend.academy.linktracker.scrapper.domain.model.TrackedSubscription;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/**
 * Shared in-memory state for scrapper use cases.
 */
@Component
public class ScrapperInMemoryState {

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
    public boolean registerChat(long chatId) {
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
    public boolean deleteChat(long chatId) {
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
    public boolean isChatRegistered(long chatId) {
        return registeredChats.contains(chatId);
    }

    /**
     * Returns mutable per-chat subscriptions map by URL.
     *
     * @param chatId telegram chat identifier
     * @return concurrent map for chat or null when chat has no state
     */
    public ConcurrentMap<String, TrackedSubscription> subscriptionsForChat(long chatId) {
        return subscriptionsByChat.get(chatId);
    }

    /**
     * Generates next internal link identifier.
     *
     * @return positive link id
     */
    public long nextLinkId() {
        return linkIdSequence.incrementAndGet();
    }
}
