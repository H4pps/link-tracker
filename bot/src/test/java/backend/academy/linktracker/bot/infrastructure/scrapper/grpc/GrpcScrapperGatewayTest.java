package backend.academy.linktracker.bot.infrastructure.scrapper.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import backend.academy.linktracker.bot.application.scrapper.AddScrapperLinkCommand;
import backend.academy.linktracker.bot.application.scrapper.exception.ScrapperConflictException;
import backend.academy.linktracker.bot.application.scrapper.exception.ScrapperNotFoundException;
import backend.academy.linktracker.bot.application.scrapper.exception.ScrapperUnavailableException;
import backend.academy.linktracker.bot.logging.BotLogger;
import backend.academy.linktracker.bot.properties.ScrapperProperties;
import backend.academy.linktracker.grpc.Ack;
import backend.academy.linktracker.grpc.AddLinkRequest;
import backend.academy.linktracker.grpc.ChatRequest;
import backend.academy.linktracker.grpc.Link;
import backend.academy.linktracker.grpc.ListLinksRequest;
import backend.academy.linktracker.grpc.ListLinksResponse;
import backend.academy.linktracker.grpc.RemoveLinkRequest;
import backend.academy.linktracker.grpc.ScrapperServiceGrpc;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class GrpcScrapperGatewayTest {

    private Server server;
    private GrpcScrapperGateway gateway;

    @AfterEach
    void tearDown() {
        if (gateway != null) {
            gateway.shutdown();
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

        gateway = createGateway(server.getPort());

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
        gateway = createGateway(server.getPort());

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
        gateway = createGateway(server.getPort());

        assertThatThrownBy(() -> gateway.removeLink(1L, "https://github.com/a/b"))
                .isInstanceOf(ScrapperNotFoundException.class);
    }

    @Test
    void registerChatMapsUnavailableStatus() throws IOException {
        server = startServer(new ScrapperServiceGrpc.ScrapperServiceImplBase() {
            @Override
            public void registerChat(ChatRequest request, StreamObserver<Ack> responseObserver) {
                responseObserver.onError(Status.UNAVAILABLE.asRuntimeException());
            }
        });
        gateway = createGateway(server.getPort());

        assertThatThrownBy(() -> gateway.registerChat(1L)).isInstanceOf(ScrapperUnavailableException.class);
    }

    private Server startServer(ScrapperServiceGrpc.ScrapperServiceImplBase service) throws IOException {
        Server localServer = ServerBuilder.forPort(0).addService(service).build();
        localServer.start();
        return localServer;
    }

    private GrpcScrapperGateway createGateway(int port) {
        ScrapperProperties properties = new ScrapperProperties();
        properties.setGrpcHost("localhost");
        properties.setGrpcPort(port);
        properties.setGrpcDeadline(Duration.ofSeconds(1));
        return new GrpcScrapperGateway(properties, new BotLogger());
    }
}
