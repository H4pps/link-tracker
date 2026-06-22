package com.linktracker.scrapper.application.chat;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.linktracker.scrapper.application.link.ListLinksCache;
import com.linktracker.scrapper.domain.exception.NotFoundException;
import com.linktracker.scrapper.logging.ScrapperLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScrapperChatUseCaseCacheTest {

    @Mock
    private ScrapperChatRepository chatRepository;

    @Mock
    private ListLinksCache listLinksCache;

    @Mock
    private ScrapperLogger scrapperLogger;

    private ScrapperChatUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        useCase = new ScrapperChatUseCaseImpl(chatRepository, listLinksCache, scrapperLogger);
    }

    @Test
    void successfulDeleteEvictsChatCache() {
        when(chatRepository.delete(1L)).thenReturn(true);

        useCase.deleteChat(1L);

        verify(listLinksCache).evict(1L);
    }

    @Test
    void successfulDeleteCompletesWhenEvictFails() {
        when(chatRepository.delete(1L)).thenReturn(true);
        doThrow(new IllegalStateException("valkey unavailable"))
                .when(listLinksCache)
                .evict(1L);

        useCase.deleteChat(1L);

        verify(listLinksCache).evict(1L);
    }

    @Test
    void failedDeleteDoesNotEvictChatCache() {
        when(chatRepository.delete(1L)).thenReturn(false);

        assertThatThrownBy(() -> useCase.deleteChat(1L)).isInstanceOf(NotFoundException.class);

        verify(listLinksCache, never()).evict(1L);
    }
}
