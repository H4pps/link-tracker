package backend.academy.linktracker.scrapper.infrastructure.external;

import backend.academy.linktracker.scrapper.application.external.ExternalSourceException;
import backend.academy.linktracker.scrapper.application.external.ExternalSourceReader;
import backend.academy.linktracker.scrapper.application.external.link.LinkSource;
import backend.academy.linktracker.scrapper.application.external.link.github.GithubLinkSource;
import backend.academy.linktracker.scrapper.application.external.update.ExternalUpdate;
import backend.academy.linktracker.scrapper.application.external.update.ExternalUpdateType;
import backend.academy.linktracker.scrapper.infrastructure.external.dto.github.GithubIssueItemResponse;
import backend.academy.linktracker.scrapper.infrastructure.resilience.HttpResiliencePredicates;
import backend.academy.linktracker.scrapper.infrastructure.resilience.ResilientCallExecutor;
import backend.academy.linktracker.scrapper.logging.ScrapperLogger;
import backend.academy.linktracker.scrapper.properties.GithubProperties;
import backend.academy.linktracker.scrapper.properties.ResilienceProperties;
import java.time.Instant;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Manual GitHub API reader for latest repository issue/pull-request updates.
 */
@Component
public class GithubExternalSourceReader implements ExternalSourceReader {

    private final RestClient restClient;
    private final String token;
    private final ResilienceProperties resilienceProperties;
    private final ResilientCallExecutor resilientCallExecutor;
    private final ScrapperLogger scrapperLogger;

    /**
     * Creates GitHub reader with configured rest client.
     *
     * @param restClientBuilder spring rest client builder
     * @param githubProperties GitHub API properties
     * @param scrapperLogger structured logger
     */
    public GithubExternalSourceReader(
            RestClient.Builder restClientBuilder,
            GithubProperties githubProperties,
            ResilienceProperties resilienceProperties,
            ScrapperLogger scrapperLogger) {
        this.scrapperLogger = scrapperLogger;
        this.token = githubProperties.getToken();
        this.resilienceProperties = resilienceProperties;
        this.resilientCallExecutor = new ResilientCallExecutor(resilienceProperties);
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
    public Optional<ExternalUpdate> fetchLatestUpdate(LinkSource source) {
        GithubLinkSource githubSource = cast(source);
        try {
            GithubIssueItemResponse[] response = resilientCallExecutor.execute(
                    "github-http",
                    () -> restClient
                            .get()
                            .uri(
                                    "/repos/{owner}/{repo}/issues?sort=created&direction=desc&per_page=1&state=all",
                                    githubSource.owner(),
                                    githubSource.repository())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                            .retrieve()
                            .body(GithubIssueItemResponse[].class),
                    throwable -> HttpResiliencePredicates.isRetryableFailure(throwable, resilienceProperties),
                    throwable -> HttpResiliencePredicates.isCircuitBreakerFailure(throwable, resilienceProperties));
            if (response == null || response.length == 0) {
                return Optional.empty();
            }
            return Optional.of(mapLatestUpdate(response[0]));
        } catch (RuntimeException exception) {
            scrapperLogger.logExternalFetchFailed(
                    "github",
                    "https://github.com/" + githubSource.owner() + "/" + githubSource.repository(),
                    exception.getClass().getSimpleName());
            if (exception instanceof ExternalSourceException externalSourceException) {
                throw externalSourceException;
            }
            throw new ExternalSourceException("Failed to fetch GitHub issue metadata", exception);
        }
    }

    private ExternalUpdate mapLatestUpdate(GithubIssueItemResponse item) {
        if (item == null
                || item.createdAt() == null
                || item.createdAt().isBlank()
                || item.title() == null
                || item.title().isBlank()
                || item.user() == null
                || item.user().login() == null
                || item.user().login().isBlank()) {
            throw new ExternalSourceException("GitHub response missing required issue fields", null);
        }

        ExternalUpdateType type =
                item.pullRequest() == null ? ExternalUpdateType.GITHUB_ISSUE : ExternalUpdateType.GITHUB_PULL_REQUEST;
        return new ExternalUpdate(
                type,
                Instant.parse(item.createdAt()),
                item.title(),
                item.user().login(),
                item.body() == null ? "" : item.body());
    }

    private GithubLinkSource cast(LinkSource source) {
        if (!(source instanceof GithubLinkSource githubSource)) {
            throw new IllegalArgumentException(
                    "Expected GithubLinkSource, got: " + source.getClass().getSimpleName());
        }
        return githubSource;
    }
}
