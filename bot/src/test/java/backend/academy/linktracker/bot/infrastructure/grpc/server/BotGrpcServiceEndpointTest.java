package backend.academy.linktracker.bot.infrastructure.grpc.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import backend.academy.linktracker.bot.application.update.BotUpdateUseCase;
import backend.academy.linktracker.grpc.BotServiceGrpc;
import backend.academy.linktracker.grpc.LinkUpdateRequest;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BotGrpcServiceEndpointTest {

    @Mock
    private BotUpdateUseCase botUpdateUseCase;

    private Server server;
    private ManagedChannel channel;
    private BotServiceGrpc.BotServiceBlockingStub stub;

    @BeforeEach
    void setUp() throws Exception {
        server = ServerBuilder.forPort(0)
                .addService(new BotGrpcServiceEndpoint(botUpdateUseCase))
                .build()
                .start();
        channel = ManagedChannelBuilder.forAddress("localhost", server.getPort())
                .usePlaintext()
                .build();
        stub = BotServiceGrpc.newBlockingStub(channel);
    }

    @AfterEach
    void tearDown() {
        channel.shutdownNow();
        server.shutdownNow();
    }

    @Test
    void sendUpdateDelegatesToUseCaseOnValidPayload() {
        var response = stub.sendUpdate(LinkUpdateRequest.newBuilder()
                .setId(1)
                .setUrl("https://github.com/a/b")
                .setDescription("changed")
                .addTgChatIds(100)
                .build());

        assertThat(response.getAccepted()).isTrue();
        verify(botUpdateUseCase).processLinkUpdate(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void sendUpdateReturnsInvalidArgumentForInvalidPayload() {
        assertThatThrownBy(() -> stub.sendUpdate(
                        LinkUpdateRequest.newBuilder().setId(0).setUrl("").build()))
                .isInstanceOf(StatusRuntimeException.class)
                .extracting(
                        error -> ((StatusRuntimeException) error).getStatus().getCode())
                .isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    @Test
    void sendUpdateReturnsInternalWhenUseCaseThrows() {
        doThrow(new IllegalStateException("boom"))
                .when(botUpdateUseCase)
                .processLinkUpdate(org.mockito.ArgumentMatchers.any());

        assertThatThrownBy(() -> stub.sendUpdate(LinkUpdateRequest.newBuilder()
                        .setId(1)
                        .setUrl("https://github.com/a/b")
                        .addTgChatIds(10)
                        .build()))
                .isInstanceOf(StatusRuntimeException.class)
                .extracting(
                        error -> ((StatusRuntimeException) error).getStatus().getCode())
                .isEqualTo(Status.Code.INTERNAL);
    }
}
