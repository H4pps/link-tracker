package backend.academy.linktracker.bot.infrastructure.scrapper.grpc;

import backend.academy.linktracker.bot.application.scrapper.AddScrapperLinkCommand;
import backend.academy.linktracker.bot.application.scrapper.ScrapperGateway;
import backend.academy.linktracker.bot.application.scrapper.ScrapperLinkView;
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
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * gRPC implementation of bot-side scrapper gateway.
 */
@Component
@ConditionalOnProperty(prefix = "app.scrapper", name = "mode", havingValue = "grpc", matchIfMissing = true)
public class GrpcScrapperGateway implements ScrapperGateway {

    private final ManagedChannel channel;
    private final ScrapperServiceGrpc.ScrapperServiceBlockingStub blockingStub;
    private final ScrapperProperties scrapperProperties;
    private final BotLogger botLogger;

    /**
     * Creates gRPC gateway with managed channel.
     *
     * @param scrapperProperties transport properties
     * @param botLogger structured logger
     */
    public GrpcScrapperGateway(ScrapperProperties scrapperProperties, BotLogger botLogger) {
        this.scrapperProperties = scrapperProperties;
        this.botLogger = botLogger;
        this.channel = ManagedChannelBuilder.forAddress(
                        scrapperProperties.getGrpcHost(), scrapperProperties.getGrpcPort())
                .usePlaintext()
                .build();
        this.blockingStub = ScrapperServiceGrpc.newBlockingStub(channel);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void registerChat(long chatId) {
        executeVoid("register-chat", chatId, null, () -> withDeadline()
                .registerChat(ChatRequest.newBuilder().setChatId(chatId).build()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteChat(long chatId) {
        executeVoid("delete-chat", chatId, null, () -> withDeadline()
                .deleteChat(ChatRequest.newBuilder().setChatId(chatId).build()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<ScrapperLinkView> listLinks(long chatId) {
        ListLinksResponse response = execute("list-links", chatId, null, () -> withDeadline()
                .listLinks(ListLinksRequest.newBuilder().setChatId(chatId).build()));
        return response.getLinksList().stream().map(this::toView).toList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ScrapperLinkView addLink(long chatId, AddScrapperLinkCommand command) {
        Link response = execute("add-link", chatId, command.url(), () -> withDeadline()
                .addLink(AddLinkRequest.newBuilder()
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
        Link response = execute("remove-link", chatId, url, () -> withDeadline()
                .removeLink(RemoveLinkRequest.newBuilder()
                        .setChatId(chatId)
                        .setUrl(url)
                        .build()));
        return toView(response);
    }

    /**
     * Closes underlying gRPC channel.
     */
    @PreDestroy
    public void shutdown() {
        channel.shutdown();
        try {
            channel.awaitTermination(3, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private ScrapperServiceGrpc.ScrapperServiceBlockingStub withDeadline() {
        return blockingStub.withDeadlineAfter(
                scrapperProperties.getGrpcDeadline().toMillis(), TimeUnit.MILLISECONDS);
    }

    private ScrapperLinkView toView(Link response) {
        return new ScrapperLinkView(
                response.getId(), response.getUrl(), response.getTagsList(), response.getFiltersList());
    }

    private <T> T execute(String operation, long chatId, String url, ThrowingSupplier<T> call) {
        try {
            botLogger.logScrapperRequest(operation, chatId, url);
            return call.get();
        } catch (StatusRuntimeException exception) {
            String code = exception.getStatus().getCode().name();
            botLogger.logScrapperRequestFailed(operation, chatId, url, 0, code);
            if (exception.getStatus().getCode() == io.grpc.Status.Code.NOT_FOUND) {
                throw new ScrapperNotFoundException("Scrapper returned not found", exception);
            }
            if (exception.getStatus().getCode() == io.grpc.Status.Code.ALREADY_EXISTS) {
                throw new ScrapperConflictException("Scrapper returned conflict", exception);
            }
            throw new ScrapperUnavailableException("Scrapper gRPC error: " + code, exception);
        }
    }

    private void executeVoid(String operation, long chatId, String url, ThrowingSupplier<Ack> call) {
        execute(operation, chatId, url, call);
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get();
    }
}
