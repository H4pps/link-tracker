package backend.academy.linktracker.scrapper.infrastructure.grpc.server;

import backend.academy.linktracker.grpc.Ack;
import backend.academy.linktracker.grpc.AddLinkRequest;
import backend.academy.linktracker.grpc.ChatRequest;
import backend.academy.linktracker.grpc.Link;
import backend.academy.linktracker.grpc.ListLinksRequest;
import backend.academy.linktracker.grpc.ListLinksResponse;
import backend.academy.linktracker.grpc.ListTagsRequest;
import backend.academy.linktracker.grpc.ListTagsResponse;
import backend.academy.linktracker.grpc.RemoveLinkRequest;
import backend.academy.linktracker.grpc.RenameTagRequest;
import backend.academy.linktracker.grpc.ScrapperServiceGrpc;
import backend.academy.linktracker.grpc.TagIdRequest;
import backend.academy.linktracker.grpc.TagNameRequest;
import backend.academy.linktracker.scrapper.application.chat.ScrapperChatUseCase;
import backend.academy.linktracker.scrapper.application.link.AddLinkCommand;
import backend.academy.linktracker.scrapper.application.link.LinkView;
import backend.academy.linktracker.scrapper.application.link.RemoveLinkCommand;
import backend.academy.linktracker.scrapper.application.link.ScrapperLinkUseCase;
import backend.academy.linktracker.scrapper.application.pagination.RepositoryPageRequest;
import backend.academy.linktracker.scrapper.application.tag.TagUseCase;
import backend.academy.linktracker.scrapper.domain.exception.AlreadyExistsException;
import backend.academy.linktracker.scrapper.domain.exception.ConflictException;
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
    private final TagUseCase tagUseCase;

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
            List<Link> links = linkViews.stream().map(this::toGrpcLink).toList();
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

    /**
     * Creates standalone tag over gRPC.
     */
    @Override
    public void createTag(
            TagNameRequest request, StreamObserver<backend.academy.linktracker.grpc.Tag> responseObserver) {
        try {
            backend.academy.linktracker.scrapper.domain.model.Tag created = tagUseCase.createTag(request.getName());
            responseObserver.onNext(toGrpcTag(created));
            responseObserver.onCompleted();
        } catch (AlreadyExistsException | IllegalArgumentException exception) {
            responseObserver.onError(mapToStatus(exception).asRuntimeException());
        } catch (RuntimeException exception) {
            responseObserver.onError(
                    Status.INTERNAL.withDescription("create-tag failed").asRuntimeException());
        }
    }

    /**
     * Reads standalone tag by id over gRPC.
     */
    @Override
    public void getTag(TagIdRequest request, StreamObserver<backend.academy.linktracker.grpc.Tag> responseObserver) {
        try {
            backend.academy.linktracker.scrapper.domain.model.Tag tag = tagUseCase.getTag(request.getTagId());
            responseObserver.onNext(toGrpcTag(tag));
            responseObserver.onCompleted();
        } catch (NotFoundException exception) {
            responseObserver.onError(mapToStatus(exception).asRuntimeException());
        } catch (RuntimeException exception) {
            responseObserver.onError(
                    Status.INTERNAL.withDescription("get-tag failed").asRuntimeException());
        }
    }

    /**
     * Reads standalone tag by name over gRPC.
     */
    @Override
    public void getTagByName(
            TagNameRequest request, StreamObserver<backend.academy.linktracker.grpc.Tag> responseObserver) {
        try {
            backend.academy.linktracker.scrapper.domain.model.Tag tag = tagUseCase.getTagByName(request.getName());
            responseObserver.onNext(toGrpcTag(tag));
            responseObserver.onCompleted();
        } catch (NotFoundException | IllegalArgumentException exception) {
            responseObserver.onError(mapToStatus(exception).asRuntimeException());
        } catch (RuntimeException exception) {
            responseObserver.onError(
                    Status.INTERNAL.withDescription("get-tag-by-name failed").asRuntimeException());
        }
    }

    /**
     * Lists standalone tags over gRPC.
     */
    @Override
    public void listTags(ListTagsRequest request, StreamObserver<ListTagsResponse> responseObserver) {
        try {
            RepositoryPageRequest pageRequest = toPageRequest(request.getLimit(), request.getOffset());
            List<backend.academy.linktracker.scrapper.domain.model.Tag> tags = tagUseCase.listTags(pageRequest);
            List<backend.academy.linktracker.grpc.Tag> responseTags =
                    tags.stream().map(this::toGrpcTag).toList();
            responseObserver.onNext(ListTagsResponse.newBuilder()
                    .addAllTags(responseTags)
                    .setSize(responseTags.size())
                    .build());
            responseObserver.onCompleted();
        } catch (IllegalArgumentException exception) {
            responseObserver.onError(mapToStatus(exception).asRuntimeException());
        } catch (RuntimeException exception) {
            responseObserver.onError(
                    Status.INTERNAL.withDescription("list-tags failed").asRuntimeException());
        }
    }

    /**
     * Renames standalone tag over gRPC.
     */
    @Override
    public void renameTag(
            RenameTagRequest request, StreamObserver<backend.academy.linktracker.grpc.Tag> responseObserver) {
        try {
            backend.academy.linktracker.scrapper.domain.model.Tag renamed =
                    tagUseCase.renameTag(request.getTagId(), request.getName());
            responseObserver.onNext(toGrpcTag(renamed));
            responseObserver.onCompleted();
        } catch (AlreadyExistsException | NotFoundException | IllegalArgumentException exception) {
            responseObserver.onError(mapToStatus(exception).asRuntimeException());
        } catch (RuntimeException exception) {
            responseObserver.onError(
                    Status.INTERNAL.withDescription("rename-tag failed").asRuntimeException());
        }
    }

    /**
     * Deletes standalone tag over gRPC.
     */
    @Override
    public void deleteTag(TagIdRequest request, StreamObserver<Ack> responseObserver) {
        try {
            tagUseCase.deleteTag(request.getTagId());
            responseObserver.onNext(Ack.newBuilder().setAccepted(true).build());
            responseObserver.onCompleted();
        } catch (NotFoundException | ConflictException exception) {
            responseObserver.onError(mapToStatus(exception).asRuntimeException());
        } catch (RuntimeException exception) {
            responseObserver.onError(
                    Status.INTERNAL.withDescription("delete-tag failed").asRuntimeException());
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

    private backend.academy.linktracker.grpc.Tag toGrpcTag(backend.academy.linktracker.scrapper.domain.model.Tag tag) {
        return backend.academy.linktracker.grpc.Tag.newBuilder()
                .setId(tag.id())
                .setName(tag.name())
                .build();
    }

    private RepositoryPageRequest toPageRequest(int limit, long offset) {
        if (limit < 0 || offset < 0) {
            throw new IllegalArgumentException("Page limit and offset must be non-negative");
        }
        if (limit == 0 && offset == 0) {
            return RepositoryPageRequest.all();
        }
        return new RepositoryPageRequest(limit, offset);
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
        if (exception instanceof ConflictException) {
            return Status.FAILED_PRECONDITION.withDescription(exception.getMessage());
        }
        if (exception instanceof IllegalArgumentException) {
            return Status.INVALID_ARGUMENT.withDescription(exception.getMessage());
        }
        return Status.INTERNAL.withDescription(exception.getMessage());
    }
}
