package backend.academy.linktracker.scrapper.infrastructure.bot.grpc;

import backend.academy.linktracker.scrapper.application.update.BotNotificationSender;
import backend.academy.linktracker.scrapper.application.update.LinkUpdateNotification;
import backend.academy.linktracker.scrapper.application.update.LinkUpdateOutboxRepository;
import backend.academy.linktracker.scrapper.infrastructure.bot.kafka.sender.KafkaBotNotificationSender;
import backend.academy.linktracker.scrapper.logging.ScrapperLogger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * gRPC-primary sender that falls back to Kafka outbox storage on failed notification delivery.
 */
@Component
@ConditionalOnProperty(prefix = "app.bot", name = "mode", havingValue = "grpc", matchIfMissing = false)
public class GrpcFallbackBotNotificationSender implements BotNotificationSender {

    private final GrpcBotNotificationSender grpcSender;
    private final KafkaBotNotificationSender fallbackSender;

    public GrpcFallbackBotNotificationSender(
            GrpcBotNotificationSender grpcSender,
            LinkUpdateOutboxRepository outboxRepository,
            ScrapperLogger scrapperLogger) {
        this(grpcSender, new KafkaBotNotificationSender(outboxRepository, scrapperLogger));
    }

    GrpcFallbackBotNotificationSender(GrpcBotNotificationSender grpcSender, KafkaBotNotificationSender fallbackSender) {
        this.grpcSender = grpcSender;
        this.fallbackSender = fallbackSender;
    }

    @Override
    public boolean send(LinkUpdateNotification notification) {
        try {
            if (grpcSender.send(notification)) {
                return true;
            }
        } catch (RuntimeException ignored) {
            return fallbackSender.send(notification);
        }
        return fallbackSender.send(notification);
    }
}
