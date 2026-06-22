package com.linktracker.scrapper.infrastructure.bot.kafka.sender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.linktracker.scrapper.application.update.LinkUpdateNotification;
import com.linktracker.scrapper.application.update.LinkUpdateOutboxEvent;
import com.linktracker.scrapper.application.update.LinkUpdateOutboxRepository;
import com.linktracker.scrapper.logging.ScrapperLogger;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KafkaBotNotificationSenderTest {

    @Mock
    private LinkUpdateOutboxRepository outboxRepository;

    @Mock
    private ScrapperLogger scrapperLogger;

    private KafkaBotNotificationSender sender;

    @BeforeEach
    void setUp() {
        sender = new KafkaBotNotificationSender(outboxRepository, scrapperLogger);
    }

    @Test
    void sendStoresOutboxRecordAndReturnsTrue() {
        LinkUpdateNotification notification =
                new LinkUpdateNotification(7L, "https://example.com", "changed", List.of(100L, 200L));

        boolean accepted = sender.send(notification);

        assertThat(accepted).isTrue();
        ArgumentCaptor<LinkUpdateOutboxEvent> captor = ArgumentCaptor.forClass(LinkUpdateOutboxEvent.class);
        verify(outboxRepository).save(captor.capture());
        assertThat(captor.getValue().id()).isEqualTo(7L);
        assertThat(captor.getValue().url()).isEqualTo("https://example.com");
        assertThat(captor.getValue().description()).isEqualTo("changed");
        assertThat(captor.getValue().tgChatIds()).containsExactly(100L, 200L);
    }

    @Test
    void sendReturnsFalseWhenOutboxSaveFails() {
        doThrow(new IllegalStateException("boom")).when(outboxRepository).save(any());

        boolean accepted = sender.send(new LinkUpdateNotification(7L, "https://example.com", "changed", List.of(100L)));

        assertThat(accepted).isFalse();
        verify(scrapperLogger)
                .logExternalFetchFailed("bot-kafka-outbox", "https://example.com", "IllegalStateException");
    }
}
