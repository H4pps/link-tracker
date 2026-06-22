package com.linktracker.scrapper.infrastructure.bot.grpc;

import com.linktracker.grpc.Ack;
import com.linktracker.grpc.BotServiceGrpc;
import com.linktracker.grpc.LinkUpdateRequest;
import com.linktracker.scrapper.application.update.LinkUpdateNotification;
import com.linktracker.scrapper.infrastructure.resilience.GrpcResiliencePredicates;
import com.linktracker.scrapper.infrastructure.resilience.ResilientCallExecutor;
import com.linktracker.scrapper.logging.ScrapperLogger;
import com.linktracker.scrapper.properties.BotProperties;
import com.linktracker.scrapper.properties.ResilienceProperties;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * gRPC sender for scrapper-to-bot update notifications.
 */
@Component
@ConditionalOnProperty(prefix = "app.bot", name = "mode", havingValue = "grpc", matchIfMissing = false)
public class GrpcBotNotificationSender {

    private final BotProperties botProperties;
    private final ResilientCallExecutor resilientCallExecutor;
    private final ScrapperLogger scrapperLogger;

    private ManagedChannel channel;
    private BotServiceGrpc.BotServiceBlockingStub blockingStub;

    public GrpcBotNotificationSender(
            BotProperties botProperties, ResilienceProperties resilienceProperties, ScrapperLogger scrapperLogger) {
        this.botProperties = botProperties;
        this.resilientCallExecutor = new ResilientCallExecutor(resilienceProperties);
        this.scrapperLogger = scrapperLogger;
    }

    /**
     * Sends update payload to the Bot gRPC endpoint.
     *
     * @param notification update payload
     * @return true when bot accepted notification
     */
    public synchronized boolean send(LinkUpdateNotification notification) {
        try {
            return resilientCallExecutor.execute(
                    "bot-grpc-notifications",
                    () -> sendOnce(notification),
                    GrpcResiliencePredicates::isRetryableFailure,
                    throwable -> GrpcResiliencePredicates.isCircuitBreakerFailure(throwable)
                            || throwable instanceof BotNotificationRejectedException);
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

    private synchronized BotServiceGrpc.BotServiceBlockingStub withDeadline() {
        return blockingStub.withDeadlineAfter(botProperties.getGrpcDeadline().toMillis(), TimeUnit.MILLISECONDS);
    }

    private boolean sendOnce(LinkUpdateNotification notification) {
        initializeIfNeeded();
        Ack response = withDeadline()
                .sendUpdate(LinkUpdateRequest.newBuilder()
                        .setId(notification.id())
                        .setUrl(notification.url())
                        .setDescription(notification.description() == null ? "" : notification.description())
                        .addAllTgChatIds(notification.tgChatIds())
                        .build());
        if (!response.getAccepted()) {
            throw new BotNotificationRejectedException();
        }
        return true;
    }

    private synchronized void initializeIfNeeded() {
        if (channel != null) {
            return;
        }
        channel = ManagedChannelBuilder.forAddress(botProperties.getGrpcHost(), botProperties.getGrpcPort())
                .usePlaintext()
                .build();
        blockingStub = BotServiceGrpc.newBlockingStub(channel);
    }

    private static class BotNotificationRejectedException extends RuntimeException {
        private BotNotificationRejectedException() {
            super("Bot rejected gRPC notification");
        }
    }
}
