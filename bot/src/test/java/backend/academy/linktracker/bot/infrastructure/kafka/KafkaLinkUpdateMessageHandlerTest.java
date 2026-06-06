package backend.academy.linktracker.bot.infrastructure.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import backend.academy.linktracker.bot.application.update.BotUpdateUseCase;
import backend.academy.linktracker.bot.application.update.LinkUpdateCommand;
import backend.academy.linktracker.bot.application.update.ProcessedUpdateRepository;
import backend.academy.linktracker.bot.properties.KafkaProperties;
import backend.academy.linktracker.messaging.LinkUpdateEvent;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

@ExtendWith(MockitoExtension.class)
class KafkaLinkUpdateMessageHandlerTest {

    @Mock
    private BotUpdateUseCase botUpdateUseCase;

    @Mock
    private KafkaAvroDeserializer kafkaAvroDeserializer;

    @Mock
    private KafkaTemplate<String, LinkUpdateEvent> kafkaTemplate;

    @Mock
    private KafkaTemplate<String, byte[]> kafkaBytesTemplate;

    @Mock
    private ProcessedUpdateRepository processedUpdateRepository;

    @Mock
    private RetryDelayService retryDelayService;

    private KafkaLinkUpdateMessageHandler messageHandler;

    @BeforeEach
    void setUp() {
        KafkaProperties kafkaProperties = new KafkaProperties();
        kafkaProperties.setLinkUpdatesTopic("link-updates");
        kafkaProperties.setLinkUpdatesDlqTopic("link-updates-dlq");
        kafkaProperties.setMaxAttempts(3);
        kafkaProperties.setRetryBackoff(Duration.ofSeconds(1));
        lenient()
                .when(retryDelayService.delay(kafkaProperties.getRetryBackoff()))
                .thenReturn(true);

        KafkaLinkUpdateDlqPublisher dlqPublisher =
                new KafkaLinkUpdateDlqPublisher(kafkaProperties, kafkaTemplate, kafkaBytesTemplate);
        LinkUpdateEventProcessingService processingService = new LinkUpdateEventProcessingService(
                botUpdateUseCase,
                processedUpdateRepository,
                new LinkUpdateEventValidator(),
                new LinkUpdateEventMapper(),
                new KafkaLinkUpdateRetryService(kafkaProperties, retryDelayService),
                dlqPublisher);
        messageHandler = new KafkaLinkUpdateMessageHandler(
                kafkaProperties, kafkaAvroDeserializer, processingService, dlqPublisher);
    }

    @Test
    void handleMapsValidEventToUseCaseCommand() {
        byte[] payload = new byte[] {1, 2, 3};
        when(kafkaAvroDeserializer.deserialize("link-updates", payload))
                .thenReturn(event(1L, "https://example.com", "changed", List.of(10L, 20L)));

        messageHandler.handle(payload, "key-1", null);

        ArgumentCaptor<LinkUpdateCommand> captor = ArgumentCaptor.forClass(LinkUpdateCommand.class);
        verify(botUpdateUseCase).processLinkUpdate(captor.capture());
        assertThat(captor.getValue().id()).isEqualTo(1L);
        assertThat(captor.getValue().url()).isEqualTo("https://example.com");
        assertThat(captor.getValue().description()).isEqualTo("changed");
        assertThat(captor.getValue().tgChatIds()).containsExactly(10L, 20L);
    }

    @Test
    void handleSendsValidationFailuresToDlqWithoutRetries() {
        byte[] payload = new byte[] {1, 2, 3};
        when(kafkaAvroDeserializer.deserialize("link-updates", payload))
                .thenReturn(event(0L, "https://example.com", "changed", List.of(10L)));

        messageHandler.handle(payload, "key-2", null);

        verify(botUpdateUseCase, never()).processLinkUpdate(any());
        verify(kafkaTemplate).send(anyEventRecord());
        verify(retryDelayService, never()).delay(any());
    }

    @Test
    void handleSendsDeserializationFailuresToDlqWithoutRetries() {
        byte[] payload = new byte[] {7, 8, 9};
        when(kafkaAvroDeserializer.deserialize("link-updates", payload))
                .thenThrow(new IllegalStateException("broken avro payload"));

        messageHandler.handle(payload, "key-3", null);

        verify(botUpdateUseCase, never()).processLinkUpdate(any());
        ArgumentCaptor<ProducerRecord<String, byte[]>> recordCaptor = bytesRecordCaptor();
        verify(kafkaBytesTemplate).send(recordCaptor.capture());
        assertThat(recordCaptor.getValue().topic()).isEqualTo("link-updates-dlq");
        assertThat(recordCaptor.getValue().value()).isEqualTo(payload);
        assertThat(readHeader(recordCaptor.getValue(), "error-type")).isEqualTo("deserialization");
        verify(retryDelayService, never()).delay(any());
    }

    @Test
    void handleRetriesProcessingFailuresAndPublishesToDlqWhenExhausted() {
        byte[] payload = new byte[] {4, 5, 6};
        when(kafkaAvroDeserializer.deserialize("link-updates", payload))
                .thenReturn(event(1L, "https://example.com", "changed", List.of(10L)));
        doThrow(new IllegalStateException("telegram unavailable"))
                .when(botUpdateUseCase)
                .processLinkUpdate(any());

        messageHandler.handle(payload, "key-4", null);

        verify(botUpdateUseCase, times(3)).processLinkUpdate(any());
        verify(retryDelayService, times(2)).delay(Duration.ofSeconds(1));
        ArgumentCaptor<ProducerRecord<String, LinkUpdateEvent>> recordCaptor = eventRecordCaptor();
        verify(kafkaTemplate).send(recordCaptor.capture());
        assertThat(recordCaptor.getValue().topic()).isEqualTo("link-updates-dlq");
        assertThat(readHeader(recordCaptor.getValue(), "error-type")).isEqualTo("processing");
        assertThat(readHeader(recordCaptor.getValue(), "error-attempt")).isEqualTo("3");
    }

    @Test
    void handleStopsRetryingWhenDelayIsInterrupted() {
        byte[] payload = new byte[] {9, 9, 9};
        when(kafkaAvroDeserializer.deserialize("link-updates", payload))
                .thenReturn(event(1L, "https://example.com", "changed", List.of(10L)));
        when(retryDelayService.delay(Duration.ofSeconds(1))).thenReturn(false);
        doThrow(new IllegalStateException("telegram unavailable"))
                .when(botUpdateUseCase)
                .processLinkUpdate(any());

        messageHandler.handle(payload, "key-5", null);

        verify(botUpdateUseCase).processLinkUpdate(any());
        ArgumentCaptor<ProducerRecord<String, LinkUpdateEvent>> recordCaptor = eventRecordCaptor();
        verify(kafkaTemplate).send(recordCaptor.capture());
        assertThat(readHeader(recordCaptor.getValue(), "error-type")).isEqualTo("processing");
        assertThat(readHeader(recordCaptor.getValue(), "error-attempt")).isEqualTo("1");
    }

    @Test
    void handleRetriesAndStopsWhenProcessingEventuallySucceeds() {
        byte[] payload = new byte[] {11, 12, 13};
        when(kafkaAvroDeserializer.deserialize("link-updates", payload))
                .thenReturn(event(1L, "https://example.com", "changed", List.of(10L)));
        doThrow(new IllegalStateException("first failure"))
                .doNothing()
                .when(botUpdateUseCase)
                .processLinkUpdate(any());

        messageHandler.handle(payload, "key-6", null);

        verify(botUpdateUseCase, times(2)).processLinkUpdate(any());
        verify(retryDelayService).delay(Duration.ofSeconds(1));
        verify(kafkaTemplate, never()).send(anyEventRecord());
    }

    @Test
    void handleSkipsAlreadyProcessedMessageId() {
        byte[] payload = new byte[] {14, 15, 16};
        UUID messageId = UUID.randomUUID();
        when(kafkaAvroDeserializer.deserialize("link-updates", payload))
                .thenReturn(event(1L, "https://example.com", "changed", List.of(10L)));
        when(processedUpdateRepository.isProcessed(messageId)).thenReturn(true);

        messageHandler.handle(payload, "key-7", messageId.toString().getBytes(StandardCharsets.UTF_8));

        verify(botUpdateUseCase, never()).processLinkUpdate(any());
        verify(processedUpdateRepository, never()).markProcessed(any());
    }

    @Test
    void handleMarksMessageIdProcessedAfterSuccessfulDelivery() {
        byte[] payload = new byte[] {17, 18, 19};
        UUID messageId = UUID.randomUUID();
        when(kafkaAvroDeserializer.deserialize("link-updates", payload))
                .thenReturn(event(1L, "https://example.com", "changed", List.of(10L)));

        messageHandler.handle(payload, "key-8", messageId.toString().getBytes(StandardCharsets.UTF_8));

        verify(processedUpdateRepository).isProcessed(messageId);
        verify(processedUpdateRepository).markProcessed(messageId);
    }

    @Test
    void invalidMessageIdHeaderDoesNotUseIdempotencyRepository() {
        byte[] payload = new byte[] {20, 21, 22};
        when(kafkaAvroDeserializer.deserialize("link-updates", payload))
                .thenReturn(event(1L, "https://example.com", "changed", List.of(10L)));

        messageHandler.handle(payload, "key-9", "not-a-uuid".getBytes(StandardCharsets.UTF_8));

        verify(botUpdateUseCase).processLinkUpdate(any());
        verify(processedUpdateRepository, never()).isProcessed(any());
        verify(processedUpdateRepository, never()).markProcessed(any());
    }

    private LinkUpdateEvent event(long id, String url, String description, List<Long> chatIds) {
        LinkUpdateEvent event = new LinkUpdateEvent();
        event.setId(id);
        event.setUrl(url);
        event.setDescription(description);
        event.setTgChatIds(chatIds);
        return event;
    }

    private String readHeader(ProducerRecord<?, ?> record, String headerName) {
        var header = record.headers().lastHeader(headerName);
        if (header == null || header.value() == null) {
            return null;
        }
        return new String(header.value(), StandardCharsets.UTF_8);
    }

    private ProducerRecord<String, LinkUpdateEvent> anyEventRecord() {
        return any();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private ArgumentCaptor<ProducerRecord<String, LinkUpdateEvent>> eventRecordCaptor() {
        return ArgumentCaptor.forClass((Class) ProducerRecord.class);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private ArgumentCaptor<ProducerRecord<String, byte[]>> bytesRecordCaptor() {
        return ArgumentCaptor.forClass((Class) ProducerRecord.class);
    }
}
