package backend.academy.linktracker.bot.infrastructure.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import backend.academy.linktracker.bot.application.update.BotUpdateUseCase;
import backend.academy.linktracker.bot.application.update.LinkUpdateCommand;
import backend.academy.linktracker.bot.application.update.ProcessedUpdateRepository;
import backend.academy.linktracker.bot.properties.KafkaProperties;
import backend.academy.linktracker.messaging.LinkUpdateEvent;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KafkaLinkUpdateMessageHandlerTest {

    @Mock
    private BotUpdateUseCase botUpdateUseCase;

    @Mock
    private KafkaAvroDeserializer kafkaAvroDeserializer;

    @Mock
    private ProcessedUpdateRepository processedUpdateRepository;

    private KafkaLinkUpdateMessageHandler messageHandler;

    @BeforeEach
    void setUp() {
        KafkaProperties kafkaProperties = new KafkaProperties();
        kafkaProperties.setLinkUpdatesTopic("link-updates");
        LinkUpdateEventProcessingService processingService = new LinkUpdateEventProcessingService(
                botUpdateUseCase,
                processedUpdateRepository,
                new LinkUpdateEventValidator(),
                new LinkUpdateEventMapper());
        messageHandler = new KafkaLinkUpdateMessageHandler(kafkaProperties, kafkaAvroDeserializer, processingService);
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
    void handleThrowsValidationExceptionWithoutCallingUseCase() {
        byte[] payload = new byte[] {1, 2, 3};
        when(kafkaAvroDeserializer.deserialize("link-updates", payload))
                .thenReturn(event(0L, "https://example.com", "changed", List.of(10L)));

        assertThatThrownBy(() -> messageHandler.handle(payload, "key-2", null))
                .isInstanceOf(KafkaLinkUpdateValidationException.class)
                .hasMessage("id must be positive")
                .hasCauseInstanceOf(IllegalArgumentException.class);

        verify(botUpdateUseCase, never()).processLinkUpdate(any());
    }

    @Test
    void handleThrowsDeserializationExceptionWithoutCallingUseCase() {
        byte[] payload = new byte[] {7, 8, 9};
        when(kafkaAvroDeserializer.deserialize("link-updates", payload))
                .thenThrow(new IllegalStateException("broken avro payload"));

        assertThatThrownBy(() -> messageHandler.handle(payload, "key-3", null))
                .isInstanceOf(KafkaLinkUpdateDeserializationException.class)
                .hasMessage("Failed to deserialize link update event")
                .hasCauseInstanceOf(IllegalStateException.class);

        verify(botUpdateUseCase, never()).processLinkUpdate(any());
    }

    @Test
    void handlePropagatesProcessingExceptionForContainerRetry() {
        byte[] payload = new byte[] {4, 5, 6};
        when(kafkaAvroDeserializer.deserialize("link-updates", payload))
                .thenReturn(event(1L, "https://example.com", "changed", List.of(10L)));
        doThrow(new IllegalStateException("telegram unavailable"))
                .when(botUpdateUseCase)
                .processLinkUpdate(any());

        assertThatThrownBy(() -> messageHandler.handle(payload, "key-4", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("telegram unavailable");

        verify(botUpdateUseCase).processLinkUpdate(any());
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
}
