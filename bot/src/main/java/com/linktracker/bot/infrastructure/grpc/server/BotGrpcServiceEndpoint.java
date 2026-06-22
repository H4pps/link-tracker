package com.linktracker.bot.infrastructure.grpc.server;

import com.linktracker.bot.application.update.BotUpdateUseCase;
import com.linktracker.bot.application.update.LinkUpdateCommand;
import com.linktracker.grpc.Ack;
import com.linktracker.grpc.BotServiceGrpc;
import com.linktracker.grpc.LinkUpdateRequest;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * gRPC endpoint exposing bot update ingestion API.
 */
@Component
@RequiredArgsConstructor
public class BotGrpcServiceEndpoint extends BotServiceGrpc.BotServiceImplBase {

    private final BotUpdateUseCase botUpdateUseCase;

    /**
     * Accepts update payload from scrapper over gRPC.
     *
     * @param request gRPC request payload
     * @param responseObserver gRPC response observer
     */
    @Override
    public void sendUpdate(LinkUpdateRequest request, StreamObserver<Ack> responseObserver) {
        try {
            validate(request);
            botUpdateUseCase.processLinkUpdate(new LinkUpdateCommand(
                    request.getId(),
                    request.getUrl(),
                    request.getDescription(),
                    List.copyOf(request.getTgChatIdsList())));
            responseObserver.onNext(Ack.newBuilder().setAccepted(true).build());
            responseObserver.onCompleted();
        } catch (IllegalArgumentException exception) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription(exception.getMessage())
                    .asRuntimeException());
        } catch (RuntimeException exception) {
            responseObserver.onError(
                    Status.INTERNAL.withDescription("Failed to process update").asRuntimeException());
        }
    }

    private void validate(LinkUpdateRequest request) {
        if (request.getId() <= 0) {
            throw new IllegalArgumentException("id must be positive");
        }
        if (request.getUrl().isBlank()) {
            throw new IllegalArgumentException("url must not be blank");
        }
        if (request.getTgChatIdsCount() == 0) {
            throw new IllegalArgumentException("tg_chat_ids must not be empty");
        }
        if (request.getTgChatIdsList().stream().anyMatch(chatId -> chatId == null || chatId <= 0)) {
            throw new IllegalArgumentException("tg_chat_ids must contain only positive values");
        }
    }
}
