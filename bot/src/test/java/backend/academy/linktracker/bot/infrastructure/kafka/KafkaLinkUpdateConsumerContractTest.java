package backend.academy.linktracker.bot.infrastructure.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import backend.academy.linktracker.bot.application.update.LinkUpdateCommand;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;

class KafkaLinkUpdateConsumerContractTest {

    private static final String CONSUMER_CLASS =
            "backend.academy.linktracker.bot.infrastructure.kafka.consumer.KafkaLinkUpdateConsumer";
    private static final String MESSAGE_HANDLER_CLASS =
            "backend.academy.linktracker.bot.infrastructure.kafka.handler.KafkaLinkUpdateMessageHandler";
    private static final String EVENT_CLASS = "backend.academy.linktracker.messaging.LinkUpdateEvent";
    private static final String EVENT_MAPPER_CLASS =
            "backend.academy.linktracker.bot.infrastructure.kafka.processing.LinkUpdateEventMapper";
    private static final String EVENT_VALIDATOR_CLASS =
            "backend.academy.linktracker.bot.infrastructure.kafka.processing.LinkUpdateEventValidator";
    private static final String KAFKA_CONFIGURATION_CLASS =
            "backend.academy.linktracker.bot.infrastructure.kafka.config.KafkaConsumerConfiguration";

    @Test
    void kafkaConsumerExistsAndDependsOnMessageHandler() {
        Class<?> consumerClass = loadRequiredClass(CONSUMER_CLASS);
        Class<?> messageHandlerClass = loadRequiredClass(MESSAGE_HANDLER_CLASS);

        assertThat(Arrays.stream(consumerClass.getDeclaredConstructors())
                        .flatMap(constructor -> Arrays.stream(constructor.getParameterTypes()))
                        .anyMatch(messageHandlerClass::isAssignableFrom))
                .as("Kafka consumer must delegate raw listener inputs to the message handler")
                .isTrue();
    }

    @Test
    void kafkaConsumerDefinesRawListenerContract() {
        Class<?> consumerClass = loadRequiredClass(CONSUMER_CLASS);

        assertThat(findMethod(consumerClass, "listen", byte[].class, String.class, byte[].class))
                .as("Consumer listener must keep receiving payload, key, and message-id header")
                .isNotNull();
    }

    @Test
    void mapperAndValidatorDefineEventContract() {
        Class<?> eventClass = loadRequiredClass(EVENT_CLASS);
        Class<?> mapperClass = loadRequiredClass(EVENT_MAPPER_CLASS);
        Class<?> validatorClass = loadRequiredClass(EVENT_VALIDATOR_CLASS);

        assertThat(hasAccessorOrField(eventClass, "id")).isTrue();
        assertThat(hasAccessorOrField(eventClass, "url")).isTrue();
        assertThat(hasAccessorOrField(eventClass, "description")).isTrue();
        assertThat(hasAccessorOrField(eventClass, "tgChatIds")).isTrue();
        assertThat(Arrays.stream(mapperClass.getDeclaredMethods())
                        .anyMatch(method -> method.getReturnType().equals(LinkUpdateCommand.class)
                                && method.getParameterCount() == 1
                                && method.getParameterTypes()[0].equals(eventClass)))
                .as("Mapper must convert a valid LinkUpdateEvent to LinkUpdateCommand")
                .isTrue();
        assertThat(Arrays.stream(validatorClass.getDeclaredMethods())
                        .anyMatch(method -> method.getName().toLowerCase().contains("validate")
                                && method.getParameterCount() == 1
                                && method.getParameterTypes()[0].equals(eventClass)))
                .as("Validator must validate positive id, nonblank url, and nonempty positive tgChatIds")
                .isTrue();
    }

    @Test
    void kafkaConfigurationDefinesNativeRetryAndRecovererContract() {
        Class<?> configurationClass = loadRequiredClass(KAFKA_CONFIGURATION_CLASS);

        assertThat(Arrays.stream(configurationClass.getDeclaredMethods())
                        .anyMatch(method -> method.getReturnType().equals(DefaultErrorHandler.class)))
                .as("Kafka consumer retry must be configured through Spring Kafka DefaultErrorHandler")
                .isTrue();
        assertThat(Arrays.stream(configurationClass.getDeclaredMethods())
                        .anyMatch(method -> method.getReturnType().equals(DeadLetterPublishingRecoverer.class)))
                .as("Kafka consumer DLQ publication must use Spring Kafka DeadLetterPublishingRecoverer")
                .isTrue();
    }

    private Method findMethod(Class<?> type, String methodName, Class<?>... parameterTypes) {
        try {
            return type.getMethod(methodName, parameterTypes);
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
