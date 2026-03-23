package backend.academy.linktracker.bot.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;

/**
 * Container end-to-end checks for HTTP fallback and default gRPC transport.
 */
class TransportIntegrationE2EIT {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final HttpClient HTTP_CLIENT =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private static final long CHAT_ID = 10101L;
    private static final String TELEGRAM_TOKEN = "e2e-test-token";
    private static final String TRACKED_URL = "https://github.com/acme/repo";
    private static final Path REPOSITORY_ROOT = resolveRepositoryRoot();
    private static final ImageFromDockerfile BOT_IMAGE =
            imageFromActualDockerfile("link-tracker-bot-e2e:latest", "bot/Dockerfile");
    private static final ImageFromDockerfile SCRAPPER_IMAGE =
            imageFromActualDockerfile("link-tracker-scrapper-e2e:latest", "scrapper/Dockerfile");

    /**
     * Verifies HTTP transport fallback with containerized REST lifecycle checks.
     *
     * @throws Exception when network or parsing operation fails
     */
    @Test
    void httpFallbackLifecycleWorksInContainers() throws Exception {
        try (FakeIntegrationServer fakeServer = new FakeIntegrationServer();
                RunningSystem system = startSystem(TransportMode.HTTP, fakeServer, false, false)) {
            String scrapperBaseUrl = system.scrapperBaseUrl();
            String botBaseUrl = system.botBaseUrl();

            assertThat(postWithoutBody(scrapperBaseUrl + "/tg-chat/" + CHAT_ID, Map.of())
                            .statusCode())
                    .isEqualTo(200);

            HttpResponse<String> addLinkResponse = sendJsonRequest(
                    "POST", scrapperBaseUrl + "/links", """
                    {"link":"https://github.com/acme/repo","tags":["work"],"filters":["branch=main"]}
                    """, Map.of("Tg-Chat-Id", String.valueOf(CHAT_ID)));
            assertThat(addLinkResponse.statusCode()).isEqualTo(200);
            JsonNode addLinkJson = OBJECT_MAPPER.readTree(addLinkResponse.body());
            assertThat(addLinkJson.path("id").asLong()).isPositive();
            assertThat(addLinkJson.path("url").asText()).isEqualTo(TRACKED_URL);

            HttpResponse<String> listLinksResponse = sendJsonRequest(
                    "GET", scrapperBaseUrl + "/links", null, Map.of("Tg-Chat-Id", String.valueOf(CHAT_ID)));
            assertThat(listLinksResponse.statusCode()).isEqualTo(200);
            JsonNode listLinksJson = OBJECT_MAPPER.readTree(listLinksResponse.body());
            assertThat(listLinksJson.path("size").asInt()).isEqualTo(1);
            assertThat(listLinksJson.path("links").get(0).path("url").asText()).isEqualTo(TRACKED_URL);

            HttpResponse<String> removeLinkResponse = sendJsonRequest(
                    "DELETE", scrapperBaseUrl + "/links", """
                    {"link":"https://github.com/acme/repo"}
                    """, Map.of("Tg-Chat-Id", String.valueOf(CHAT_ID)));
            assertThat(removeLinkResponse.statusCode()).isEqualTo(200);

            assertThat(sendJsonRequest("DELETE", scrapperBaseUrl + "/tg-chat/" + CHAT_ID, null, Map.of())
                            .statusCode())
                    .isEqualTo(200);

            HttpResponse<String> updateResponse = sendJsonRequest("POST", botBaseUrl + "/updates", """
                    {"id":1,"url":"https://github.com/acme/repo","description":"changed","tgChatIds":[10101]}
                    """, Map.of());
            assertThat(updateResponse.statusCode()).isEqualTo(200);
            Awaitility.await()
                    .atMost(Duration.ofSeconds(10))
                    .pollInterval(Duration.ofMillis(250))
                    .untilAsserted(() -> assertThat(fakeServer.sentMessages())
                            .anyMatch(text -> text.contains("Обновление по ссылке: " + TRACKED_URL)));
        }
    }

    /**
     * Verifies gRPC-default end-to-end flow in containers for both integration directions.
     *
     * @throws Exception when network or parsing operation fails
     */
    @Test
    void grpcDefaultFlowConnectsBothDirections() throws Exception {
        try (FakeIntegrationServer fakeServer = new FakeIntegrationServer();
                RunningSystem system = startSystem(TransportMode.GRPC, fakeServer, true, true)) {
            fakeServer.setGithubUpdatedAt(Instant.parse("2026-01-01T00:00:00Z"));

            fakeServer.enqueueTelegramMessage(CHAT_ID, "/start");
            fakeServer.enqueueTelegramMessage(CHAT_ID, "/track");
            fakeServer.enqueueTelegramMessage(CHAT_ID, TRACKED_URL);
            fakeServer.enqueueTelegramMessage(CHAT_ID, "work");
            fakeServer.enqueueTelegramMessage(CHAT_ID, "");

            Awaitility.await()
                    .atMost(Duration.ofSeconds(25))
                    .pollInterval(Duration.ofMillis(500))
                    .untilAsserted(() -> {
                        HttpResponse<String> listLinksResponse = sendJsonRequest(
                                "GET",
                                system.scrapperBaseUrl() + "/links",
                                null,
                                Map.of("Tg-Chat-Id", String.valueOf(CHAT_ID)));
                        assertThat(listLinksResponse.statusCode()).isEqualTo(200);
                        JsonNode linksJson = OBJECT_MAPPER.readTree(listLinksResponse.body());
                        List<String> trackedUrls = new ArrayList<>();
                        linksJson
                                .path("links")
                                .forEach(linkNode ->
                                        trackedUrls.add(linkNode.path("url").asText()));
                        assertThat(trackedUrls).contains(TRACKED_URL);
                    });

            Awaitility.await()
                    .atMost(Duration.ofSeconds(25))
                    .pollInterval(Duration.ofMillis(500))
                    .untilAsserted(
                            () -> assertThat(fakeServer.githubRequestCount()).isGreaterThan(0));

            fakeServer.setGithubUpdatedAt(Instant.parse("2026-01-01T00:01:00Z"));
            Awaitility.await()
                    .atMost(Duration.ofSeconds(25))
                    .pollInterval(Duration.ofMillis(500))
                    .untilAsserted(() -> assertThat(fakeServer.sentMessages())
                            .anyMatch(text -> text.contains("Обновление по ссылке: " + TRACKED_URL)));
        }
    }

    /**
     * Starts bot and scrapper containers with selected transport mode.
     *
     * @param mode selected inter-service transport mode
     * @param fakeServer integration server for Telegram and external APIs
     * @param schedulerEnabled scheduler enabled flag for scrapper
     * @param pollingEnabled polling enabled flag for bot
     * @return started system holder
     */
    private RunningSystem startSystem(
            TransportMode mode, FakeIntegrationServer fakeServer, boolean schedulerEnabled, boolean pollingEnabled) {
        Testcontainers.exposeHostPorts(fakeServer.port());
        Network network = Network.newNetwork();
        try {
            GenericContainer<?> scrapperContainer = new GenericContainer<>(SCRAPPER_IMAGE)
                    .withNetwork(network)
                    .withNetworkAliases("scrapper")
                    .withExposedPorts(8081, 9091)
                    .withEnv("GITHUB_TOKEN", "integration-token")
                    .withEnv("STACKOVERFLOW_KEY", "integration-key")
                    .withEnv("STACKOVERFLOW_ACCESS_KEY", "integration-access-token")
                    .withEnv("APP_SCRAPPER_GRPC_SERVER_PORT", "9091")
                    .withEnv("APP_BOT_MODE", mode.value())
                    .withEnv("APP_BOT_BASE_URL", "http://bot:8080")
                    .withEnv("APP_BOT_GRPC_HOST", "bot")
                    .withEnv("APP_BOT_GRPC_PORT", "9090")
                    .withEnv("APP_BOT_GRPC_DEADLINE", "3s")
                    .withEnv("APP_SCHEDULER_ENABLED", String.valueOf(schedulerEnabled))
                    .withEnv("APP_SCHEDULER_INTERVAL", "1s")
                    .withEnv("APP_GITHUB_BASE_URL", "http://host.testcontainers.internal:" + fakeServer.port())
                    .withEnv("APP_STACKOVERFLOW_BASE_URL", "http://host.testcontainers.internal:" + fakeServer.port())
                    .waitingFor(Wait.forHttp("/v3/api-docs")
                            .forPort(8081)
                            .forStatusCode(200)
                            .withStartupTimeout(Duration.ofMinutes(4)));

            GenericContainer<?> botContainer = new GenericContainer<>(BOT_IMAGE)
                    .withNetwork(network)
                    .withNetworkAliases("bot")
                    .withExposedPorts(8080, 9090)
                    .withEnv("TELEGRAM_TOKEN", TELEGRAM_TOKEN)
                    .withEnv("APP_TELEGRAM_URL", "http://host.testcontainers.internal:" + fakeServer.port() + "/bot")
                    .withEnv("APP_TELEGRAM_POLLING_ENABLED", String.valueOf(pollingEnabled))
                    .withEnv("APP_TELEGRAM_UPDATE_LISTENER_SLEEP", "100ms")
                    .withEnv("APP_BOT_GRPC_SERVER_PORT", "9090")
                    .withEnv("APP_SCRAPPER_MODE", mode.value())
                    .withEnv("APP_SCRAPPER_BASE_URL", "http://scrapper:8081")
                    .withEnv("APP_SCRAPPER_GRPC_HOST", "scrapper")
                    .withEnv("APP_SCRAPPER_GRPC_PORT", "9091")
                    .withEnv("APP_SCRAPPER_GRPC_DEADLINE", "3s")
                    .waitingFor(Wait.forHttp("/v3/api-docs")
                            .forPort(8080)
                            .forStatusCode(200)
                            .withStartupTimeout(Duration.ofMinutes(4)));

            scrapperContainer.start();
            botContainer.start();
            return new RunningSystem(network, botContainer, scrapperContainer);
        } catch (RuntimeException exception) {
            network.close();
            throw exception;
        }
    }

    /**
     * Sends an HTTP request without body.
     *
     * @param url absolute request URL
     * @param headers request headers
     * @return HTTP response with string body
     * @throws IOException when transport fails
     * @throws InterruptedException when interrupted while waiting for response
     */
    private HttpResponse<String> postWithoutBody(String url, Map<String, String> headers)
            throws IOException, InterruptedException {
        return sendJsonRequest("POST", url, null, headers);
    }

    /**
     * Sends JSON or empty HTTP request to REST endpoint.
     *
     * @param method HTTP method
     * @param url absolute request URL
     * @param body optional request JSON body
     * @param headers additional request headers
     * @return HTTP response with string body
     * @throws IOException when transport fails
     * @throws InterruptedException when interrupted while waiting for response
     */
    private HttpResponse<String> sendJsonRequest(String method, String url, String body, Map<String, String> headers)
            throws IOException, InterruptedException {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder().uri(URI.create(url));
        if (body == null) {
            requestBuilder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            requestBuilder.header("Content-Type", "application/json");
            requestBuilder.method(method, HttpRequest.BodyPublishers.ofString(body.strip()));
        }
        headers.forEach(requestBuilder::header);
        return HTTP_CLIENT.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Resolves repository root path from current test working directory.
     *
     * @return repository root path
     */
    private static Path resolveRepositoryRoot() {
        String multiModuleDirectory = System.getProperty("maven.multiModuleProjectDirectory");
        if (multiModuleDirectory != null && !multiModuleDirectory.isBlank()) {
            Path multiModuleRoot =
                    Path.of(multiModuleDirectory).toAbsolutePath().normalize();
            if (isRepositoryRoot(multiModuleRoot)) {
                return multiModuleRoot;
            }
        }

        String basedir = System.getProperty("basedir");
        if (basedir != null && !basedir.isBlank()) {
            Path basedirRoot = Path.of(basedir).toAbsolutePath().normalize();
            if (isRepositoryRoot(basedirRoot)) {
                return basedirRoot;
            }
        }

        Path candidate = Path.of("").toAbsolutePath().normalize();
        while (candidate != null) {
            if (isRepositoryRoot(candidate)) {
                return candidate;
            }
            candidate = candidate.getParent();
        }
        throw new IllegalStateException("Failed to resolve repository root for E2E docker build context");
    }

    /**
     * Checks that candidate directory contains expected multi-module structure.
     *
     * @param candidate candidate root path
     * @return {@code true} when candidate has required module layout
     */
    private static boolean isRepositoryRoot(Path candidate) {
        return candidate.resolve(".mvn").toFile().isDirectory()
                && candidate.resolve("pom.xml").toFile().isFile()
                && candidate.resolve("ai-agent").resolve("pom.xml").toFile().isFile()
                && candidate.resolve("bot").resolve("pom.xml").toFile().isFile()
                && candidate.resolve("scrapper").resolve("pom.xml").toFile().isFile()
                && candidate
                        .resolve("build-report-aggregate")
                        .resolve("pom.xml")
                        .toFile()
                        .isFile();
    }

    /**
     * Creates a Testcontainers image descriptor based on a real service Dockerfile.
     *
     * @param imageName docker image tag used by Testcontainers
     * @param dockerfilePath dockerfile path relative to repository root
     * @return image descriptor using repository root as build context
     */
    private static ImageFromDockerfile imageFromActualDockerfile(String imageName, String dockerfilePath) {
        return new ImageFromDockerfile(imageName, false)
                .withFileFromPath(".", REPOSITORY_ROOT)
                .withDockerfile(REPOSITORY_ROOT.resolve(dockerfilePath));
    }

    /**
     * Transport mode used in containerized test setup.
     */
    private enum TransportMode {
        HTTP("http"),
        GRPC("grpc");

        private final String value;

        TransportMode(String value) {
            this.value = value;
        }

        /**
         * Returns lowercase property value expected by Spring configuration.
         *
         * @return lowercase transport mode
         */
        String value() {
            return value;
        }
    }

    /**
     * Holder for running bot and scrapper containers.
     *
     * @param network shared docker network
     * @param botContainer started bot container
     * @param scrapperContainer started scrapper container
     */
    private record RunningSystem(
            Network network, GenericContainer<?> botContainer, GenericContainer<?> scrapperContainer)
            implements AutoCloseable {

        /**
         * Returns mapped bot REST base URL.
         *
         * @return bot REST base URL
         */
        String botBaseUrl() {
            return "http://localhost:" + botContainer.getMappedPort(8080);
        }

        /**
         * Returns mapped scrapper REST base URL.
         *
         * @return scrapper REST base URL
         */
        String scrapperBaseUrl() {
            return "http://localhost:" + scrapperContainer.getMappedPort(8081);
        }

        /**
         * Stops containers and closes network.
         */
        @Override
        public void close() {
            botContainer.stop();
            scrapperContainer.stop();
            network.close();
        }
    }

    /**
     * Lightweight fake HTTP server for Telegram and GitHub endpoints used in E2E tests.
     */
    private static final class FakeIntegrationServer implements AutoCloseable {

        private final HttpServer server;
        private final Queue<TelegramInboundUpdate> pendingUpdates = new ConcurrentLinkedQueue<>();
        private final List<String> sentMessages = new CopyOnWriteArrayList<>();
        private final AtomicLong nextUpdateId = new AtomicLong(1);
        private final AtomicLong githubRequestCount = new AtomicLong();
        private final AtomicReference<Instant> githubUpdatedAt = new AtomicReference<>(Instant.now());

        /**
         * Creates and starts the fake server.
         *
         * @throws IOException when server cannot start
         */
        private FakeIntegrationServer() throws IOException {
            this.server = HttpServer.create(new InetSocketAddress(0), 0);
            this.server.createContext("/", this::handleRequest);
            this.server.setExecutor(Executors.newFixedThreadPool(4));
            this.server.start();
        }

        /**
         * Returns listening server port.
         *
         * @return server port
         */
        int port() {
            return server.getAddress().getPort();
        }

        /**
         * Enqueues a Telegram message that will be returned via next getUpdates poll.
         *
         * @param chatId telegram chat identifier
         * @param text incoming message text
         */
        void enqueueTelegramMessage(long chatId, String text) {
            pendingUpdates.add(new TelegramInboundUpdate(nextUpdateId.getAndIncrement(), chatId, text));
        }

        /**
         * Sets GitHub `updated_at` value returned by fake repository endpoint.
         *
         * @param value next repository timestamp value
         */
        void setGithubUpdatedAt(Instant value) {
            githubUpdatedAt.set(value);
        }

        /**
         * Returns immutable snapshot of sent Telegram message texts.
         *
         * @return sent Telegram messages
         */
        List<String> sentMessages() {
            return List.copyOf(sentMessages);
        }

        /**
         * Returns number of GitHub repository endpoint calls.
         *
         * @return GitHub fetch request count
         */
        long githubRequestCount() {
            return githubRequestCount.get();
        }

        /**
         * Stops the server.
         */
        @Override
        public void close() {
            server.stop(0);
        }

        private void handleRequest(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if (path.endsWith("/getUpdates")) {
                respondJson(exchange, telegramUpdatesResponse());
                return;
            }
            if (path.endsWith("/sendMessage")) {
                sentMessages.add(extractTelegramText(readBody(exchange)));
                respondJson(exchange, "{\"ok\":true,\"result\":{\"message_id\":1}}");
                return;
            }
            if (path.endsWith("/setMyCommands")) {
                respondJson(exchange, "{\"ok\":true,\"result\":true}");
                return;
            }
            if ("/repos/acme/repo".equals(path)) {
                githubRequestCount.incrementAndGet();
                respondJson(
                        exchange, "{\"updated_at\":\"" + githubUpdatedAt.get().toString() + "\"}");
                return;
            }
            respondJson(exchange, "{\"ok\":true,\"result\":[]}");
        }

        private String telegramUpdatesResponse() throws JsonProcessingException {
            List<Map<String, Object>> updates = new ArrayList<>();
            TelegramInboundUpdate update;
            while ((update = pendingUpdates.poll()) != null) {
                updates.add(update.toTelegramPayload());
            }
            return OBJECT_MAPPER.writeValueAsString(Map.of("ok", true, "result", updates));
        }

        private byte[] readBody(HttpExchange exchange) throws IOException {
            return exchange.getRequestBody().readAllBytes();
        }

        private String extractTelegramText(byte[] requestBody) {
            String rawBody = new String(requestBody, StandardCharsets.UTF_8);
            if (rawBody.isBlank()) {
                return "";
            }
            if (rawBody.startsWith("{")) {
                try {
                    return Optional.ofNullable(OBJECT_MAPPER.readTree(rawBody).get("text"))
                            .map(JsonNode::asText)
                            .orElse(rawBody);
                } catch (IOException ignored) {
                    return rawBody;
                }
            }

            Map<String, String> formValues = parseFormBody(rawBody);
            return formValues.getOrDefault("text", rawBody);
        }

        private Map<String, String> parseFormBody(String body) {
            if (body.isBlank()) {
                return Collections.emptyMap();
            }
            Map<String, String> values = new LinkedHashMap<>();
            Arrays.stream(body.split("&")).map(entry -> entry.split("=", 2)).forEach(parts -> {
                String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
                String value = parts.length == 2 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "";
                values.put(key, value);
            });
            return values;
        }

        private void respondJson(HttpExchange exchange, String body) throws IOException {
            byte[] payload = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, payload.length);
            try (OutputStream responseBody = exchange.getResponseBody()) {
                responseBody.write(payload);
            }
        }

        /**
         * Telegram update payload stored by fake server queue.
         *
         * @param updateId Telegram update identifier
         * @param chatId telegram chat identifier
         * @param text incoming user text
         */
        private record TelegramInboundUpdate(long updateId, long chatId, String text) {

            /**
             * Converts internal model into Telegram update JSON map.
             *
             * @return update payload map
             */
            Map<String, Object> toTelegramPayload() {
                return Map.of(
                        "update_id",
                        updateId,
                        "message",
                        Map.of(
                                "message_id",
                                updateId,
                                "date",
                                Instant.now().getEpochSecond(),
                                "chat",
                                Map.of("id", chatId, "type", "private"),
                                "text",
                                text));
            }
        }
    }
}
