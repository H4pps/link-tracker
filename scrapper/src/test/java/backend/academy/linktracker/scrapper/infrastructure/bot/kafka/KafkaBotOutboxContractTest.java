package backend.academy.linktracker.scrapper.infrastructure.bot.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import backend.academy.linktracker.scrapper.application.update.BotNotificationSender;
import backend.academy.linktracker.scrapper.application.update.LinkUpdateNotification;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class KafkaBotOutboxContractTest {

    private static final String KAFKA_SENDER_CLASS =
            "backend.academy.linktracker.scrapper.infrastructure.bot.kafka.sender.KafkaBotNotificationSender";
    private static final String OUTBOX_REPOSITORY_CLASS =
            "backend.academy.linktracker.scrapper.application.update.LinkUpdateOutboxRepository";
    private static final String OUTBOX_EVENT_CLASS =
            "backend.academy.linktracker.scrapper.application.update.LinkUpdateOutboxEvent";

    @Test
    void kafkaSenderStoresOutboxEventAsDeliveryContract() {
        Class<?> kafkaSenderClass = loadRequiredClass(KAFKA_SENDER_CLASS);
        Class<?> outboxRepositoryClass = loadRequiredClass(OUTBOX_REPOSITORY_CLASS);
        Class<?> outboxEventClass = loadRequiredClass(OUTBOX_EVENT_CLASS);

        assertThat(BotNotificationSender.class.isAssignableFrom(kafkaSenderClass))
                .isTrue();
        assertThat(Arrays.stream(kafkaSenderClass.getDeclaredConstructors())
                        .flatMap(constructor -> Arrays.stream(constructor.getParameterTypes()))
                        .anyMatch(outboxRepositoryClass::isAssignableFrom))
                .as("Kafka sender must depend on outbox repository instead of direct bot availability")
                .isTrue();
        assertThat(Arrays.stream(outboxRepositoryClass.getDeclaredMethods())
                        .anyMatch(method -> acceptsOutboxEvent(method, outboxEventClass)))
                .as("Outbox repository must expose an operation accepting LinkUpdateOutboxEvent")
                .isTrue();
    }

    @Test
    void kafkaSenderSendMethodAcknowledgesOutboxStorage() throws NoSuchMethodException {
        Class<?> kafkaSenderClass = loadRequiredClass(KAFKA_SENDER_CLASS);
        Method sendMethod = kafkaSenderClass.getMethod("send", LinkUpdateNotification.class);

        assertThat(sendMethod.getReturnType()).isEqualTo(boolean.class);
    }

    private boolean acceptsOutboxEvent(Method method, Class<?> outboxEventClass) {
        return Arrays.stream(method.getParameterTypes()).anyMatch(outboxEventClass::isAssignableFrom);
    }

    private Class<?> loadRequiredClass(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException exception) {
            throw new AssertionError("Missing required class: " + className, exception);
        }
    }
}
