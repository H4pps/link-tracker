package backend.academy.linktracker.scrapper.infrastructure.external;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import backend.academy.linktracker.scrapper.application.external.ExternalSourceException;
import backend.academy.linktracker.scrapper.application.external.link.stackoverflow.StackoverflowQuestionLinkSource;
import backend.academy.linktracker.scrapper.application.external.update.ExternalUpdate;
import backend.academy.linktracker.scrapper.application.external.update.ExternalUpdateType;
import backend.academy.linktracker.scrapper.logging.ScrapperLogger;
import backend.academy.linktracker.scrapper.properties.StackoverflowProperties;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.time.Duration;
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
    void mapsLatestAnswerWhenAnswerIsNewer() {
        stubQuestionTitle("How to paginate scheduler link checks?");
        stubAnswers("""
                {
                  "items":[
                    {"creation_date":1704067400,"body":"Answer body","owner":{"display_name":"alice"}}
                  ]
                }
                """);
        stubComments("""
                {
                  "items":[
                    {"creation_date":1704067300,"body":"Comment body","owner":{"display_name":"bob"}}
                  ]
                }
                """);

        ExternalUpdate update = reader.fetchLatestUpdate(new StackoverflowQuestionLinkSource(123));

        assertThat(update.type()).isEqualTo(ExternalUpdateType.STACKOVERFLOW_ANSWER);
        assertThat(update.title()).isEqualTo("How to paginate scheduler link checks?");
        assertThat(update.author()).isEqualTo("alice");
        assertThat(update.preview()).isEqualTo("Answer body");
        assertThat(update.createdAt().toString()).isEqualTo("2024-01-01T00:03:20Z");
    }

    @Test
    void mapsLatestCommentWhenCommentIsNewer() {
        stubQuestionTitle("How to paginate scheduler link checks?");
        stubAnswers("""
                {
                  "items":[
                    {"creation_date":1704067300,"body":"Answer body","owner":{"display_name":"alice"}}
                  ]
                }
                """);
        stubComments("""
                {
                  "items":[
                    {"creation_date":1704067600,"body":"Comment body","owner":{"display_name":"bob"}}
                  ]
                }
                """);

        ExternalUpdate update = reader.fetchLatestUpdate(new StackoverflowQuestionLinkSource(123));

        assertThat(update.type()).isEqualTo(ExternalUpdateType.STACKOVERFLOW_COMMENT);
        assertThat(update.title()).isEqualTo("How to paginate scheduler link checks?");
        assertThat(update.author()).isEqualTo("bob");
        assertThat(update.preview()).isEqualTo("Comment body");
        assertThat(update.createdAt().toString()).isEqualTo("2024-01-01T00:06:40Z");
    }

    @Test
    void throwsOnEmptyAnswerAndCommentPayloads() {
        stubQuestionTitle("How to paginate scheduler link checks?");
        stubAnswers("{\"items\":[]}");
        stubComments("{\"items\":[]}");

        assertThatThrownBy(() -> reader.fetchLatestUpdate(new StackoverflowQuestionLinkSource(123)))
                .isInstanceOf(ExternalSourceException.class);
    }

    @Test
    void throwsOnMalformedPayload() {
        stubFor(get(urlPathEqualTo("/2.3/questions/123"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"items\":[{}]}")));
        stubAnswers("""
                {
                  "items":[
                    {"creation_date":1704067300,"body":"Answer body","owner":{"display_name":"alice"}}
                  ]
                }
                """);
        stubComments("""
                {
                  "items":[
                    {"creation_date":1704067600,"body":"Comment body","owner":{"display_name":"bob"}}
                  ]
                }
                """);

        assertThatThrownBy(() -> reader.fetchLatestUpdate(new StackoverflowQuestionLinkSource(123)))
                .isInstanceOf(ExternalSourceException.class);
    }

    @Test
    void throwsOnNon2xx() {
        stubFor(get(urlPathEqualTo("/2.3/questions/123")).willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> reader.fetchLatestUpdate(new StackoverflowQuestionLinkSource(123)))
                .isInstanceOf(ExternalSourceException.class);
    }

    private void stubQuestionTitle(String title) {
        stubFor(get(urlPathEqualTo("/2.3/questions/123"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"items":[{"title":"%s"}]}
                                """.formatted(title))));
    }

    private void stubAnswers(String body) {
        stubFor(get(urlPathEqualTo("/2.3/questions/123/answers"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }

    private void stubComments(String body) {
        stubFor(get(urlPathEqualTo("/2.3/questions/123/comments"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }
}
