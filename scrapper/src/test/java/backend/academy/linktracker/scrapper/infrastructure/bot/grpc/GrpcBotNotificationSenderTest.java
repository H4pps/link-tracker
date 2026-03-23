package backend.academy.linktracker.scrapper.infrastructure.bot.grpc;

import static org.assertj.core.api.Assertions.assertThat;

import backend.academy.linktracker.grpc.Ack;
import backend.academy.linktracker.grpc.BotServiceGrpc;
import backend.academy.linktracker.grpc.LinkUpdateRequest;
import backend.academy.linktracker.scrapper.application.update.LinkUpdateNotification;
import backend.academy.linktracker.scrapper.logging.ScrapperLogger;
import backend.academy.linktracker.scrapper.properties.BotProperties;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class GrpcBotNotificationSenderTest {

    private Server server;
    private GrpcBotNotificationSender sender;

    @AfterEach
    void tearDown() {
        if (sender != null) {
            sender.shutdown();
        }
        if (server != null) {
            server.shutdownNow();
        }
    }

    @Test
    void returnsTrueWhenBotAcceptsNotification() throws IOException {
        server = startServer(new BotServiceGrpc.BotServiceImplBase() {
            @Override
            public void sendUpdate(LinkUpdateRequest request, StreamObserver<Ack> responseObserver) {
                responseObserver.onNext(Ack.newBuilder().setAccepted(true).build());
                responseObserver.onCompleted();
            }
        });
        sender = createSender(server.getPort());

        boolean sent = sender.send(new LinkUpdateNotification(1L, "https://github.com/a/b", "changed", List.of(10L)));

        assertThat(sent).isTrue();
    }

    @Test
    void returnsFalseOnGrpcFailure() throws IOException {
        server = startServer(new BotServiceGrpc.BotServiceImplBase() {
            @Override
            public void sendUpdate(LinkUpdateRequest request, StreamObserver<Ack> responseObserver) {
                responseObserver.onError(Status.UNAVAILABLE.asRuntimeException());
            }
        });
        sender = createSender(server.getPort());

        boolean sent = sender.send(new LinkUpdateNotification(1L, "https://github.com/a/b", "changed", List.of(10L)));

        assertThat(sent).isFalse();
    }

    private Server startServer(BotServiceGrpc.BotServiceImplBase service) throws IOException {
        Server localServer = ServerBuilder.forPort(0).addService(service).build();
        localServer.start();
        return localServer;
    }

    private GrpcBotNotificationSender createSender(int port) {
        BotProperties properties = new BotProperties();
        properties.setGrpcHost("localhost");
        properties.setGrpcPort(port);
        properties.setGrpcDeadline(Duration.ofSeconds(1));
        return new GrpcBotNotificationSender(properties, new ScrapperLogger());
    }
}
