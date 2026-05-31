package backend.academy.linktracker.bot.infrastructure.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
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
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

@ExtendWith(MockitoExtension.class)
class KafkaLinkUpdateConsumerTest {

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

    private KafkaProperties kafkaProperties;
    private KafkaLinkUpdateConsumer consumer;

    @BeforeEach
    void setUp() {
        kafkaProperties = new KafkaProperties();
        kafkaProperties.setLinkUpdatesTopic("link-updates");
        kafkaProperties.setLinkUpdatesDlqTopic("link-updates-dlq");
        kafkaProperties.setMaxAttempts(3);
        kafkaProperties.setRetryBackoff(Duration.ZERO);
        consumer = new KafkaLinkUpdateConsumer(
                botUpdateUseCase,
                kafkaProperties,
                kafkaAvroDeserializer,
                kafkaTemplate,
                kafkaBytesTemplate,
                processedUpdateRepository);
    }

    @Test
    void consumeMapsValidEventToUseCaseCommand() {
        LinkUpdateEvent event = event(1L, "https://example.com", "changed", List.of(10L, 20L));

        consumer.consume(event);

        ArgumentCaptor<LinkUpdateCommand> captor = ArgumentCaptor.forClass(LinkUpdateCommand.class);
        verify(botUpdateUseCase).processLinkUpdate(captor.capture());
        assertThat(captor.getValue().id()).isEqualTo(1L);
        assertThat(captor.getValue().url()).isEqualTo("https://example.com");
        assertThat(captor.getValue().description()).isEqualTo("changed");
        assertThat(captor.getValue().tgChatIds()).containsExactly(10L, 20L);
    }

    @Test
    void listenSendsValidationFailuresToDlqWithoutRetries() {
        byte[] payload = new byte[] {1, 2, 3};
        when(kafkaAvroDeserializer.deserialize("link-updates", payload))
                .thenReturn(event(0L, "https://example.com", "changed", List.of(10L)));

        consumer.listen(payload, "key-1", null);

        verify(botUpdateUseCase, never()).processLinkUpdate(any());
        verify(kafkaTemplate).send(any(ProducerRecord.class));
    }

    @Test
    void listenSendsDeserializationFailuresToDlqWithoutRetries() {
        byte[] payload = new byte[] {7, 8, 9};
        when(kafkaAvroDeserializer.deserialize("link-updates", payload))
                .thenThrow(new IllegalStateException("broken avro payload"));

        consumer.listen(payload, "key-2", null);

        verify(botUpdateUseCase, never()).processLinkUpdate(any());
        ArgumentCaptor<ProducerRecord<String, byte[]>> recordCaptor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaBytesTemplate).send(recordCaptor.capture());
        assertThat(recordCaptor.getValue().topic()).isEqualTo("link-updates-dlq");
        assertThat(recordCaptor.getValue().value()).isEqualTo(payload);
        assertThat(readHeader(recordCaptor.getValue(), "error-type")).isEqualTo("deserialization");
    }

    @Test
    void listenRetriesProcessingFailuresAndPublishesToDlqWhenExhausted() {
        byte[] payload = new byte[] {4, 5, 6};
        when(kafkaAvroDeserializer.deserialize("link-updates", payload))
                .thenReturn(event(1L, "https://example.com", "changed", List.of(10L)));
        doThrow(new IllegalStateException("telegram unavailable"))
                .when(botUpdateUseCase)
                .processLinkUpdate(any());

        consumer.listen(payload, "key-3", null);

        verify(botUpdateUseCase, times(3)).processLinkUpdate(any());
        ArgumentCaptor<ProducerRecord<String, LinkUpdateEvent>> recordCaptor =
                ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(recordCaptor.capture());
        assertThat(recordCaptor.getValue().topic()).isEqualTo("link-updates-dlq");
        assertThat(readHeader(recordCaptor.getValue(), "error-type")).isEqualTo("processing");
        assertThat(readHeader(recordCaptor.getValue(), "error-attempt")).isEqualTo("3");
    }

    @Test
    void listenRetriesAndStopsWhenProcessingEventuallySucceeds() {
        byte[] payload = new byte[] {11, 12, 13};
        when(kafkaAvroDeserializer.deserialize("link-updates", payload))
                .thenReturn(event(1L, "https://example.com", "changed", List.of(10L)));
        doThrow(new IllegalStateException("first failure"))
                .doNothing()
                .when(botUpdateUseCase)
                .processLinkUpdate(any());

        consumer.listen(payload, "key-4", null);

        verify(botUpdateUseCase, times(2)).processLinkUpdate(any());
        verify(kafkaTemplate, never()).send(any(ProducerRecord.class));
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
}
