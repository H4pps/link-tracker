package backend.academy.linktracker.scrapper.infrastructure.grpc.server;

import backend.academy.linktracker.grpc.Ack;
import backend.academy.linktracker.grpc.AddLinkRequest;
import backend.academy.linktracker.grpc.ChatRequest;
import backend.academy.linktracker.grpc.Link;
import backend.academy.linktracker.grpc.ListLinksRequest;
import backend.academy.linktracker.grpc.ListLinksResponse;
import backend.academy.linktracker.grpc.RemoveLinkRequest;
import backend.academy.linktracker.grpc.ScrapperServiceGrpc;
import backend.academy.linktracker.scrapper.application.chat.ScrapperChatUseCase;
import backend.academy.linktracker.scrapper.application.link.AddLinkCommand;
import backend.academy.linktracker.scrapper.application.link.LinkView;
import backend.academy.linktracker.scrapper.application.link.RemoveLinkCommand;
import backend.academy.linktracker.scrapper.application.link.ScrapperLinkUseCase;
import backend.academy.linktracker.scrapper.application.repository.RepositoryPageRequest;
import backend.academy.linktracker.scrapper.domain.exception.AlreadyExistsException;
import backend.academy.linktracker.scrapper.domain.exception.NotFoundException;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * gRPC endpoint exposing scrapper chat/link operations.
 */
@Component
@RequiredArgsConstructor
public class ScrapperGrpcServiceEndpoint extends ScrapperServiceGrpc.ScrapperServiceImplBase {

    private final ScrapperChatUseCase scrapperChatUseCase;
    private final ScrapperLinkUseCase scrapperLinkUseCase;

    /**
     * Registers chat over gRPC.
     */
    @Override
    public void registerChat(ChatRequest request, StreamObserver<Ack> responseObserver) {
        handleAck(responseObserver, () -> scrapperChatUseCase.registerChat(request.getChatId()));
    }

    /**
     * Deletes chat over gRPC.
     */
    @Override
    public void deleteChat(ChatRequest request, StreamObserver<Ack> responseObserver) {
        handleAck(responseObserver, () -> scrapperChatUseCase.deleteChat(request.getChatId()));
    }

    /**
     * Lists chat links over gRPC.
     */
    @Override
    public void listLinks(ListLinksRequest request, StreamObserver<ListLinksResponse> responseObserver) {
        try {
            List<LinkView> linkViews = request.getLimit() > 0
                    ? scrapperLinkUseCase.listLinks(
                            request.getChatId(), new RepositoryPageRequest(request.getLimit(), request.getOffset()))
                    : scrapperLinkUseCase.listLinks(request.getChatId());
            List<Link> links = linkViews.stream()
                    .map(this::toGrpcLink)
                    .toList();
            responseObserver.onNext(ListLinksResponse.newBuilder()
                    .addAllLinks(links)
                    .setSize(links.size())
                    .build());
            responseObserver.onCompleted();
        } catch (NotFoundException exception) {
            responseObserver.onError(mapToStatus(exception).asRuntimeException());
        } catch (RuntimeException exception) {
            responseObserver.onError(
                    Status.INTERNAL.withDescription("list-links failed").asRuntimeException());
        }
    }

    /**
     * Adds link over gRPC.
     */
    @Override
    public void addLink(AddLinkRequest request, StreamObserver<Link> responseObserver) {
        try {
            LinkView created = scrapperLinkUseCase.addLink(
                    request.getChatId(),
                    new AddLinkCommand(
                            request.getUrl(),
                            List.copyOf(request.getTagsList()),
                            List.copyOf(request.getFiltersList())));
            responseObserver.onNext(toGrpcLink(created));
            responseObserver.onCompleted();
        } catch (AlreadyExistsException | NotFoundException exception) {
            responseObserver.onError(mapToStatus(exception).asRuntimeException());
        } catch (RuntimeException exception) {
            responseObserver.onError(
                    Status.INTERNAL.withDescription("add-link failed").asRuntimeException());
        }
    }

    /**
     * Removes link over gRPC.
     */
    @Override
    public void removeLink(RemoveLinkRequest request, StreamObserver<Link> responseObserver) {
        try {
            LinkView removed =
                    scrapperLinkUseCase.removeLink(request.getChatId(), new RemoveLinkCommand(request.getUrl()));
            responseObserver.onNext(toGrpcLink(removed));
            responseObserver.onCompleted();
        } catch (NotFoundException exception) {
            responseObserver.onError(mapToStatus(exception).asRuntimeException());
        } catch (RuntimeException exception) {
            responseObserver.onError(
                    Status.INTERNAL.withDescription("remove-link failed").asRuntimeException());
        }
    }

    private Link toGrpcLink(LinkView view) {
        return Link.newBuilder()
                .setId(view.id())
                .setUrl(view.url())
                .addAllTags(view.tags())
                .addAllFilters(view.filters())
                .build();
    }

    private void handleAck(StreamObserver<Ack> responseObserver, Runnable action) {
        try {
            action.run();
            responseObserver.onNext(Ack.newBuilder().setAccepted(true).build());
            responseObserver.onCompleted();
        } catch (AlreadyExistsException | NotFoundException exception) {
            responseObserver.onError(mapToStatus(exception).asRuntimeException());
        } catch (RuntimeException exception) {
            responseObserver.onError(
                    Status.INTERNAL.withDescription("chat operation failed").asRuntimeException());
        }
    }

    private Status mapToStatus(RuntimeException exception) {
        if (exception instanceof AlreadyExistsException) {
            return Status.ALREADY_EXISTS.withDescription(exception.getMessage());
        }
        if (exception instanceof NotFoundException) {
            return Status.NOT_FOUND.withDescription(exception.getMessage());
        }
        return Status.INTERNAL.withDescription(exception.getMessage());
    }
}
