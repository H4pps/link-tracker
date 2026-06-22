package com.linktracker.scrapper.infrastructure.bot.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.linktracker.scrapper.application.update.LinkUpdateNotification;
import com.linktracker.scrapper.application.update.LinkUpdateOutboxEvent;
import com.linktracker.scrapper.application.update.LinkUpdateOutboxRepository;
import com.linktracker.scrapper.infrastructure.bot.kafka.sender.KafkaBotNotificationSender;
import com.linktracker.scrapper.logging.ScrapperLogger;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GrpcFallbackBotNotificationSenderTest {

    @Mock
    private GrpcBotNotificationSender grpcSender;

    @Mock
    private LinkUpdateOutboxRepository outboxRepository;

    @Mock
    private ScrapperLogger scrapperLogger;

    @Test
    void failedGrpcNotificationWritesKafkaOutboxFallback() {
        LinkUpdateNotification notification =
                new LinkUpdateNotification(7L, "https://github.com/a/b", "changed", List.of(10L));
        when(grpcSender.send(notification)).thenReturn(false);
        GrpcFallbackBotNotificationSender sender = createSender();

        boolean accepted = sender.send(notification);

        assertThat(accepted).isTrue();
        ArgumentCaptor<LinkUpdateOutboxEvent> captor = ArgumentCaptor.forClass(LinkUpdateOutboxEvent.class);
        verify(outboxRepository).save(captor.capture());
        assertThat(captor.getValue().id()).isEqualTo(7L);
        assertThat(captor.getValue().url()).isEqualTo("https://github.com/a/b");
        assertThat(captor.getValue().description()).isEqualTo("changed");
        assertThat(captor.getValue().tgChatIds()).containsExactly(10L);
    }

    @Test
    void successfulGrpcNotificationDoesNotWriteFallbackOutboxRecord() {
        LinkUpdateNotification notification =
                new LinkUpdateNotification(7L, "https://github.com/a/b", "changed", List.of(10L));
        when(grpcSender.send(notification)).thenReturn(true);
        GrpcFallbackBotNotificationSender sender = createSender();

        boolean accepted = sender.send(notification);

        assertThat(accepted).isTrue();
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void thrownGrpcNotificationFailureWritesKafkaOutboxFallback() {
        LinkUpdateNotification notification =
                new LinkUpdateNotification(7L, "https://github.com/a/b", "changed", List.of(10L));
        doThrow(new IllegalStateException("boom")).when(grpcSender).send(notification);
        GrpcFallbackBotNotificationSender sender = createSender();

        boolean accepted = sender.send(notification);

        assertThat(accepted).isTrue();
        verify(outboxRepository).save(any());
    }

    private GrpcFallbackBotNotificationSender createSender() {
        return new GrpcFallbackBotNotificationSender(
                grpcSender, new KafkaBotNotificationSender(outboxRepository, scrapperLogger));
    }
}
