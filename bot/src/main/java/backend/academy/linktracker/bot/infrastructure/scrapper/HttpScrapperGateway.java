package backend.academy.linktracker.bot.infrastructure.scrapper;

import backend.academy.linktracker.bot.application.scrapper.AddScrapperLinkCommand;
import backend.academy.linktracker.bot.application.scrapper.ScrapperGateway;
import backend.academy.linktracker.bot.application.scrapper.ScrapperLinkView;
import backend.academy.linktracker.bot.application.scrapper.exception.ScrapperConflictException;
import backend.academy.linktracker.bot.application.scrapper.exception.ScrapperNotFoundException;
import backend.academy.linktracker.bot.application.scrapper.exception.ScrapperUnavailableException;
import backend.academy.linktracker.bot.infrastructure.scrapper.dto.ScrapperAddLinkRequest;
import backend.academy.linktracker.bot.infrastructure.scrapper.dto.ScrapperLinkResponse;
import backend.academy.linktracker.bot.infrastructure.scrapper.dto.ScrapperListLinksResponse;
import backend.academy.linktracker.bot.infrastructure.scrapper.dto.ScrapperRemoveLinkRequest;
import backend.academy.linktracker.bot.logging.BotLogger;
import backend.academy.linktracker.bot.properties.ScrapperProperties;
import java.util.List;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * HTTP implementation of bot-side scrapper gateway using {@link RestClient}.
 */
@Component
public class HttpScrapperGateway implements ScrapperGateway {

    private static final String CHAT_ENDPOINT_TEMPLATE = "/tg-chat/{id}";
    private static final String LINKS_ENDPOINT = "/links";
    private static final String CHAT_HEADER = "Tg-Chat-Id";

    private final RestClient restClient;
    private final BotLogger botLogger;

    /**
     * Creates gateway with configured rest client instance.
     *
     * @param restClientBuilder Spring rest client builder
     * @param scrapperProperties scrapper HTTP properties
     * @param botLogger structured logger
     */
    public HttpScrapperGateway(
            RestClient.Builder restClientBuilder, ScrapperProperties scrapperProperties, BotLogger botLogger) {
        this.botLogger = botLogger;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(scrapperProperties.getConnectTimeout());
        requestFactory.setReadTimeout(scrapperProperties.getReadTimeout());
        this.restClient = restClientBuilder
                .baseUrl(scrapperProperties.getBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void registerChat(long chatId) {
        executeWithStatusHandling("register-chat", chatId, null, () -> restClient
                .post()
                .uri(CHAT_ENDPOINT_TEMPLATE, chatId)
                .retrieve()
                .toBodilessEntity());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteChat(long chatId) {
        executeWithStatusHandling("delete-chat", chatId, null, () -> restClient
                .delete()
                .uri(CHAT_ENDPOINT_TEMPLATE, chatId)
                .retrieve()
                .toBodilessEntity());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<ScrapperLinkView> listLinks(long chatId) {
        ScrapperListLinksResponse response = executeWithStatusHandling("list-links", chatId, null, () -> restClient
                .get()
                .uri(LINKS_ENDPOINT)
                .header(CHAT_HEADER, String.valueOf(chatId))
                .retrieve()
                .body(ScrapperListLinksResponse.class));
        if (response == null || response.links() == null) {
            return List.of();
        }
        return response.links().stream().map(this::toView).toList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ScrapperLinkView addLink(long chatId, AddScrapperLinkCommand command) {
        ScrapperLinkResponse response = executeWithStatusHandling("add-link", chatId, command.url(), () -> restClient
                .post()
                .uri(LINKS_ENDPOINT)
                .header(CHAT_HEADER, String.valueOf(chatId))
                .body(new ScrapperAddLinkRequest(command.url(), command.tags(), command.filters()))
                .retrieve()
                .body(ScrapperLinkResponse.class));
        if (response == null) {
            throw new ScrapperUnavailableException("Empty response from scrapper add-link", null);
        }
        return toView(response);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ScrapperLinkView removeLink(long chatId, String url) {
        ScrapperLinkResponse response = executeWithStatusHandling("remove-link", chatId, url, () -> restClient
                .method(org.springframework.http.HttpMethod.DELETE)
                .uri(LINKS_ENDPOINT)
                .header(CHAT_HEADER, String.valueOf(chatId))
                .body(new ScrapperRemoveLinkRequest(url))
                .retrieve()
                .body(ScrapperLinkResponse.class));
        if (response == null) {
            throw new ScrapperUnavailableException("Empty response from scrapper remove-link", null);
        }
        return toView(response);
    }

    private ScrapperLinkView toView(ScrapperLinkResponse response) {
        return new ScrapperLinkView(response.id(), response.url(), response.tags(), response.filters());
    }

    private <T> T executeWithStatusHandling(
            String operation, long chatId, String url, ThrowingSupplier<T> requestCall) {
        try {
            botLogger.logScrapperRequest(operation, chatId, url);
            return requestCall.get();
        } catch (HttpStatusCodeException exception) {
            int status = exception.getStatusCode().value();
            botLogger.logScrapperRequestFailed(
                    operation, chatId, url, status, exception.getClass().getSimpleName());
            if (status == 404) {
                throw new ScrapperNotFoundException("Scrapper returned not found", exception);
            }
            if (status == 409) {
                throw new ScrapperConflictException("Scrapper returned conflict", exception);
            }
            throw new ScrapperUnavailableException("Scrapper HTTP error: " + status, exception);
        } catch (ResourceAccessException exception) {
            botLogger.logScrapperRequestFailed(
                    operation, chatId, url, 0, exception.getClass().getSimpleName());
            throw new ScrapperUnavailableException("Scrapper transport error", exception);
        }
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get();
    }
}
