package backend.academy.linktracker.scrapper.infrastructure.bot.kafka.outbox;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import backend.academy.linktracker.messaging.RawLinkUpdateEvent;
import backend.academy.linktracker.scrapper.application.update.LinkUpdateOutboxEvent;
import backend.academy.linktracker.scrapper.application.update.LinkUpdateOutboxRepository;
import backend.academy.linktracker.scrapper.logging.ScrapperLogger;
import backend.academy.linktracker.scrapper.properties.KafkaProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

@ExtendWith(MockitoExtension.class)
class KafkaOutboxPublisherTest {

    @Mock
    private LinkUpdateOutboxRepository outboxRepository;

    @Mock
    private KafkaTemplate<String, RawLinkUpdateEvent> kafkaTemplate;

    @Mock
    private ScrapperLogger scrapperLogger;

    private KafkaProperties kafkaProperties;
    private KafkaOutboxPublisher publisher;
    private LinkUpdateOutboxEventMapper mapper;

    @BeforeEach
    void setUp() {
        kafkaProperties = new KafkaProperties();
        kafkaProperties.setRawUpdatesTopic("link.raw-updates");
        kafkaProperties.setOutboxBatchSize(10);
        kafkaProperties.setRetryBackoff(Duration.ofSeconds(1));
        mapper = new LinkUpdateOutboxEventMapper();
        publisher = new KafkaOutboxPublisher(outboxRepository, kafkaTemplate, kafkaProperties, scrapperLogger, mapper);
    }

    @Test
    void marksEventSentAfterKafkaAck() {
        LinkUpdateOutboxEvent pending = new LinkUpdateOutboxEvent(
                10L,
                UUID.randomUUID(),
                11L,
                "https://example.com",
                "changed",
                "octocat",
                List.of(100L),
                LinkUpdateOutboxEvent.Status.PENDING,
                0,
                null,
                Instant.now(),
                Instant.now(),
                Instant.now(),
                null);
        when(outboxRepository.findPending(any(), eq(10))).thenReturn(List.of(pending));
        when(kafkaTemplate.send(anyRecord())).thenReturn(CompletableFuture.completedFuture(null));

        publisher.publishDueEvents();

        verify(outboxRepository).markSent(10L);
        verify(outboxRepository, never()).markFailed(anyLong(), any(String.class), any());
    }

    @Test
    void marksEventFailedWhenKafkaSendFails() {
        LinkUpdateOutboxEvent pending = new LinkUpdateOutboxEvent(
                10L,
                UUID.randomUUID(),
                11L,
                "https://example.com",
                "changed",
                "octocat",
                List.of(100L),
                LinkUpdateOutboxEvent.Status.PENDING,
                0,
                null,
                Instant.now(),
                Instant.now(),
                Instant.now(),
                null);
        CompletableFuture<SendResult<String, RawLinkUpdateEvent>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new IllegalStateException("kafka unavailable"));

        when(outboxRepository.findPending(any(), eq(10))).thenReturn(List.of(pending));
        when(kafkaTemplate.send(anyRecord())).thenReturn(failedFuture);

        publisher.publishDueEvents();

        verify(outboxRepository).markFailed(eq(10L), eq("IllegalStateException: kafka unavailable"), any());
        verify(outboxRepository, never()).markSent(anyLong());
    }

    private ProducerRecord<String, RawLinkUpdateEvent> anyRecord() {
        return any();
    }
}
