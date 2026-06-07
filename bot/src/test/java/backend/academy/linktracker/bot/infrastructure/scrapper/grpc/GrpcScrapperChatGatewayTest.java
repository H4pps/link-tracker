package backend.academy.linktracker.bot.infrastructure.scrapper.grpc;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import backend.academy.linktracker.bot.application.scrapper.exception.ScrapperUnavailableException;
import backend.academy.linktracker.bot.logging.BotLogger;
import backend.academy.linktracker.bot.properties.ResilienceProperties;
import backend.academy.linktracker.bot.properties.ScrapperProperties;
import backend.academy.linktracker.grpc.Ack;
import backend.academy.linktracker.grpc.ChatRequest;
import backend.academy.linktracker.grpc.ScrapperServiceGrpc;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class GrpcScrapperChatGatewayTest {

    private Server server;
    private GrpcScrapperClient client;

    @AfterEach
    void tearDown() {
        if (client != null) {
            client.shutdown();
        }
        if (server != null) {
            server.shutdownNow();
        }
    }

    @Test
    void registerChatMapsUnavailableStatus() throws IOException {
        server = startServer(new ScrapperServiceGrpc.ScrapperServiceImplBase() {
            @Override
            public void registerChat(ChatRequest request, StreamObserver<Ack> responseObserver) {
                responseObserver.onError(Status.UNAVAILABLE.asRuntimeException());
            }
        });

        GrpcScrapperChatGateway gateway = new GrpcScrapperChatGateway(createClient(server.getPort()));

        assertThatThrownBy(() -> gateway.registerChat(1L)).isInstanceOf(ScrapperUnavailableException.class);
    }

    @Test
    void deleteChatMapsUnavailableStatus() throws IOException {
        server = startServer(new ScrapperServiceGrpc.ScrapperServiceImplBase() {
            @Override
            public void deleteChat(ChatRequest request, StreamObserver<Ack> responseObserver) {
                responseObserver.onError(Status.UNAVAILABLE.asRuntimeException());
            }
        });

        GrpcScrapperChatGateway gateway = new GrpcScrapperChatGateway(createClient(server.getPort()));

        assertThatThrownBy(() -> gateway.deleteChat(1L)).isInstanceOf(ScrapperUnavailableException.class);
    }

    @Test
    void registerChatRetriesUnavailableStatusUntilSuccess() throws IOException {
        AtomicInteger attempts = new AtomicInteger();
        server = startServer(new ScrapperServiceGrpc.ScrapperServiceImplBase() {
            @Override
            public void registerChat(ChatRequest request, StreamObserver<Ack> responseObserver) {
                if (attempts.incrementAndGet() < 3) {
                    responseObserver.onError(Status.UNAVAILABLE.asRuntimeException());
                    return;
                }
                responseObserver.onNext(Ack.newBuilder().setAccepted(true).build());
                responseObserver.onCompleted();
            }
        });
        GrpcScrapperChatGateway gateway = new GrpcScrapperChatGateway(createClient(server.getPort()));

        gateway.registerChat(1L);

        org.assertj.core.api.Assertions.assertThat(attempts).hasValue(3);
    }

    private Server startServer(ScrapperServiceGrpc.ScrapperServiceImplBase service) throws IOException {
        Server localServer = ServerBuilder.forPort(0).addService(service).build();
        localServer.start();
        return localServer;
    }

    private GrpcScrapperClient createClient(int port) {
        ScrapperProperties properties = new ScrapperProperties();
        properties.setGrpcHost("localhost");
        properties.setGrpcPort(port);
        properties.setGrpcDeadline(Duration.ofSeconds(1));
        ResilienceProperties resilienceProperties = new ResilienceProperties();
        resilienceProperties.retry().setMaxAttempts(3);
        resilienceProperties.retry().setBackoff(Duration.ofMillis(1));
        resilienceProperties.circuitBreaker().setMinimumNumberOfCalls(10);
        resilienceProperties.circuitBreaker().setSlidingWindowSize(10);
        client = new GrpcScrapperClient(properties, resilienceProperties, new BotLogger());
        return client;
    }
}
