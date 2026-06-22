package com.linktracker.bot.infrastructure.kafka.consumer;

import static org.mockito.Mockito.verify;

import com.linktracker.bot.infrastructure.kafka.handler.KafkaLinkUpdateMessageHandler;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KafkaLinkUpdateConsumerTest {

    @Mock
    private KafkaLinkUpdateMessageHandler messageHandler;

    @Test
    void listenDelegatesRawKafkaMessageToHandler() {
        KafkaLinkUpdateConsumer consumer = new KafkaLinkUpdateConsumer(messageHandler);
        byte[] payload = new byte[] {1, 2, 3};
        byte[] messageIdHeader = "message-id".getBytes(StandardCharsets.UTF_8);

        consumer.listen(payload, "key-1", messageIdHeader);

        verify(messageHandler).handle(payload, "key-1", messageIdHeader);
    }
}
