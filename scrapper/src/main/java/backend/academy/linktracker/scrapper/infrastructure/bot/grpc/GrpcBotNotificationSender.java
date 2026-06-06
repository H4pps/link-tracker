package backend.academy.linktracker.scrapper.infrastructure.bot.grpc;

import backend.academy.linktracker.grpc.Ack;
import backend.academy.linktracker.grpc.BotServiceGrpc;
import backend.academy.linktracker.grpc.LinkUpdateRequest;
import backend.academy.linktracker.scrapper.application.update.BotNotificationSender;
import backend.academy.linktracker.scrapper.application.update.LinkUpdateNotification;
import backend.academy.linktracker.scrapper.logging.ScrapperLogger;
import backend.academy.linktracker.scrapper.properties.BotProperties;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * gRPC sender for scrapper-to-bot update notifications.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.bot", name = "mode", havingValue = "grpc", matchIfMissing = false)
public class GrpcBotNotificationSender implements BotNotificationSender {

    private final BotProperties botProperties;
    private final ScrapperLogger scrapperLogger;

    private ManagedChannel channel;
    private BotServiceGrpc.BotServiceBlockingStub blockingStub;

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized boolean send(LinkUpdateNotification notification) {
        try {
            initializeIfNeeded();
            Ack response = withDeadline()
                    .sendUpdate(LinkUpdateRequest.newBuilder()
                            .setId(notification.id())
                            .setUrl(notification.url())
                            .setDescription(notification.description() == null ? "" : notification.description())
                            .addAllTgChatIds(notification.tgChatIds())
                            .build());
            return response.getAccepted();
        } catch (StatusRuntimeException exception) {
            scrapperLogger.logExternalFetchFailed(
                    "bot-grpc",
                    notification.url(),
                    exception.getStatus().getCode().name());
            return false;
        } catch (RuntimeException exception) {
            scrapperLogger.logExternalFetchFailed(
                    "bot-grpc", notification.url(), exception.getClass().getSimpleName());
            return false;
        }
    }

    /**
     * Closes underlying gRPC channel.
     */
    @PreDestroy
    public synchronized void shutdown() {
        if (channel == null) {
            return;
        }
        channel.shutdown();
        try {
            channel.awaitTermination(3, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private BotServiceGrpc.BotServiceBlockingStub withDeadline() {
        return blockingStub.withDeadlineAfter(botProperties.getGrpcDeadline().toMillis(), TimeUnit.MILLISECONDS);
    }

    private void initializeIfNeeded() {
        if (channel != null) {
            return;
        }
        channel = ManagedChannelBuilder.forAddress(botProperties.getGrpcHost(), botProperties.getGrpcPort())
                .usePlaintext()
                .build();
        blockingStub = BotServiceGrpc.newBlockingStub(channel);
    }
}
