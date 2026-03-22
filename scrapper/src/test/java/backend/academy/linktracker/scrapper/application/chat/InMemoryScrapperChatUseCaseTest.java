package backend.academy.linktracker.scrapper.application.chat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import backend.academy.linktracker.scrapper.domain.exception.AlreadyExistsException;
import backend.academy.linktracker.scrapper.domain.exception.NotFoundException;
import backend.academy.linktracker.scrapper.infrastructure.memory.ScrapperInMemoryState;
import backend.academy.linktracker.scrapper.logging.ScrapperLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InMemoryScrapperChatUseCaseTest {

    @Mock
    private ScrapperLogger scrapperLogger;

    private ScrapperInMemoryState state;
    private InMemoryScrapperChatUseCase useCase;

    @BeforeEach
    void setUp() {
        state = new ScrapperInMemoryState();
        useCase = new InMemoryScrapperChatUseCase(state, scrapperLogger);
    }

    @Test
    void registerChatStoresNewChat() {
        useCase.registerChat(10L);

        assertTrue(state.isChatRegistered(10L));
    }

    @Test
    void registerChatThrowsConflictWhenChatExists() {
        useCase.registerChat(10L);

        assertThrows(AlreadyExistsException.class, () -> useCase.registerChat(10L));
    }

    @Test
    void deleteChatRemovesRegisteredChat() {
        useCase.registerChat(10L);

        useCase.deleteChat(10L);

        assertFalse(state.isChatRegistered(10L));
    }

    @Test
    void deleteChatThrowsWhenChatDoesNotExist() {
        assertThrows(NotFoundException.class, () -> useCase.deleteChat(10L));
    }
}
