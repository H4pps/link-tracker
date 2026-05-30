package backend.academy.linktracker.scrapper.infrastructure.memory.inmemory;

import backend.academy.linktracker.scrapper.application.chat.ScrapperChatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/**
 * In-memory implementation of chat repository.
 */
@Repository
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.database.access-type", havingValue = "MEMORY")
public class InMemoryScrapperChatRepository implements ScrapperChatRepository {

    private final InMemoryScrapperStorage storage;

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean register(long chatId) {
        return storage.registerChat(chatId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean delete(long chatId) {
        return storage.deleteChat(chatId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean exists(long chatId) {
        return storage.isChatRegistered(chatId);
    }
}
