package backend.academy.linktracker.bot.infrastructure.scrapper.grpc;

import backend.academy.linktracker.bot.application.scrapper.exception.ScrapperConflictException;
import backend.academy.linktracker.bot.application.scrapper.exception.ScrapperNotFoundException;
import backend.academy.linktracker.bot.application.scrapper.exception.ScrapperUnavailableException;
import backend.academy.linktracker.bot.infrastructure.resilience.GrpcResiliencePredicates;
import backend.academy.linktracker.bot.infrastructure.resilience.ResilientCallExecutor;
import backend.academy.linktracker.bot.logging.BotLogger;
import backend.academy.linktracker.bot.properties.ResilienceProperties;
import backend.academy.linktracker.bot.properties.ScrapperProperties;
import backend.academy.linktracker.grpc.ScrapperServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Shared gRPC client infrastructure for bot-side scrapper adapters.
 */
@Component
@ConditionalOnProperty(prefix = "app.scrapper", name = "mode", havingValue = "grpc", matchIfMissing = true)
public class GrpcScrapperClient {

    private final ManagedChannel channel;
    private final ScrapperServiceGrpc.ScrapperServiceBlockingStub blockingStub;
    private final ScrapperProperties scrapperProperties;
    private final ResilientCallExecutor resilientCallExecutor;
    private final BotLogger botLogger;

    /**
     * Creates managed channel and base blocking stub.
     *
     * @param scrapperProperties scrapper transport properties
     * @param botLogger structured logger
     */
    public GrpcScrapperClient(
            ScrapperProperties scrapperProperties, ResilienceProperties resilienceProperties, BotLogger botLogger) {
        this.scrapperProperties = scrapperProperties;
        this.resilientCallExecutor = new ResilientCallExecutor(resilienceProperties);
        this.botLogger = botLogger;
        this.channel = ManagedChannelBuilder.forAddress(
                        scrapperProperties.getGrpcHost(), scrapperProperties.getGrpcPort())
                .usePlaintext()
                .build();
        this.blockingStub = ScrapperServiceGrpc.newBlockingStub(channel);
    }

    /**
     * Executes a gRPC call with standard logging, deadline and exception mapping.
     *
     * @param operation scrapper operation name
     * @param chatId telegram chat identifier
     * @param url tracked URL when available
     * @param call callback using deadline-aware blocking stub
     * @return call result
     * @param <T> call result type
     */
    public <T> T execute(String operation, long chatId, String url, GrpcCall<T> call) {
        try {
            botLogger.logScrapperRequest(operation, chatId, url);
            return resilientCallExecutor.execute(
                    "scrapper-grpc",
                    () -> call.execute(withDeadline()),
                    GrpcResiliencePredicates::isRetryableFailure,
                    GrpcResiliencePredicates::isCircuitBreakerFailure);
        } catch (StatusRuntimeException exception) {
            String code = exception.getStatus().getCode().name();
            botLogger.logScrapperRequestFailed(operation, chatId, url, 0, code);
            throw mapException(exception);
        } catch (RuntimeException exception) {
            String code = exception.getClass().getSimpleName();
            botLogger.logScrapperRequestFailed(operation, chatId, url, 0, code);
            throw new ScrapperUnavailableException("Scrapper gRPC resilience error: " + code, exception);
        }
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

    private RuntimeException mapException(StatusRuntimeException exception) {
        if (exception.getStatus().getCode() == Status.Code.NOT_FOUND) {
            return new ScrapperNotFoundException("Scrapper returned not found", exception);
        }
        if (exception.getStatus().getCode() == Status.Code.ALREADY_EXISTS) {
            return new ScrapperConflictException("Scrapper returned conflict", exception);
        }
        return new ScrapperUnavailableException(
                "Scrapper gRPC error: " + exception.getStatus().getCode().name(), exception);
    }

    @FunctionalInterface
    public interface GrpcCall<T> {
        T execute(ScrapperServiceGrpc.ScrapperServiceBlockingStub blockingStub);
    }
}
