package backend.academy.linktracker.scrapper.infrastructure.bot.http;

import backend.academy.linktracker.scrapper.application.update.BotNotificationSender;
import backend.academy.linktracker.scrapper.application.update.LinkUpdateNotification;
import backend.academy.linktracker.scrapper.infrastructure.bot.http.dto.BotLinkUpdateRequest;
import backend.academy.linktracker.scrapper.logging.ScrapperLogger;
import backend.academy.linktracker.scrapper.properties.BotProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * HTTP sender for scrapper-to-bot update notifications.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.bot", name = "mode", havingValue = "http")
public class HttpBotNotificationSender implements BotNotificationSender {

    private static final String UPDATES_ENDPOINT = "/updates";

    private final RestClient.Builder restClientBuilder;
    private final BotProperties botProperties;
    private final ScrapperLogger scrapperLogger;

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean send(LinkUpdateNotification notification) {
        try {
            restClient()
                    .post()
                    .uri(UPDATES_ENDPOINT)
                    .body(new BotLinkUpdateRequest(
                            notification.id(),
                            notification.url(),
                            notification.description(),
                            notification.tgChatIds()))
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (RuntimeException exception) {
            scrapperLogger.logExternalFetchFailed(
                    "bot", notification.url(), exception.getClass().getSimpleName());
            return false;
        }
    }

    private RestClient restClient() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(botProperties.getConnectTimeout());
        requestFactory.setReadTimeout(botProperties.getReadTimeout());
        return restClientBuilder
                .baseUrl(botProperties.getBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }
}
