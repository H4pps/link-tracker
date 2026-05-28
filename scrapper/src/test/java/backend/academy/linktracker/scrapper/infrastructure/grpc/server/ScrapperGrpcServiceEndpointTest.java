package backend.academy.linktracker.scrapper.infrastructure.grpc.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import backend.academy.linktracker.grpc.AddLinkRequest;
import backend.academy.linktracker.grpc.ChatRequest;
import backend.academy.linktracker.grpc.ListLinksRequest;
import backend.academy.linktracker.grpc.ScrapperServiceGrpc;
import backend.academy.linktracker.scrapper.application.chat.ScrapperChatUseCase;
import backend.academy.linktracker.scrapper.application.link.LinkView;
import backend.academy.linktracker.scrapper.application.link.ScrapperLinkUseCase;
import backend.academy.linktracker.scrapper.domain.exception.AlreadyExistsException;
import backend.academy.linktracker.scrapper.domain.exception.NotFoundException;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScrapperGrpcServiceEndpointTest {

    @Mock
    private ScrapperChatUseCase chatUseCase;

    @Mock
    private ScrapperLinkUseCase linkUseCase;

    private Server server;
    private ManagedChannel channel;
    private ScrapperServiceGrpc.ScrapperServiceBlockingStub stub;

    @BeforeEach
    void setUp() throws Exception {
        server = ServerBuilder.forPort(0)
                .addService(new ScrapperGrpcServiceEndpoint(chatUseCase, linkUseCase))
                .build()
                .start();
        channel = ManagedChannelBuilder.forAddress("localhost", server.getPort())
                .usePlaintext()
                .build();
        stub = ScrapperServiceGrpc.newBlockingStub(channel);
    }

    @AfterEach
    void tearDown() {
        channel.shutdownNow();
        server.shutdownNow();
    }

    @Test
    void addLinkMapsUseCaseResponse() {
        when(linkUseCase.addLink(anyLong(), any()))
                .thenReturn(new LinkView(5L, "https://github.com/a/b", List.of("work"), List.of()));

        var response = stub.addLink(AddLinkRequest.newBuilder()
                .setChatId(1)
                .setUrl("https://github.com/a/b")
                .addTags("work")
                .build());

        assertThat(response.getId()).isEqualTo(5L);
        assertThat(response.getUrl()).isEqualTo("https://github.com/a/b");
        verify(linkUseCase).addLink(anyLong(), any());
    }

    @Test
    void registerChatMapsAlreadyExistsToGrpcStatus() {
        doThrow(new AlreadyExistsException("exists")).when(chatUseCase).registerChat(1L);

        assertThatThrownBy(() ->
                        stub.registerChat(ChatRequest.newBuilder().setChatId(1L).build()))
                .isInstanceOf(StatusRuntimeException.class)
                .extracting(
                        error -> ((StatusRuntimeException) error).getStatus().getCode())
                .isEqualTo(Status.Code.ALREADY_EXISTS);
    }

    @Test
    void listLinksMapsNotFoundToGrpcStatus() {
        when(linkUseCase.listLinks(1L)).thenThrow(new NotFoundException("missing"));

        assertThatThrownBy(() -> stub.listLinks(
                        ListLinksRequest.newBuilder().setChatId(1L).build()))
                .isInstanceOf(StatusRuntimeException.class)
                .extracting(
                        error -> ((StatusRuntimeException) error).getStatus().getCode())
                .isEqualTo(Status.Code.NOT_FOUND);
    }
}
