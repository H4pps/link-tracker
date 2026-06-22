package com.linktracker.bot.infrastructure.scrapper.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.linktracker.bot.application.scrapper.command.AddScrapperLinkCommand;
import com.linktracker.bot.application.scrapper.exception.ScrapperConflictException;
import com.linktracker.bot.application.scrapper.exception.ScrapperNotFoundException;
import com.linktracker.bot.application.scrapper.exception.ScrapperUnavailableException;
import com.linktracker.bot.logging.BotLogger;
import com.linktracker.bot.properties.ResilienceProperties;
import com.linktracker.bot.properties.ScrapperProperties;
import com.linktracker.grpc.AddLinkRequest;
import com.linktracker.grpc.Link;
import com.linktracker.grpc.ListLinksRequest;
import com.linktracker.grpc.ListLinksResponse;
import com.linktracker.grpc.RemoveLinkRequest;
import com.linktracker.grpc.ScrapperServiceGrpc;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class GrpcScrapperLinkGatewayTest {

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
    void listLinksMapsGrpcResponse() throws IOException {
        server = startServer(new ScrapperServiceGrpc.ScrapperServiceImplBase() {
            @Override
            public void listLinks(ListLinksRequest request, StreamObserver<ListLinksResponse> responseObserver) {
                responseObserver.onNext(ListLinksResponse.newBuilder()
                        .addLinks(Link.newBuilder()
                                .setId(1)
                                .setUrl("https://github.com/a/b")
                                .addTags("work")
                                .build())
                        .setSize(1)
                        .build());
                responseObserver.onCompleted();
            }
        });
        GrpcScrapperLinkGateway gateway = new GrpcScrapperLinkGateway(createClient(server.getPort()));

        var links = gateway.listLinks(1L);

        assertThat(links).hasSize(1);
        assertThat(links.getFirst().url()).isEqualTo("https://github.com/a/b");
        assertThat(links.getFirst().tags()).containsExactly("work");
    }

    @Test
    void addLinkMapsAlreadyExistsStatus() throws IOException {
        server = startServer(new ScrapperServiceGrpc.ScrapperServiceImplBase() {
            @Override
            public void addLink(AddLinkRequest request, StreamObserver<Link> responseObserver) {
                responseObserver.onError(Status.ALREADY_EXISTS.asRuntimeException());
            }
        });
        GrpcScrapperLinkGateway gateway = new GrpcScrapperLinkGateway(createClient(server.getPort()));

        assertThatThrownBy(() ->
                        gateway.addLink(1L, new AddScrapperLinkCommand("https://github.com/a/b", List.of(), List.of())))
                .isInstanceOf(ScrapperConflictException.class);
    }

    @Test
    void removeLinkMapsNotFoundStatus() throws IOException {
        server = startServer(new ScrapperServiceGrpc.ScrapperServiceImplBase() {
            @Override
            public void removeLink(RemoveLinkRequest request, StreamObserver<Link> responseObserver) {
                responseObserver.onError(Status.NOT_FOUND.asRuntimeException());
            }
        });
        GrpcScrapperLinkGateway gateway = new GrpcScrapperLinkGateway(createClient(server.getPort()));

        assertThatThrownBy(() -> gateway.removeLink(1L, "https://github.com/a/b"))
                .isInstanceOf(ScrapperNotFoundException.class);
    }

    @Test
    void listLinksMapsOtherStatusToUnavailable() throws IOException {
        server = startServer(new ScrapperServiceGrpc.ScrapperServiceImplBase() {
            @Override
            public void listLinks(ListLinksRequest request, StreamObserver<ListLinksResponse> responseObserver) {
                responseObserver.onError(Status.INTERNAL.asRuntimeException());
            }
        });
        GrpcScrapperLinkGateway gateway = new GrpcScrapperLinkGateway(createClient(server.getPort()));

        assertThatThrownBy(() -> gateway.listLinks(1L)).isInstanceOf(ScrapperUnavailableException.class);
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
        properties.setGrpcDeadline(Duration.ofSeconds(10));
        ResilienceProperties resilienceProperties = new ResilienceProperties();
        resilienceProperties.retry().setMaxAttempts(3);
        resilienceProperties.retry().setBackoff(Duration.ofMillis(1));
        resilienceProperties.circuitBreaker().setMinimumNumberOfCalls(10);
        resilienceProperties.circuitBreaker().setSlidingWindowSize(10);
        client = new GrpcScrapperClient(properties, resilienceProperties, new BotLogger());
        return client;
    }
}
