package backend.academy.linktracker.scrapper.infrastructure.bot.kafka;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import backend.academy.linktracker.messaging.LinkUpdateEvent;
import backend.academy.linktracker.scrapper.application.update.LinkUpdateOutboxEvent;
import backend.academy.linktracker.scrapper.application.update.LinkUpdateOutboxRepository;
import backend.academy.linktracker.scrapper.logging.ScrapperLogger;
import backend.academy.linktracker.scrapper.properties.KafkaProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

@ExtendWith(MockitoExtension.class)
class KafkaOutboxPublisherTest {

    @Mock
    private LinkUpdateOutboxRepository outboxRepository;

    @Mock
    private KafkaTemplate<String, LinkUpdateEvent> kafkaTemplate;

    @Mock
    private ScrapperLogger scrapperLogger;

    private KafkaProperties kafkaProperties;
    private KafkaOutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        kafkaProperties = new KafkaProperties();
        kafkaProperties.setLinkUpdatesTopic("link-updates");
        kafkaProperties.setOutboxBatchSize(10);
        kafkaProperties.setRetryBackoff(Duration.ofSeconds(1));
        publisher = new KafkaOutboxPublisher(outboxRepository, kafkaTemplate, kafkaProperties, scrapperLogger);
    }

    @Test
    void marksEventSentAfterKafkaAck() {
        LinkUpdateOutboxEvent pending = new LinkUpdateOutboxEvent(
                10L,
                11L,
                "https://example.com",
                "changed",
                List.of(100L),
                LinkUpdateOutboxEvent.Status.PENDING,
                0,
                null,
                Instant.now(),
                Instant.now(),
                Instant.now(),
                null);
        when(outboxRepository.findPending(any(), eq(10))).thenReturn(List.of(pending));
        when(kafkaTemplate.send(eq("link-updates"), eq("https://example.com"), any(LinkUpdateEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        publisher.publishDueEvents();

        verify(outboxRepository).markSent(10L);
        verify(outboxRepository, never()).markFailed(anyLong(), any(String.class), any());
    }

    @Test
    void marksEventFailedWhenKafkaSendFails() {
        LinkUpdateOutboxEvent pending = new LinkUpdateOutboxEvent(
                10L,
                11L,
                "https://example.com",
                "changed",
                List.of(100L),
                LinkUpdateOutboxEvent.Status.PENDING,
                0,
                null,
                Instant.now(),
                Instant.now(),
                Instant.now(),
                null);
        CompletableFuture<?> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new IllegalStateException("kafka unavailable"));

        when(outboxRepository.findPending(any(), eq(10))).thenReturn(List.of(pending));
        when(kafkaTemplate.send(eq("link-updates"), eq("https://example.com"), any(LinkUpdateEvent.class)))
                .thenReturn((CompletableFuture) failedFuture);

        publisher.publishDueEvents();

        verify(outboxRepository).markFailed(eq(10L), eq("IllegalStateException: kafka unavailable"), any());
        verify(outboxRepository, never()).markSent(anyLong());
    }
}
