package com.linktracker.scrapper.infrastructure.grpc.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.linktracker.grpc.AddLinkRequest;
import com.linktracker.grpc.ChatRequest;
import com.linktracker.grpc.ListLinksRequest;
import com.linktracker.grpc.ListTagsRequest;
import com.linktracker.grpc.RenameTagRequest;
import com.linktracker.grpc.ScrapperServiceGrpc;
import com.linktracker.grpc.TagIdRequest;
import com.linktracker.grpc.TagNameRequest;
import com.linktracker.scrapper.application.chat.ScrapperChatUseCase;
import com.linktracker.scrapper.application.link.LinkView;
import com.linktracker.scrapper.application.link.ScrapperLinkUseCase;
import com.linktracker.scrapper.application.pagination.RepositoryPageRequest;
import com.linktracker.scrapper.application.tag.TagUseCase;
import com.linktracker.scrapper.domain.exception.AlreadyExistsException;
import com.linktracker.scrapper.domain.exception.ConflictException;
import com.linktracker.scrapper.domain.exception.NotFoundException;
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

    @Mock
    private TagUseCase tagUseCase;

    private Server server;
    private ManagedChannel channel;
    private ScrapperServiceGrpc.ScrapperServiceBlockingStub stub;

    @BeforeEach
    void setUp() throws Exception {
        server = ServerBuilder.forPort(0)
                .addService(new ScrapperGrpcServiceEndpoint(chatUseCase, linkUseCase, tagUseCase))
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

    @Test
    void listLinksWithOmittedLimitUsesUnpagedUseCaseCall() {
        when(linkUseCase.listLinks(1L))
                .thenReturn(List.of(new LinkView(10L, "https://github.com/a/b", List.of("x"), List.of())));

        var response = stub.listLinks(
                ListLinksRequest.newBuilder().setChatId(1L).setOffset(5L).build());

        assertThat(response.getSize()).isEqualTo(1);
        verify(linkUseCase).listLinks(1L);
    }

    @Test
    void listLinksMapsLimitAndOffsetToPagedUseCaseCall() {
        when(linkUseCase.listLinks(1L, new RepositoryPageRequest(2, 3)))
                .thenReturn(List.of(new LinkView(11L, "https://github.com/a/b", List.of("x"), List.of("f"))));

        var response = stub.listLinks(ListLinksRequest.newBuilder()
                .setChatId(1L)
                .setLimit(2)
                .setOffset(3)
                .build());

        assertThat(response.getSize()).isEqualTo(1);
        assertThat(response.getLinksList()).hasSize(1);
        assertThat(response.getLinks(0).getId()).isEqualTo(11L);
        verify(linkUseCase).listLinks(1L, new RepositoryPageRequest(2, 3));
    }

    @Test
    void createTagMapsUseCaseResponse() {
        when(tagUseCase.createTag("infra")).thenReturn(new com.linktracker.scrapper.domain.model.Tag(7L, "infra"));

        var response =
                stub.createTag(TagNameRequest.newBuilder().setName("infra").build());

        assertThat(response.getId()).isEqualTo(7L);
        assertThat(response.getName()).isEqualTo("infra");
        verify(tagUseCase).createTag("infra");
    }

    @Test
    void listTagsMapsLimitAndOffsetToPagedUseCaseCall() {
        when(tagUseCase.listTags(new RepositoryPageRequest(2, 3)))
                .thenReturn(List.of(new com.linktracker.scrapper.domain.model.Tag(9L, "ops")));

        var response = stub.listTags(
                ListTagsRequest.newBuilder().setLimit(2).setOffset(3).build());

        assertThat(response.getSize()).isEqualTo(1);
        assertThat(response.getTags(0).getId()).isEqualTo(9L);
        verify(tagUseCase).listTags(new RepositoryPageRequest(2, 3));
    }

    @Test
    void listTagsMapsNegativePagingToInvalidArgument() {
        assertThatThrownBy(() ->
                        stub.listTags(ListTagsRequest.newBuilder().setLimit(-1).build()))
                .isInstanceOf(StatusRuntimeException.class)
                .extracting(
                        error -> ((StatusRuntimeException) error).getStatus().getCode())
                .isEqualTo(Status.Code.INVALID_ARGUMENT);

        verify(tagUseCase, never()).listTags(any());
    }

    @Test
    void getTagMapsNotFoundToGrpcStatus() {
        when(tagUseCase.getTag(77L)).thenThrow(new NotFoundException("missing"));

        assertThatThrownBy(() ->
                        stub.getTag(TagIdRequest.newBuilder().setTagId(77L).build()))
                .isInstanceOf(StatusRuntimeException.class)
                .extracting(
                        error -> ((StatusRuntimeException) error).getStatus().getCode())
                .isEqualTo(Status.Code.NOT_FOUND);
    }

    @Test
    void renameTagMapsDuplicateAndInvalidNameToGrpcStatus() {
        when(tagUseCase.renameTag(10L, "dup")).thenThrow(new AlreadyExistsException("exists"));
        when(tagUseCase.renameTag(10L, " ")).thenThrow(new IllegalArgumentException("blank"));

        assertThatThrownBy(() -> stub.renameTag(RenameTagRequest.newBuilder()
                        .setTagId(10L)
                        .setName("dup")
                        .build()))
                .isInstanceOf(StatusRuntimeException.class)
                .extracting(
                        error -> ((StatusRuntimeException) error).getStatus().getCode())
                .isEqualTo(Status.Code.ALREADY_EXISTS);

        assertThatThrownBy(() -> stub.renameTag(
                        RenameTagRequest.newBuilder().setTagId(10L).setName(" ").build()))
                .isInstanceOf(StatusRuntimeException.class)
                .extracting(
                        error -> ((StatusRuntimeException) error).getStatus().getCode())
                .isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    @Test
    void deleteTagMapsAttachedConflictToFailedPrecondition() {
        doThrow(new ConflictException("attached")).when(tagUseCase).deleteTag(33L);

        assertThatThrownBy(() ->
                        stub.deleteTag(TagIdRequest.newBuilder().setTagId(33L).build()))
                .isInstanceOf(StatusRuntimeException.class)
                .extracting(
                        error -> ((StatusRuntimeException) error).getStatus().getCode())
                .isEqualTo(Status.Code.FAILED_PRECONDITION);
    }
}
