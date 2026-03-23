package backend.academy.linktracker.bot;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathTemplate;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import backend.academy.linktracker.bot.properties.TelegramProperties;
import backend.academy.linktracker.bot.telegram.command.TelegramCommandProcessor;
import backend.academy.linktracker.bot.telegram.command.TelegramCommandRegistry;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.User;
import com.pengrad.telegrambot.request.GetUpdates;
import com.pengrad.telegrambot.request.SetMyCommands;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.wiremock.spring.EnableWireMock;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@EnableWireMock
class TelegramBotIntegrationTest implements WithAssertions {

    @Autowired
    TelegramBot telegramBot;

    @Autowired
    TelegramProperties telegramProperties;

    @Autowired
    TelegramCommandRegistry commandRegistry;

    @Autowired
    TelegramCommandProcessor commandProcessor;

    @AfterEach
    void clearUpdatesListener() {
        telegramBot.removeGetUpdatesListener();
    }

    @Test
    void nonExistingTokenRequest() {
        stubFor(post(urlMatching("/bot[^/]+/getUpdates"))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withBody("{\"ok\":false,\"error_code\":404,\"description\":\"Not Found\"}")));

        var getUpdatesRequest = new GetUpdates();
        var getUpdatesResponse = telegramBot.execute(getUpdatesRequest);

        assertFalse(getUpdatesResponse.isOk());
        assertEquals(404, getUpdatesResponse.errorCode());

        verify(
                1,
                postRequestedFor(urlPathTemplate("/bot{token}/getUpdates"))
                        .withPathParam("token", equalTo(telegramProperties.getToken())));
    }

    @Test
    void updatesListenerReceivesUpdates() throws InterruptedException {
        stubFor(post(urlMatching("/bot[^/]+/getUpdates"))
                .inScenario("Updates Listener")
                .whenScenarioStateIs(STARTED)
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody("""
                                {
                                  "ok": true,
                                  "result": [
                                    {
                                      "update_id": 123456,
                                      "message": {
                                        "message_id": 1,
                                        "from": {
                                          "id": 987654321,
                                          "is_bot": false,
                                          "first_name": "Test",
                                          "username": "testuser"
                                        },
                                        "chat": {
                                          "id": 987654321,
                                          "type": "private"
                                        },
                                        "date": 1234567890,
                                        "text": "Hello Bot"
                                      }
                                    }
                                  ]
                                }
                                """))
                .willSetStateTo("Updates Received"));

        stubFor(post(urlMatching("/bot[^/]+/getUpdates"))
                .inScenario("Updates Listener")
                .whenScenarioStateIs("Updates Received")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody("""
                                {
                                  "ok": true,
                                  "result": []
                                }
                                """)));

        List<Update> receivedUpdates = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        telegramBot.setUpdatesListener(updates -> {
            receivedUpdates.addAll(updates);
            latch.countDown();
            return UpdatesListener.CONFIRMED_UPDATES_ALL;
        });

        boolean received = latch.await(10, TimeUnit.SECONDS);

        assertTrue(received);
        assertThat(receivedUpdates)
                .hasSize(1)
                .first()
                .returns(123456, Update::updateId)
                .extracting(Update::message)
                .returns("Hello Bot", Message::text)
                .extracting(Message::from)
                .returns("testuser", User::username);

        verify(postRequestedFor(urlPathTemplate("/bot{token}/getUpdates"))
                .withPathParam("token", equalTo(telegramProperties.getToken())));
    }

    @Test
    void registersCommandsMenuFromRegistry() {
        telegramBot.execute(new SetMyCommands(commandRegistry.menuCommands()));

        verify(postRequestedFor(urlMatching("/bot[^/]+/setMyCommands"))
                .withRequestBody(containing("start"))
                .withRequestBody(containing("help"))
                .withRequestBody(containing("track"))
                .withRequestBody(containing("untrack"))
                .withRequestBody(containing("list")));
    }

    @Test
    void startCommandReturnsGreetingContract() {
        stubFor(post(urlMatching("/tg-chat/123")).willReturn(aResponse().withStatus(200)));

        var response = commandProcessor.process(123L, "/start");

        assertThat(response.reply())
                .isEqualTo("Добро пожаловать! Используйте /help, чтобы посмотреть доступные команды.");
    }

    @Test
    void helpCommandReturnsCommandsListContract() {
        var response = commandProcessor.process(123L, "/help");

        assertThat(response.reply()).isEqualTo("""
                        Доступные команды:
                        /help - список команд
                        /list - показать отслеживаемые ссылки
                        /start - начало работы
                        /track - начать отслеживание ссылки
                        /untrack - прекратить отслеживание ссылки""");
    }

    @Test
    void trackCommandReturnsDialogStartPrompt() {
        var response = commandProcessor.process(123L, "/track");

        assertThat(response.reply())
                .isEqualTo("Введите ссылку, которую хотите отслеживать. Для отмены используйте /cancel.");
    }

    @Test
    void untrackCommandWithoutArgumentReturnsUsageHint() {
        var response = commandProcessor.process(123L, "/untrack");

        assertThat(response.reply()).isEqualTo("Использование: /untrack <url>");
    }

    @Test
    void listCommandReturnsEmptyStateStub() {
        stubFor(post(urlMatching("/tg-chat/123")).willReturn(aResponse().withStatus(200)));
        stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlMatching("/links"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody("""
                                {
                                  "links": [],
                                  "size": 0
                                }
                                """)));

        commandProcessor.process(123L, "/start");
        var response = commandProcessor.process(123L, "/list");

        assertThat(response.reply()).isEqualTo("Список отслеживаемых ссылок пока пуст.");
    }

    @Test
    void unknownCommandReturnsErrorContract() {
        var response = commandProcessor.process(123L, "/unsupported");

        assertThat(response.reply()).isEqualTo(TelegramCommandProcessor.UNKNOWN_REPLY);
    }
}
