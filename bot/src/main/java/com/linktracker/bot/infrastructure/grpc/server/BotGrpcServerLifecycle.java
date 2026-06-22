package com.linktracker.bot.infrastructure.grpc.server;

import com.linktracker.bot.logging.BotLogger;
import com.linktracker.bot.properties.GrpcServerProperties;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Starts and stops embedded gRPC server for bot service.
 */
@Component
@RequiredArgsConstructor
public class BotGrpcServerLifecycle {

    private final GrpcServerProperties grpcServerProperties;
    private final BotGrpcServiceEndpoint botGrpcServiceEndpoint;
    private final BotLogger botLogger;

    private Server server;

    /**
     * Starts gRPC server after Spring context is ready.
     *
     * @throws IOException when server cannot be started
     */
    @EventListener(ApplicationReadyEvent.class)
    public synchronized void start() throws IOException {
        if (server != null) {
            return;
        }
        int port = grpcServerProperties.getPort();
        server = ServerBuilder.forPort(port)
                .addService(botGrpcServiceEndpoint)
                .build()
                .start();
        botLogger.logGrpcServerStarted(port);
    }

    /**
     * Stops gRPC server during application shutdown.
     */
    @PreDestroy
    public synchronized void stop() {
        if (server == null) {
            return;
        }
        server.shutdown();
        try {
            server.awaitTermination(3, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
