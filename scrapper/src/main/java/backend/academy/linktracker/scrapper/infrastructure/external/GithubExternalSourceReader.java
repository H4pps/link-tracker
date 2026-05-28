package backend.academy.linktracker.scrapper.infrastructure.external;

import backend.academy.linktracker.scrapper.application.external.ExternalSourceException;
import backend.academy.linktracker.scrapper.application.external.ExternalSourceReader;
import backend.academy.linktracker.scrapper.application.external.GithubLinkSource;
import backend.academy.linktracker.scrapper.application.external.LinkSource;
import backend.academy.linktracker.scrapper.infrastructure.external.dto.GithubRepositoryResponse;
import backend.academy.linktracker.scrapper.logging.ScrapperLogger;
import backend.academy.linktracker.scrapper.properties.GithubProperties;
import java.time.Instant;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Manual GitHub API reader for repository update timestamps.
 */
@Component
public class GithubExternalSourceReader implements ExternalSourceReader {

    private final RestClient restClient;
    private final String token;
    private final ScrapperLogger scrapperLogger;

    /**
     * Creates GitHub reader with configured rest client.
     *
     * @param restClientBuilder spring rest client builder
     * @param githubProperties GitHub API properties
     * @param scrapperLogger structured logger
     */
    public GithubExternalSourceReader(
            RestClient.Builder restClientBuilder, GithubProperties githubProperties, ScrapperLogger scrapperLogger) {
        this.scrapperLogger = scrapperLogger;
        this.token = githubProperties.getToken();
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(githubProperties.getConnectTimeout());
        requestFactory.setReadTimeout(githubProperties.getReadTimeout());
        this.restClient = restClientBuilder
                .baseUrl(githubProperties.getBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean supports(LinkSource source) {
        return source instanceof GithubLinkSource;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Instant fetchLastUpdated(LinkSource source) {
        GithubLinkSource githubSource = cast(source);
        try {
            GithubRepositoryResponse response = restClient
                    .get()
                    .uri("/repos/{owner}/{repo}", githubSource.owner(), githubSource.repository())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                    .retrieve()
                    .body(GithubRepositoryResponse.class);
            if (response == null
                    || response.updatedAt() == null
                    || response.updatedAt().isBlank()) {
                throw new ExternalSourceException("GitHub response missing updated_at", null);
            }
            return Instant.parse(response.updatedAt());
        } catch (RuntimeException exception) {
            scrapperLogger.logExternalFetchFailed(
                    "github",
                    "https://github.com/" + githubSource.owner() + "/" + githubSource.repository(),
                    exception.getClass().getSimpleName());
            if (exception instanceof ExternalSourceException externalSourceException) {
                throw externalSourceException;
            }
            throw new ExternalSourceException("Failed to fetch GitHub repository metadata", exception);
        }
    }

    private GithubLinkSource cast(LinkSource source) {
        if (!(source instanceof GithubLinkSource githubSource)) {
            throw new IllegalArgumentException(
                    "Expected GithubLinkSource, got: " + source.getClass().getSimpleName());
        }
        return githubSource;
    }
}
