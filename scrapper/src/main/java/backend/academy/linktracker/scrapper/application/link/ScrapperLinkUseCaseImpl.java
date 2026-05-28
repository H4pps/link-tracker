package backend.academy.linktracker.scrapper.application.link;

import backend.academy.linktracker.scrapper.application.repository.RepositoryPageRequest;
import backend.academy.linktracker.scrapper.application.repository.ScrapperChatRepository;
import backend.academy.linktracker.scrapper.application.repository.ScrapperLinkRepository;
import backend.academy.linktracker.scrapper.domain.exception.AlreadyExistsException;
import backend.academy.linktracker.scrapper.domain.exception.NotFoundException;
import backend.academy.linktracker.scrapper.domain.model.TrackedSubscription;
import backend.academy.linktracker.scrapper.logging.ScrapperLogger;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Repository-backed implementation of link management use case.
 */
@Component
@RequiredArgsConstructor
public class ScrapperLinkUseCaseImpl implements ScrapperLinkUseCase {

    private final ScrapperChatRepository chatRepository;
    private final ScrapperLinkRepository linkRepository;
    private final ScrapperLogger scrapperLogger;

    /**
     * Returns links tracked for the provided chat.
     *
     * @param chatId telegram chat identifier
     * @return links sorted by ascending internal id
     */
    @Override
    public List<LinkView> listLinks(long chatId) {
        return listLinks(chatId, RepositoryPageRequest.all());
    }

    /**
     * Returns links tracked for the provided chat with optional page bounds.
     *
     * @param chatId telegram chat identifier
     * @param pageRequest page bounds, unbounded when limit is zero
     * @return links sorted by ascending internal id
     */
    @Override
    public List<LinkView> listLinks(long chatId, RepositoryPageRequest pageRequest) {
        scrapperLogger.logUseCaseAccepted("list-links", chatId, null);
        requireChatExists(chatId);
        return linkRepository.findAllByChatId(chatId, pageRequest).stream()
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
        requireChatExists(chatId);
        TrackedSubscription created = linkRepository
                .addIfAbsent(chatId, command.link(), command.tags(), command.filters())
                .orElse(null);
        if (created == null) {
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
        requireChatExists(chatId);
        TrackedSubscription removed =
                linkRepository.remove(chatId, command.link()).orElse(null);
        if (removed == null) {
            throw new NotFoundException("Link not found for chat: " + command.link());
        }
        return toLinkView(removed);
    }

    private void requireChatExists(long chatId) {
        if (!chatRepository.exists(chatId)) {
            throw new NotFoundException("Chat not found: " + chatId);
        }
    }

    private LinkView toLinkView(TrackedSubscription subscription) {
        return new LinkView(subscription.id(), subscription.url(), subscription.tags(), subscription.filters());
    }
}
