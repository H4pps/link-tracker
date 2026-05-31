package backend.academy.linktracker.scrapper.infrastructure.bot;

import static org.assertj.core.api.Assertions.assertThat;

import backend.academy.linktracker.scrapper.application.update.BotNotificationSender;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

class BotTransportSenderConditionalContractTest {

    @Test
    void grpcSenderRequiresExplicitGrpcModeAndIsNoLongerMatchIfMissing() {
        Class<?> grpcSenderClass = loadRequiredClass(
                "backend.academy.linktracker.scrapper.infrastructure.bot.grpc.GrpcBotNotificationSender");
        ConditionalOnProperty condition = conditionalOnProperty(grpcSenderClass);

        assertThat(condition.prefix()).isEqualTo("app.bot");
        assertThat(condition.name()).containsExactly("mode");
        assertThat(condition.havingValue()).isEqualTo("grpc");
        assertThat(condition.matchIfMissing()).isFalse();
    }

    @Test
    void httpSenderRemainsExplicitHttpModeOnly() {
        Class<?> httpSenderClass = loadRequiredClass(
                "backend.academy.linktracker.scrapper.infrastructure.bot.http.HttpBotNotificationSender");
        ConditionalOnProperty condition = conditionalOnProperty(httpSenderClass);

        assertThat(condition.prefix()).isEqualTo("app.bot");
        assertThat(condition.name()).containsExactly("mode");
        assertThat(condition.havingValue()).isEqualTo("http");
        assertThat(condition.matchIfMissing()).isFalse();
    }

    @Test
    void kafkaSenderExistsAndIsDefaultMatchIfMissingMode() {
        Class<?> kafkaSenderClass = loadRequiredClass(
                "backend.academy.linktracker.scrapper.infrastructure.bot.kafka.KafkaBotNotificationSender");
        ConditionalOnProperty condition = conditionalOnProperty(kafkaSenderClass);

        assertThat(BotNotificationSender.class.isAssignableFrom(kafkaSenderClass))
                .isTrue();
        assertThat(condition.prefix()).isEqualTo("app.bot");
        assertThat(condition.name()).containsExactly("mode");
        assertThat(condition.havingValue()).isEqualTo("kafka");
        assertThat(condition.matchIfMissing()).isTrue();
    }

    private Class<?> loadRequiredClass(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException exception) {
            throw new AssertionError("Missing required class: " + className, exception);
        }
    }

    private ConditionalOnProperty conditionalOnProperty(Class<?> type) {
        ConditionalOnProperty annotation = type.getAnnotation(ConditionalOnProperty.class);
        assertThat(annotation)
                .as("@ConditionalOnProperty must be present on %s", type.getName())
                .isNotNull();
        return annotation;
    }
}
