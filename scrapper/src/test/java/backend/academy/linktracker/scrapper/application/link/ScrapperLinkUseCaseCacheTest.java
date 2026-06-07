package backend.academy.linktracker.scrapper.application.link;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import backend.academy.linktracker.scrapper.application.chat.ScrapperChatRepository;
import backend.academy.linktracker.scrapper.application.pagination.RepositoryPageRequest;
import backend.academy.linktracker.scrapper.domain.exception.AlreadyExistsException;
import backend.academy.linktracker.scrapper.domain.exception.NotFoundException;
import backend.academy.linktracker.scrapper.domain.model.TrackedSubscription;
import backend.academy.linktracker.scrapper.logging.ScrapperLogger;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScrapperLinkUseCaseCacheTest {

    @Mock
    private ScrapperChatRepository chatRepository;

    @Mock
    private ScrapperLinkRepository linkRepository;

    @Mock
    private ListLinksCache listLinksCache;

    @Mock
    private ScrapperLogger scrapperLogger;

    private ScrapperLinkUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        useCase = new ScrapperLinkUseCaseImpl(chatRepository, linkRepository, listLinksCache, scrapperLogger);
    }

    @Test
    void unpagedListCacheMissLoadsRepositoryAndStoresSortedLinks() {
        when(chatRepository.exists(1L)).thenReturn(true);
        when(listLinksCache.get(1L)).thenReturn(Optional.empty());
        when(linkRepository.findAllByChatId(1L, RepositoryPageRequest.all()))
                .thenReturn(List.of(
                        new TrackedSubscription(2L, "https://example.com/b", List.of("b"), List.of("f2")),
                        new TrackedSubscription(1L, "https://example.com/a", List.of("a"), List.of("f1"))));

        List<LinkView> links = useCase.listLinks(1L);

        assertThat(links)
                .containsExactly(
                        new LinkView(1L, "https://example.com/a", List.of("a"), List.of("f1")),
                        new LinkView(2L, "https://example.com/b", List.of("b"), List.of("f2")));
        verify(listLinksCache).put(1L, links);
    }

    @Test
    void unpagedListCacheHitReturnsCachedLinksWithoutRepositoryRead() {
        List<LinkView> cached = List.of(new LinkView(10L, "https://example.com/cached", List.of("x"), List.of()));
        when(chatRepository.exists(1L)).thenReturn(true);
        when(listLinksCache.get(1L)).thenReturn(Optional.of(cached));

        List<LinkView> links = useCase.listLinks(1L);

        assertThat(links).containsExactlyElementsOf(cached);
        verifyNoInteractions(linkRepository);
    }

    @Test
    void paginatedListBypassesCache() {
        RepositoryPageRequest pageRequest = new RepositoryPageRequest(1, 1);
        when(chatRepository.exists(1L)).thenReturn(true);
        when(linkRepository.findAllByChatId(1L, pageRequest))
                .thenReturn(List.of(new TrackedSubscription(2L, "https://example.com/page", List.of(), List.of())));

        List<LinkView> links = useCase.listLinks(1L, pageRequest);

        assertThat(links).containsExactly(new LinkView(2L, "https://example.com/page", List.of(), List.of()));
        verifyNoInteractions(listLinksCache);
    }

    @Test
    void cacheReadAndWriteFailuresFallBackToRepositoryResult() {
        when(chatRepository.exists(1L)).thenReturn(true);
        when(listLinksCache.get(1L)).thenThrow(new IllegalStateException("valkey unavailable"));
        doThrow(new IllegalStateException("valkey unavailable"))
                .when(listLinksCache)
                .put(anyLong(), any());
        when(linkRepository.findAllByChatId(1L, RepositoryPageRequest.all()))
                .thenReturn(List.of(new TrackedSubscription(1L, "https://example.com/a", List.of(), List.of())));

        List<LinkView> links = useCase.listLinks(1L);

        assertThat(links).containsExactly(new LinkView(1L, "https://example.com/a", List.of(), List.of()));
    }

    @Test
    void successfulAddAndRemoveEvictChatCache() {
        when(chatRepository.exists(1L)).thenReturn(true);
        when(linkRepository.addIfAbsent(1L, "https://example.com/a", List.of("t"), List.of("f")))
                .thenReturn(
                        Optional.of(new TrackedSubscription(1L, "https://example.com/a", List.of("t"), List.of("f"))));
        when(linkRepository.remove(1L, "https://example.com/a"))
                .thenReturn(
                        Optional.of(new TrackedSubscription(1L, "https://example.com/a", List.of("t"), List.of("f"))));

        LinkView added = useCase.addLink(1L, new AddLinkCommand("https://example.com/a", List.of("t"), List.of("f")));
        LinkView removed = useCase.removeLink(1L, new RemoveLinkCommand("https://example.com/a"));

        assertThat(added.id()).isEqualTo(1L);
        assertThat(removed.id()).isEqualTo(1L);
        verify(listLinksCache, times(2)).evict(1L);
    }

    @Test
    void successfulAddReturnsResultWhenEvictFails() {
        when(chatRepository.exists(1L)).thenReturn(true);
        when(linkRepository.addIfAbsent(1L, "https://example.com/a", List.of(), List.of()))
                .thenReturn(Optional.of(new TrackedSubscription(1L, "https://example.com/a", List.of(), List.of())));
        doThrow(new IllegalStateException("valkey unavailable"))
                .when(listLinksCache)
                .evict(1L);

        LinkView added = useCase.addLink(1L, new AddLinkCommand("https://example.com/a", List.of(), List.of()));

        assertThat(added).isEqualTo(new LinkView(1L, "https://example.com/a", List.of(), List.of()));
    }

    @Test
    void failedAddAndRemoveDoNotEvictCache() {
        when(chatRepository.exists(1L)).thenReturn(true);
        when(linkRepository.addIfAbsent(1L, "https://example.com/a", List.of(), List.of()))
                .thenReturn(Optional.empty());
        when(linkRepository.remove(1L, "https://example.com/missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.addLink(1L, new AddLinkCommand("https://example.com/a", List.of(), List.of())))
                .isInstanceOf(AlreadyExistsException.class);
        assertThatThrownBy(() -> useCase.removeLink(1L, new RemoveLinkCommand("https://example.com/missing")))
                .isInstanceOf(NotFoundException.class);

        verify(listLinksCache, never()).evict(1L);
    }

    @Test
    void missingChatDoesNotReadOrWriteCache() {
        when(chatRepository.exists(1L)).thenReturn(false);

        assertThatThrownBy(() -> useCase.listLinks(1L)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> useCase.addLink(1L, new AddLinkCommand("https://example.com/a", List.of(), List.of())))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> useCase.removeLink(1L, new RemoveLinkCommand("https://example.com/a")))
                .isInstanceOf(NotFoundException.class);

        verifyNoInteractions(listLinksCache);
        verifyNoInteractions(linkRepository);
    }
}
