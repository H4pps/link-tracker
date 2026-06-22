package com.linktracker.bot.infrastructure.grpc.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.linktracker.bot.application.update.BotUpdateUseCase;
import com.linktracker.bot.application.update.LinkUpdateCommand;
import com.linktracker.grpc.BotServiceGrpc;
import com.linktracker.grpc.LinkUpdateRequest;
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
import org.mockito.ArgumentCaptor;
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
                .addTgChatIds(200)
                .build());

        ArgumentCaptor<LinkUpdateCommand> commandCaptor = ArgumentCaptor.forClass(LinkUpdateCommand.class);
        assertThat(response.getAccepted()).isTrue();
        verify(botUpdateUseCase).processLinkUpdate(commandCaptor.capture());
        LinkUpdateCommand command = commandCaptor.getValue();
        assertThat(command.id()).isEqualTo(1L);
        assertThat(command.url()).isEqualTo("https://github.com/a/b");
        assertThat(command.description()).isEqualTo("changed");
        assertThat(command.tgChatIds()).containsExactly(100L, 200L);
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

    @Test
    void sendUpdatePreservesMultilineRichDescriptionInUseCaseCommand() {
        String description = String.join(
                "\n",
                "Update type: GitHub Pull Request",
                "Title: feat: ship scheduler metadata payload",
                "Author: octocat",
                "Created at: 2024-05-22T11:12:13Z",
                "Preview: keeps line breaks from scrapper");

        var response = stub.sendUpdate(LinkUpdateRequest.newBuilder()
                .setId(11)
                .setUrl("https://github.com/acme/platform")
                .setDescription(description)
                .addTgChatIds(100)
                .addTgChatIds(200)
                .build());

        ArgumentCaptor<LinkUpdateCommand> commandCaptor = ArgumentCaptor.forClass(LinkUpdateCommand.class);
        verify(botUpdateUseCase).processLinkUpdate(commandCaptor.capture());
        assertThat(response.getAccepted()).isTrue();
        assertThat(commandCaptor.getValue().description()).isEqualTo(description);
    }
}
