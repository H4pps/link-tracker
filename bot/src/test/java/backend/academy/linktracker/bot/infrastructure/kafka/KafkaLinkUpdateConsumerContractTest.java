package backend.academy.linktracker.bot.infrastructure.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import backend.academy.linktracker.bot.application.update.BotUpdateUseCase;
import backend.academy.linktracker.bot.application.update.LinkUpdateCommand;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class KafkaLinkUpdateConsumerContractTest {

    private static final String CONSUMER_CLASS =
            "backend.academy.linktracker.bot.infrastructure.kafka.KafkaLinkUpdateConsumer";
    private static final String EVENT_CLASS = "backend.academy.linktracker.messaging.LinkUpdateEvent";

    @Test
    void kafkaConsumerExistsAndDependsOnBotUpdateUseCase() {
        Class<?> consumerClass = loadRequiredClass(CONSUMER_CLASS);

        assertThat(Arrays.stream(consumerClass.getDeclaredConstructors())
                        .flatMap(constructor -> Arrays.stream(constructor.getParameterTypes()))
                        .anyMatch(BotUpdateUseCase.class::isAssignableFrom))
                .as("Kafka consumer must delegate valid events to BotUpdateUseCase")
                .isTrue();
    }

    @Test
    void kafkaConsumerDefinesEventMappingAndValidationContract() {
        Class<?> consumerClass = loadRequiredClass(CONSUMER_CLASS);
        Class<?> eventClass = loadRequiredClass(EVENT_CLASS);

        assertThat(hasAccessorOrField(eventClass, "id")).isTrue();
        assertThat(hasAccessorOrField(eventClass, "url")).isTrue();
        assertThat(hasAccessorOrField(eventClass, "description")).isTrue();
        assertThat(hasAccessorOrField(eventClass, "tgChatIds")).isTrue();

        assertThat(findMethod(consumerClass, "consume", eventClass)).isNotNull();
        assertThat(Arrays.stream(consumerClass.getDeclaredMethods())
                        .anyMatch(method -> method.getReturnType().equals(LinkUpdateCommand.class)
                                && method.getParameterCount() == 1
                                && method.getParameterTypes()[0].equals(eventClass)))
                .as("Consumer must map a valid LinkUpdateEvent to LinkUpdateCommand")
                .isTrue();
        assertThat(Arrays.stream(consumerClass.getDeclaredMethods())
                        .anyMatch(method -> method.getName().toLowerCase().contains("validate")
                                && method.getParameterCount() == 1
                                && method.getParameterTypes()[0].equals(eventClass)))
                .as("Consumer must validate positive id, nonblank url, and nonempty positive tgChatIds")
                .isTrue();
    }

    private Method findMethod(Class<?> type, String methodName, Class<?> parameterType) {
        try {
            return type.getMethod(methodName, parameterType);
        } catch (NoSuchMethodException exception) {
            return null;
        }
    }

    private boolean hasAccessorOrField(Class<?> type, String logicalName) {
        String suffix = Character.toUpperCase(logicalName.charAt(0)) + logicalName.substring(1);
        return Arrays.stream(type.getMethods())
                        .anyMatch(method -> method.getName().equals("get" + suffix))
                || Arrays.stream(type.getDeclaredFields())
                        .anyMatch(field -> field.getName().equals(logicalName));
    }

    private Class<?> loadRequiredClass(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException exception) {
            throw new AssertionError("Missing required class: " + className, exception);
        }
    }
}
