package backend.academy.linktracker.bot.infrastructure.scrapper.grpc;

import backend.academy.linktracker.bot.application.scrapper.ScrapperChatGateway;
import backend.academy.linktracker.grpc.ChatRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * gRPC implementation of bot-side scrapper chat gateway.
 */
@Component
@ConditionalOnProperty(prefix = "app.scrapper", name = "mode", havingValue = "grpc", matchIfMissing = true)
public class GrpcScrapperChatGateway implements ScrapperChatGateway {

    private final GrpcScrapperClient client;

    /**
     * Creates gateway using shared gRPC client.
     *
     * @param client shared scrapper gRPC client
     */
    public GrpcScrapperChatGateway(GrpcScrapperClient client) {
        this.client = client;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void registerChat(long chatId) {
        client.execute(
                "register-chat",
                chatId,
                null,
                blockingStub -> blockingStub.registerChat(
                        ChatRequest.newBuilder().setChatId(chatId).build()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteChat(long chatId) {
        client.execute(
                "delete-chat",
                chatId,
                null,
                blockingStub -> blockingStub.deleteChat(
                        ChatRequest.newBuilder().setChatId(chatId).build()));
    }
}
