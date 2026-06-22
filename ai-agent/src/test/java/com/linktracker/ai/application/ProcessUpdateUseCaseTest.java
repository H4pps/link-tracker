package com.linktracker.ai.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.linktracker.ai.application.filter.FilterDecision;
import com.linktracker.ai.application.filter.UpdateFilter;
import com.linktracker.ai.application.summarization.Summarizer;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProcessUpdateUseCaseTest {

    @Mock
    private UpdateFilter filter;

    @Mock
    private Summarizer summarizer;

    @Mock
    private ProcessedUpdatePublisher publisher;

    private ProcessUpdateUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ProcessUpdateUseCase(filter, summarizer, publisher);
    }

    private LinkUpdate update() {
        return new LinkUpdate(42L, "https://example.com", "a long enough description", "alice", List.of(10L, 20L));
    }

    @Test
    void filteredOutUpdateIsNotSummarizedOrPublished() {
        when(filter.evaluate(any())).thenReturn(FilterDecision.reject("stop-word: spam"));

        useCase.process(update(), "msg-1");

        verifyNoInteractions(summarizer);
        verify(publisher, never()).publish(any(), any());
    }

    @Test
    void allowedUpdateIsSummarizedAndPublishedWithMessageId() {
        when(filter.evaluate(any())).thenReturn(FilterDecision.allow());
        when(summarizer.summarize("a long enough description")).thenReturn("summary");

        useCase.process(update(), "msg-1");

        ArgumentCaptor<ProcessedUpdate> captor = ArgumentCaptor.forClass(ProcessedUpdate.class);
        verify(publisher).publish(captor.capture(), eq("msg-1"));
        ProcessedUpdate published = captor.getValue();
        assertThat(published.id()).isEqualTo(42L);
        assertThat(published.url()).isEqualTo("https://example.com");
        assertThat(published.description()).isEqualTo("summary");
        assertThat(published.tgChatIds()).containsExactly(10L, 20L);
        assertThat(published.priority()).isEqualTo("NORMAL");
    }
}
