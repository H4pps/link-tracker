package backend.academy.linktracker.scrapper.infrastructure.external;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import backend.academy.linktracker.scrapper.application.external.ExternalSourceException;
import backend.academy.linktracker.scrapper.application.external.GithubLinkSource;
import backend.academy.linktracker.scrapper.logging.ScrapperLogger;
import backend.academy.linktracker.scrapper.properties.GithubProperties;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.time.Duration;
import java.time.Instant;
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
        reader = new GithubExternalSourceReader(RestClient.builder(), properties, new ScrapperLogger());
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    @Test
    void parsesUpdatedAtOnSuccess() {
        stubFor(get("/repos/octocat/Hello-World")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"updated_at":"2024-01-01T00:00:00Z"}
                                """)));

        Instant updated = reader.fetchLastUpdated(new GithubLinkSource("octocat", "Hello-World"));

        assertThat(updated).isEqualTo(Instant.parse("2024-01-01T00:00:00Z"));
    }

    @Test
    void throwsOnNon2xx() {
        stubFor(get("/repos/octocat/Hello-World").willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> reader.fetchLastUpdated(new GithubLinkSource("octocat", "Hello-World")))
                .isInstanceOf(ExternalSourceException.class);
    }

    @Test
    void throwsOnMalformedPayload() {
        stubFor(get("/repos/octocat/Hello-World")
                .willReturn(aResponse().withStatus(200).withBody("{\"unknown\":1}")));

        assertThatThrownBy(() -> reader.fetchLastUpdated(new GithubLinkSource("octocat", "Hello-World")))
                .isInstanceOf(ExternalSourceException.class);
    }
}
