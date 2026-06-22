package com.linktracker.scrapper.application.link;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.linktracker.scrapper.application.chat.ScrapperChatRepository;
import com.linktracker.scrapper.application.chat.ScrapperChatUseCaseImpl;
import com.linktracker.scrapper.domain.exception.AlreadyExistsException;
import com.linktracker.scrapper.domain.exception.NotFoundException;
import com.linktracker.scrapper.infrastructure.cache.NoOpListLinksCache;
import com.linktracker.scrapper.infrastructure.memory.inmemory.InMemoryScrapperChatRepository;
import com.linktracker.scrapper.infrastructure.memory.inmemory.InMemoryScrapperLinkRepository;
import com.linktracker.scrapper.infrastructure.memory.inmemory.InMemoryScrapperStorage;
import com.linktracker.scrapper.logging.ScrapperLogger;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScrapperLinkUseCaseImplTest {

    @Mock
    private ScrapperLogger scrapperLogger;

    private ScrapperLinkUseCaseImpl linkUseCase;
    private ScrapperChatUseCaseImpl chatUseCase;

    @BeforeEach
    void setUp() {
        InMemoryScrapperStorage storage = new InMemoryScrapperStorage();
        ScrapperChatRepository chatRepository = new InMemoryScrapperChatRepository(storage);
        ScrapperLinkRepository linkRepository = new InMemoryScrapperLinkRepository(storage);
        NoOpListLinksCache listLinksCache = new NoOpListLinksCache();
        linkUseCase = new ScrapperLinkUseCaseImpl(chatRepository, linkRepository, listLinksCache, scrapperLogger);
        chatUseCase = new ScrapperChatUseCaseImpl(chatRepository, listLinksCache, scrapperLogger);
    }

    @Test
    void addListAndRemoveLinkForChat() {
        chatUseCase.registerChat(1L);

        LinkView created = linkUseCase.addLink(
                1L, new AddLinkCommand("https://github.com/octocat/Hello-World", List.of("w"), List.of("f")));
        List<LinkView> listed = linkUseCase.listLinks(1L);
        LinkView removed = linkUseCase.removeLink(1L, new RemoveLinkCommand("https://github.com/octocat/Hello-World"));
        List<LinkView> empty = linkUseCase.listLinks(1L);

        assertEquals(1L, created.id());
        assertEquals("https://github.com/octocat/Hello-World", created.url());
        assertEquals(List.of("w"), created.tags());
        assertEquals(List.of("f"), created.filters());
        assertEquals(1, listed.size());
        assertEquals(created.id(), listed.getFirst().id());
        assertEquals(created.id(), removed.id());
        assertTrue(empty.isEmpty());
    }

    @Test
    void addLinkThrowsConflictForDuplicateInSameChat() {
        chatUseCase.registerChat(1L);
        linkUseCase.addLink(1L, new AddLinkCommand("https://example.com/a", List.of(), List.of()));

        assertThrows(
                AlreadyExistsException.class,
                () -> linkUseCase.addLink(1L, new AddLinkCommand("https://example.com/a", List.of("t"), List.of())));
    }

    @Test
    void listAddAndRemoveThrowNotFoundWhenChatDoesNotExist() {
        assertThrows(NotFoundException.class, () -> linkUseCase.listLinks(1L));
        assertThrows(
                NotFoundException.class,
                () -> linkUseCase.addLink(1L, new AddLinkCommand("https://example.com/a", List.of(), List.of())));
        assertThrows(
                NotFoundException.class,
                () -> linkUseCase.removeLink(1L, new RemoveLinkCommand("https://example.com/a")));
    }

    @Test
    void deleteChatCascadesSubscriptionsCleanup() {
        chatUseCase.registerChat(1L);
        linkUseCase.addLink(1L, new AddLinkCommand("https://example.com/a", List.of(), List.of()));

        chatUseCase.deleteChat(1L);

        assertThrows(NotFoundException.class, () -> linkUseCase.listLinks(1L));
    }

    @Test
    void sameUrlCanBeTrackedByDifferentChats() {
        chatUseCase.registerChat(1L);
        chatUseCase.registerChat(2L);

        LinkView first =
                linkUseCase.addLink(1L, new AddLinkCommand("https://example.com/shared", List.of(), List.of()));
        LinkView second =
                linkUseCase.addLink(2L, new AddLinkCommand("https://example.com/shared", List.of(), List.of()));

        assertEquals(1, linkUseCase.listLinks(1L).size());
        assertEquals(1, linkUseCase.listLinks(2L).size());
        assertTrue(first.id() < second.id());
    }

    @Test
    void listLinksReturnsAscendingIdOrder() {
        chatUseCase.registerChat(1L);

        LinkView first = linkUseCase.addLink(1L, new AddLinkCommand("https://example.com/2", List.of(), List.of()));
        LinkView second = linkUseCase.addLink(1L, new AddLinkCommand("https://example.com/1", List.of(), List.of()));

        List<LinkView> links = linkUseCase.listLinks(1L);

        assertEquals(
                List.of(first.id(), second.id()),
                links.stream().map(LinkView::id).toList());
    }
}
