package backend.academy.linktracker.bot.infrastructure.scrapper.http;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.configureFor;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import backend.academy.linktracker.bot.application.scrapper.command.AddScrapperLinkCommand;
import backend.academy.linktracker.bot.application.scrapper.exception.ScrapperConflictException;
import backend.academy.linktracker.bot.application.scrapper.exception.ScrapperNotFoundException;
import backend.academy.linktracker.bot.application.scrapper.view.ScrapperLinkView;
import backend.academy.linktracker.bot.logging.BotLogger;
import backend.academy.linktracker.bot.properties.ScrapperProperties;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class HttpScrapperGatewayTest {

    private WireMockServer wireMockServer;
    private HttpScrapperGateway gateway;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockServer.start();
        configureFor("localhost", wireMockServer.port());

        ScrapperProperties properties = new ScrapperProperties();
        properties.setBaseUrl(wireMockServer.baseUrl());
        properties.setConnectTimeout(Duration.ofSeconds(1));
        properties.setReadTimeout(Duration.ofSeconds(1));
        gateway = new HttpScrapperGateway(RestClient.builder(), properties, new BotLogger());
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    @Test
    void listLinksMapsContractResponse() {
        stubFor(get("/links")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "links": [
                                    {"id": 1, "url": "https://github.com/a/b", "tags": ["work"], "filters": []}
                                  ],
                                  "size": 1
                                }
                                """)));

        ScrapperLinkView link = gateway.listLinks(1L).getFirst();

        assertThat(link.url()).isEqualTo("https://github.com/a/b");
        assertThat(link.tags()).containsExactly("work");
    }

    @Test
    void addLinkMapsConflictStatus() {
        stubFor(post("/links").willReturn(aResponse().withStatus(409)));

        assertThatThrownBy(() -> gateway.addLink(
                        1L,
                        new AddScrapperLinkCommand("https://github.com/a/b", java.util.List.of(), java.util.List.of())))
                .isInstanceOf(ScrapperConflictException.class);
    }

    @Test
    void removeLinkMapsNotFoundStatus() {
        stubFor(delete("/links").willReturn(aResponse().withStatus(404)));

        assertThatThrownBy(() -> gateway.removeLink(1L, "https://github.com/a/b"))
                .isInstanceOf(ScrapperNotFoundException.class);
    }
}
