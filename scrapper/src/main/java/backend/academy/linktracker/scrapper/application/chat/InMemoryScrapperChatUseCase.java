package backend.academy.linktracker.scrapper.application.chat;

import backend.academy.linktracker.scrapper.domain.exception.AlreadyExistsException;
import backend.academy.linktracker.scrapper.domain.exception.NotFoundException;
import backend.academy.linktracker.scrapper.infrastructure.memory.ScrapperInMemoryState;
import backend.academy.linktracker.scrapper.logging.ScrapperLogger;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * In-memory implementation of chat registration use case.
 */
@Component
@RequiredArgsConstructor
public class InMemoryScrapperChatUseCase implements ScrapperChatUseCase {

    private final ScrapperInMemoryState state;
    private final ScrapperLogger scrapperLogger;

    /**
     * Registers chat for tracking operations.
     *
     * @param chatId telegram chat identifier
     */
    @Override
    public void registerChat(long chatId) {
        scrapperLogger.logUseCaseAccepted("register-chat", chatId, null);
        if (!state.registerChat(chatId)) {
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
        if (!state.deleteChat(chatId)) {
            throw new NotFoundException("Chat not found: " + chatId);
        }
    }
}
