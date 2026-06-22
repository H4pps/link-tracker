package com.linktracker.ai.infrastructure.kafka.publisher;

import com.linktracker.ai.application.ProcessedUpdate;
import com.linktracker.ai.application.ProcessedUpdatePublisher;
import com.linktracker.ai.properties.KafkaProperties;
import com.linktracker.messaging.ProcessedLinkUpdateEvent;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes processed updates as Avro events to the {@code link.processed-updates} topic.
 */
@Component
@RequiredArgsConstructor
public class KafkaProcessedUpdatePublisher implements ProcessedUpdatePublisher {

    private static final String MESSAGE_ID_HEADER = "message-id";

    private final KafkaTemplate<String, ProcessedLinkUpdateEvent> processedUpdateKafkaTemplate;
    private final KafkaProperties kafkaProperties;

    @Override
    public void publish(ProcessedUpdate update, String messageId) {
        ProcessedLinkUpdateEvent event = new ProcessedLinkUpdateEvent(
                update.id(), update.url(), update.description(), update.tgChatIds(), update.priority());
        ProducerRecord<String, ProcessedLinkUpdateEvent> record =
                new ProducerRecord<>(kafkaProperties.getProcessedUpdatesTopic(), update.url(), event);
        if (messageId != null && !messageId.isBlank()) {
            record.headers().add(new RecordHeader(MESSAGE_ID_HEADER, messageId.getBytes(StandardCharsets.UTF_8)));
        }
        processedUpdateKafkaTemplate.send(record);
    }
}
