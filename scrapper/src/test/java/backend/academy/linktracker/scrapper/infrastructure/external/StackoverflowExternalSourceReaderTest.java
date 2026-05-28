package backend.academy.linktracker.scrapper.infrastructure.external;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import backend.academy.linktracker.scrapper.application.external.ExternalSourceException;
import backend.academy.linktracker.scrapper.application.external.StackoverflowQuestionLinkSource;
import backend.academy.linktracker.scrapper.logging.ScrapperLogger;
import backend.academy.linktracker.scrapper.properties.StackoverflowProperties;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class StackoverflowExternalSourceReaderTest {

    private WireMockServer wireMockServer;
    private StackoverflowExternalSourceReader reader;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockServer.start();
        com.github.tomakehurst.wiremock.client.WireMock.configureFor("localhost", wireMockServer.port());

        StackoverflowProperties properties = new StackoverflowProperties();
        properties.setBaseUrl(wireMockServer.baseUrl());
        properties.setKey("key");
        properties.setAccessToken("access-token");
        properties.setConnectTimeout(Duration.ofSeconds(1));
        properties.setReadTimeout(Duration.ofSeconds(1));
        reader = new StackoverflowExternalSourceReader(RestClient.builder(), properties, new ScrapperLogger());
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    @Test
    void parsesLastActivityDateOnSuccess() {
        stubFor(get(com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo("/2.3/questions/123"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"items":[{"last_activity_date":1704067200}]}
                                """)));

        Instant updated = reader.fetchLastUpdated(new StackoverflowQuestionLinkSource(123));

        assertThat(updated).isEqualTo(Instant.ofEpochSecond(1704067200));
    }

    @Test
    void throwsOnNon2xx() {
        stubFor(get(com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo("/2.3/questions/123"))
                .willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> reader.fetchLastUpdated(new StackoverflowQuestionLinkSource(123)))
                .isInstanceOf(ExternalSourceException.class);
    }

    @Test
    void throwsOnMalformedPayload() {
        stubFor(get(com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo("/2.3/questions/123"))
                .willReturn(aResponse().withStatus(200).withBody("{\"items\":[]}")));

        assertThatThrownBy(() -> reader.fetchLastUpdated(new StackoverflowQuestionLinkSource(123)))
                .isInstanceOf(ExternalSourceException.class);
    }
}
