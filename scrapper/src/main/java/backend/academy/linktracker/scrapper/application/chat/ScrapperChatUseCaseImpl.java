package backend.academy.linktracker.scrapper.application.chat;

import backend.academy.linktracker.scrapper.application.link.ListLinksCache;
import backend.academy.linktracker.scrapper.domain.exception.AlreadyExistsException;
import backend.academy.linktracker.scrapper.domain.exception.NotFoundException;
import backend.academy.linktracker.scrapper.logging.ScrapperLogger;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Repository-backed implementation of chat registration use case.
 */
@Component
@RequiredArgsConstructor
public class ScrapperChatUseCaseImpl implements ScrapperChatUseCase {

    private final ScrapperChatRepository chatRepository;
    private final ListLinksCache listLinksCache;
    private final ScrapperLogger scrapperLogger;

    /**
     * Registers chat for tracking operations.
     *
     * @param chatId telegram chat identifier
     */
    @Override
    public void registerChat(long chatId) {
        scrapperLogger.logUseCaseAccepted("register-chat", chatId, null);
        if (!chatRepository.register(chatId)) {
            throw new AlreadyExistsException("Chat already registered: " + chatId);
        }
    }

    /**
     * Deletes chat from tracking operations together with all its links.
     *
     * @param chatId telegram chat identifier
     */
    @Override
    public void deleteChat(long chatId) {
        scrapperLogger.logUseCaseAccepted("delete-chat", chatId, null);
        if (!chatRepository.delete(chatId)) {
            throw new NotFoundException("Chat not found: " + chatId);
        }
        try {
            listLinksCache.evict(chatId);
        } catch (RuntimeException exception) {
            scrapperLogger.logCacheEvictFailed(chatId, exception);
        }
    }
}
