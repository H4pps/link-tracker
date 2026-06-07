package backend.academy.linktracker.scrapper.infrastructure.external;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import backend.academy.linktracker.scrapper.application.external.ExternalSourceException;
import backend.academy.linktracker.scrapper.application.external.link.github.GithubLinkSource;
import backend.academy.linktracker.scrapper.application.external.update.ExternalUpdate;
import backend.academy.linktracker.scrapper.application.external.update.ExternalUpdateType;
import backend.academy.linktracker.scrapper.logging.ScrapperLogger;
import backend.academy.linktracker.scrapper.properties.GithubProperties;
import backend.academy.linktracker.scrapper.properties.ResilienceProperties;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class GithubExternalSourceReaderTest {

    private WireMockServer wireMockServer;
    private GithubExternalSourceReader reader;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockServer.start();
        com.github.tomakehurst.wiremock.client.WireMock.configureFor("localhost", wireMockServer.port());

        GithubProperties properties = new GithubProperties();
        properties.setBaseUrl(wireMockServer.baseUrl());
        properties.setToken("token");
        properties.setConnectTimeout(Duration.ofSeconds(1));
        properties.setReadTimeout(Duration.ofSeconds(1));
        reader = new GithubExternalSourceReader(
                RestClient.builder(), properties, defaultResilienceProperties(), new ScrapperLogger());
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    @Test
    void mapsGithubIssueUpdate() {
        stubFor(get(urlPathEqualTo("/repos/octocat/Hello-World/issues"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                [
                                  {
                                    "title":"Fix scheduler retries",
                                    "created_at":"2024-01-01T00:00:00Z",
                                    "body":"Issue body",
                                    "user":{"login":"octocat"}
                                  }
                                ]
                                """)));

        ExternalUpdate update = reader.fetchLatestUpdate(new GithubLinkSource("octocat", "Hello-World"))
                .orElseThrow();

        assertThat(update.type()).isEqualTo(ExternalUpdateType.GITHUB_ISSUE);
        assertThat(update.title()).isEqualTo("Fix scheduler retries");
        assertThat(update.author()).isEqualTo("octocat");
        assertThat(update.preview()).isEqualTo("Issue body");
        assertThat(update.createdAt().toString()).isEqualTo("2024-01-01T00:00:00Z");
        verify(getRequestedFor(urlPathEqualTo("/repos/octocat/Hello-World/issues"))
                .withQueryParam("sort", equalTo("created"))
                .withQueryParam("direction", equalTo("desc"))
                .withQueryParam("per_page", equalTo("1"))
                .withQueryParam("state", equalTo("all"))
                .withHeader("Authorization", equalTo("Bearer token"))
                .withHeader("Accept", equalTo("application/vnd.github+json")));
    }

    @Test
    void mapsGithubPullRequestUpdate() {
        stubFor(get(urlPathEqualTo("/repos/octocat/Hello-World/issues"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                [
                                  {
                                    "title":"feat: add metadata payload",
                                    "created_at":"2024-02-01T10:11:12Z",
                                    "body":"PR body",
                                    "user":{"login":"hubot"},
                                    "pull_request":{"id":42}
                                  }
                                ]
                                """)));

        ExternalUpdate update = reader.fetchLatestUpdate(new GithubLinkSource("octocat", "Hello-World"))
                .orElseThrow();

        assertThat(update.type()).isEqualTo(ExternalUpdateType.GITHUB_PULL_REQUEST);
        assertThat(update.title()).isEqualTo("feat: add metadata payload");
        assertThat(update.author()).isEqualTo("hubot");
        assertThat(update.preview()).isEqualTo("PR body");
        assertThat(update.createdAt().toString()).isEqualTo("2024-02-01T10:11:12Z");
    }

    @Test
    void returnsEmptyWhenRepositoryHasNoIssuesOrPullRequests() {
        stubFor(get(urlPathEqualTo("/repos/octocat/Hello-World/issues"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[]")));

        assertThat(reader.fetchLatestUpdate(new GithubLinkSource("octocat", "Hello-World")))
                .isEmpty();
    }

    @Test
    void throwsOnMalformedPayload() {
        stubFor(get(urlPathEqualTo("/repos/octocat/Hello-World/issues"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                [{"title":"missing-created-at","user":{"login":"octocat"}}]
                                """)));

        assertThatThrownBy(() -> reader.fetchLatestUpdate(new GithubLinkSource("octocat", "Hello-World")))
                .isInstanceOf(ExternalSourceException.class);
    }

    @Test
    void throwsOnNon2xx() {
        stubFor(get(urlPathEqualTo("/repos/octocat/Hello-World/issues"))
                .willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> reader.fetchLatestUpdate(new GithubLinkSource("octocat", "Hello-World")))
                .isInstanceOf(ExternalSourceException.class);
    }

    @Test
    void delayedResponseFailsByTimeoutBeforeServerDelayCompletes() {
        reader = createReader(Duration.ofMillis(100), resilienceProperties(1, 1));
        stubFor(get(urlPathEqualTo("/repos/octocat/Hello-World/issues"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withFixedDelay(2_000)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[]")));

        long startedAt = System.nanoTime();
        assertThatThrownBy(() -> reader.fetchLatestUpdate(new GithubLinkSource("octocat", "Hello-World")))
                .isInstanceOf(ExternalSourceException.class);
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);

        assertThat(elapsed).isLessThan(Duration.ofMillis(1_500));
    }

    @Test
    void retryableStatusSequenceSucceedsAfterRetries() {
        reader = createReader(Duration.ofSeconds(1), resilienceProperties(3, 1));
        stubFor(get(urlPathEqualTo("/repos/octocat/Hello-World/issues"))
                .inScenario("github-retry")
                .whenScenarioStateIs(STARTED)
                .willReturn(aResponse().withStatus(500))
                .willSetStateTo("second-failure"));
        stubFor(get(urlPathEqualTo("/repos/octocat/Hello-World/issues"))
                .inScenario("github-retry")
                .whenScenarioStateIs("second-failure")
                .willReturn(aResponse().withStatus(500))
                .willSetStateTo("success"));
        stubFor(get(urlPathEqualTo("/repos/octocat/Hello-World/issues"))
                .inScenario("github-retry")
                .whenScenarioStateIs("success")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                [
                                  {
                                    "title":"Fix scheduler retries",
                                    "created_at":"2024-01-01T00:00:00Z",
                                    "body":"Issue body",
                                    "user":{"login":"octocat"}
                                  }
                                ]
                                """)));

        ExternalUpdate update = reader.fetchLatestUpdate(new GithubLinkSource("octocat", "Hello-World"))
                .orElseThrow();

        assertThat(update.title()).isEqualTo("Fix scheduler retries");
        verify(3, getRequestedFor(urlPathEqualTo("/repos/octocat/Hello-World/issues")));
    }

    @Test
    void nonRetryableClientStatusIsNotRetried() {
        reader = createReader(Duration.ofSeconds(1), resilienceProperties(3, 1));
        stubFor(get(urlPathEqualTo("/repos/octocat/Hello-World/issues"))
                .willReturn(aResponse().withStatus(400)));

        assertThatThrownBy(() -> reader.fetchLatestUpdate(new GithubLinkSource("octocat", "Hello-World")))
                .isInstanceOf(ExternalSourceException.class);

        verify(1, getRequestedFor(urlPathEqualTo("/repos/octocat/Hello-World/issues")));
    }

    private GithubExternalSourceReader createReader(Duration readTimeout, ResilienceProperties resilienceProperties) {
        GithubProperties properties = new GithubProperties();
        properties.setBaseUrl(wireMockServer.baseUrl());
        properties.setToken("token");
        properties.setConnectTimeout(Duration.ofSeconds(1));
        properties.setReadTimeout(readTimeout);
        return new GithubExternalSourceReader(
                RestClient.builder(), properties, resilienceProperties, new ScrapperLogger());
    }

    private ResilienceProperties defaultResilienceProperties() {
        return resilienceProperties(3, 1);
    }

    private ResilienceProperties resilienceProperties(int maxAttempts, long backoffMillis) {
        ResilienceProperties properties = new ResilienceProperties();
        properties.retry().setMaxAttempts(maxAttempts);
        properties.retry().setBackoff(Duration.ofMillis(backoffMillis));
        properties.retry().setRetryableHttpStatuses(Set.of(500, 502, 503, 504));
        properties.circuitBreaker().setMinimumNumberOfCalls(10);
        properties.circuitBreaker().setSlidingWindowSize(10);
        return properties;
    }
}
