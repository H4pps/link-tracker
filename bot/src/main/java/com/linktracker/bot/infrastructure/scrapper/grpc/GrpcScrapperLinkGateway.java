package com.linktracker.bot.infrastructure.scrapper.grpc;

import com.linktracker.bot.application.scrapper.ScrapperLinkGateway;
import com.linktracker.bot.application.scrapper.command.AddScrapperLinkCommand;
import com.linktracker.bot.application.scrapper.view.ScrapperLinkView;
import com.linktracker.grpc.AddLinkRequest;
import com.linktracker.grpc.Link;
import com.linktracker.grpc.ListLinksRequest;
import com.linktracker.grpc.ListLinksResponse;
import com.linktracker.grpc.RemoveLinkRequest;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * gRPC implementation of bot-side scrapper link gateway.
 */
@Component
@ConditionalOnProperty(prefix = "app.scrapper", name = "mode", havingValue = "grpc", matchIfMissing = true)
public class GrpcScrapperLinkGateway implements ScrapperLinkGateway {

    private final GrpcScrapperClient client;

    /**
     * Creates gateway using shared gRPC client.
     *
     * @param client shared scrapper gRPC client
     */
    public GrpcScrapperLinkGateway(GrpcScrapperClient client) {
        this.client = client;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<ScrapperLinkView> listLinks(long chatId) {
        ListLinksResponse response = client.execute(
                "list-links",
                chatId,
                null,
                blockingStub -> blockingStub.listLinks(
                        ListLinksRequest.newBuilder().setChatId(chatId).build()));
        return response.getLinksList().stream().map(this::toView).toList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ScrapperLinkView addLink(long chatId, AddScrapperLinkCommand command) {
        Link response = client.execute(
                "add-link",
                chatId,
                command.url(),
                blockingStub -> blockingStub.addLink(AddLinkRequest.newBuilder()
                        .setChatId(chatId)
                        .setUrl(command.url())
                        .addAllTags(command.tags())
                        .addAllFilters(command.filters())
                        .build()));
        return toView(response);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ScrapperLinkView removeLink(long chatId, String url) {
        Link response = client.execute(
                "remove-link",
                chatId,
                url,
                blockingStub -> blockingStub.removeLink(RemoveLinkRequest.newBuilder()
                        .setChatId(chatId)
                        .setUrl(url)
                        .build()));
        return toView(response);
    }

    private ScrapperLinkView toView(Link response) {
        return new ScrapperLinkView(
                response.getId(), response.getUrl(), response.getTagsList(), response.getFiltersList());
    }
}
