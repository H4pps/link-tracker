package backend.academy.linktracker.scrapper.application.link;

import backend.academy.linktracker.scrapper.domain.exception.AlreadyExistsException;
import backend.academy.linktracker.scrapper.domain.exception.NotFoundException;
import backend.academy.linktracker.scrapper.domain.model.TrackedSubscription;
import backend.academy.linktracker.scrapper.infrastructure.memory.ScrapperInMemoryState;
import backend.academy.linktracker.scrapper.logging.ScrapperLogger;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * In-memory implementation of link management use case.
 */
@Component
@RequiredArgsConstructor
public class InMemoryScrapperLinkUseCase implements ScrapperLinkUseCase {

    private final ScrapperInMemoryState state;
    private final ScrapperLogger scrapperLogger;

    /**
     * Returns links tracked for the provided chat.
     *
     * @param chatId telegram chat identifier
     * @return links sorted by ascending internal id
     */
    @Override
    public List<LinkView> listLinks(long chatId) {
        scrapperLogger.logUseCaseAccepted("list-links", chatId, null);
        return subscriptionsForExistingChat(chatId).values().stream()
                .sorted(Comparator.comparingLong(TrackedSubscription::id))
                .map(this::toLinkView)
                .toList();
    }

    /**
     * Adds tracked link for the provided chat.
     *
     * @param chatId telegram chat identifier
     * @param command add-link request data
     * @return created link projection
     */
    @Override
    public LinkView addLink(long chatId, AddLinkCommand command) {
        scrapperLogger.logUseCaseAccepted("add-link", chatId, command.link());
        ConcurrentMap<String, TrackedSubscription> subscriptions = subscriptionsForExistingChat(chatId);
        TrackedSubscription created =
                new TrackedSubscription(state.nextLinkId(), command.link(), command.tags(), command.filters());

        TrackedSubscription existing = subscriptions.putIfAbsent(command.link(), created);
        if (existing != null) {
            throw new AlreadyExistsException("Link already tracked for chat: " + command.link());
        }

        return toLinkView(created);
    }

    /**
     * Removes tracked link for the provided chat.
     *
     * @param chatId telegram chat identifier
     * @param command remove-link request data
     * @return removed link projection
     */
    @Override
    public LinkView removeLink(long chatId, RemoveLinkCommand command) {
        scrapperLogger.logUseCaseAccepted("remove-link", chatId, command.link());
        TrackedSubscription removed = subscriptionsForExistingChat(chatId).remove(command.link());
        if (removed == null) {
            throw new NotFoundException("Link not found for chat: " + command.link());
        }
        return toLinkView(removed);
    }

    private ConcurrentMap<String, TrackedSubscription> subscriptionsForExistingChat(long chatId) {
        if (!state.isChatRegistered(chatId)) {
            throw new NotFoundException("Chat not found: " + chatId);
        }

        ConcurrentMap<String, TrackedSubscription> subscriptions = state.subscriptionsForChat(chatId);
        if (subscriptions == null) {
            throw new NotFoundException("Chat not found: " + chatId);
        }
        return subscriptions;
    }

    private LinkView toLinkView(TrackedSubscription subscription) {
        return new LinkView(subscription.id(), subscription.url(), subscription.tags(), subscription.filters());
    }
}
