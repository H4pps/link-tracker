package com.linktracker.scrapper.infrastructure.cache.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linktracker.scrapper.application.chat.ScrapperChatRepository;
import com.linktracker.scrapper.application.chat.ScrapperChatUseCaseImpl;
import com.linktracker.scrapper.application.link.AddLinkCommand;
import com.linktracker.scrapper.application.link.LinkView;
import com.linktracker.scrapper.application.link.RemoveLinkCommand;
import com.linktracker.scrapper.application.link.ScrapperLinkRepository;
import com.linktracker.scrapper.application.link.ScrapperLinkUseCaseImpl;
import com.linktracker.scrapper.application.pagination.RepositoryPageRequest;
import com.linktracker.scrapper.domain.model.TrackedLinkSnapshot;
import com.linktracker.scrapper.domain.model.TrackedSubscription;
import com.linktracker.scrapper.infrastructure.memory.inmemory.InMemoryScrapperChatRepository;
import com.linktracker.scrapper.infrastructure.memory.inmemory.InMemoryScrapperLinkRepository;
import com.linktracker.scrapper.infrastructure.memory.inmemory.InMemoryScrapperStorage;
import com.linktracker.scrapper.logging.ScrapperLogger;
import com.linktracker.scrapper.properties.CacheProperties;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
@ExtendWith(MockitoExtension.class)
class RedisListLinksCacheIntegrationTest {

    @Container
    private static final GenericContainer<?> VALKEY =
            new GenericContainer<>(DockerImageName.parse("valkey/valkey:8-alpine")).withExposedPorts(6379);

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private ScrapperLogger scrapperLogger;

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void setUp() {
        RedisStandaloneConfiguration redisConfiguration =
                new RedisStandaloneConfiguration(VALKEY.getHost(), VALKEY.getMappedPort(6379));
        connectionFactory = new LettuceConnectionFactory(redisConfiguration);
        connectionFactory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        redisTemplate.execute((RedisCallback<Void>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });
    }

    @AfterEach
    void tearDown() {
        connectionFactory.destroy();
    }

    @Test
    void cacheMissStoresListLinksJsonInValkey() throws Exception {
        TestFixture fixture = fixture(Duration.ofMinutes(10));
        fixture.chatUseCase().registerChat(100L);
        fixture.linkUseCase()
                .addLink(
                        100L, new AddLinkCommand("https://github.com/acme/cache-json", List.of("work"), List.of("f1")));

        List<LinkView> links = fixture.linkUseCase().listLinks(100L);

        String payload = redisTemplate.opsForValue().get("100");
        JsonNode json = objectMapper.readTree(payload);
        assertThat(links).hasSize(1);
        assertThat(json.path("size").asInt()).isEqualTo(1);
        assertThat(json.path("links").get(0).path("id").asLong()).isEqualTo(1L);
        assertThat(json.path("links").get(0).path("url").asText()).isEqualTo("https://github.com/acme/cache-json");
        assertThat(json.path("links").get(0).path("tags").get(0).asText()).isEqualTo("work");
        assertThat(json.path("links").get(0).path("filters").get(0).asText()).isEqualTo("f1");
    }

    @Test
    void secondUnpagedListCallIsServedFromValkey() {
        TestFixture fixture = fixture(Duration.ofMinutes(10));
        fixture.chatUseCase().registerChat(200L);
        fixture.linkUseCase().addLink(200L, new AddLinkCommand("https://github.com/acme/cached", List.of(), List.of()));

        fixture.linkUseCase().listLinks(200L);
        fixture.linkUseCase().listLinks(200L);

        assertThat(fixture.linkRepository().findAllByChatIdCount()).isEqualTo(1);
    }

    @Test
    void ttlExpiryReloadsLinksFromRepository() {
        TestFixture fixture = fixture(Duration.ofMillis(150));
        fixture.chatUseCase().registerChat(300L);
        fixture.linkUseCase().addLink(300L, new AddLinkCommand("https://github.com/acme/ttl", List.of(), List.of()));
        fixture.linkUseCase().listLinks(300L);

        await().atMost(Duration.ofSeconds(3))
                .untilAsserted(() -> assertThat(redisTemplate.hasKey("300")).isFalse());
        fixture.linkUseCase().listLinks(300L);

        assertThat(fixture.linkRepository().findAllByChatIdCount()).isEqualTo(2);
    }

    @Test
    void addAndRemoveInvalidateStoredListLinksCache() {
        TestFixture fixture = fixture(Duration.ofMinutes(10));
        fixture.chatUseCase().registerChat(400L);
        fixture.linkUseCase().addLink(400L, new AddLinkCommand("https://github.com/acme/first", List.of(), List.of()));
        fixture.linkUseCase().listLinks(400L);
        assertThat(redisTemplate.hasKey("400")).isTrue();

        fixture.linkUseCase().addLink(400L, new AddLinkCommand("https://github.com/acme/second", List.of(), List.of()));
        assertThat(redisTemplate.hasKey("400")).isFalse();

        fixture.linkUseCase().listLinks(400L);
        assertThat(redisTemplate.hasKey("400")).isTrue();

        fixture.linkUseCase().removeLink(400L, new RemoveLinkCommand("https://github.com/acme/first"));
        assertThat(redisTemplate.hasKey("400")).isFalse();
    }

    private TestFixture fixture(Duration ttl) {
        InMemoryScrapperStorage storage = new InMemoryScrapperStorage();
        ScrapperChatRepository chatRepository = new InMemoryScrapperChatRepository(storage);
        CountingScrapperLinkRepository linkRepository =
                new CountingScrapperLinkRepository(new InMemoryScrapperLinkRepository(storage));
        CacheProperties cacheProperties = new CacheProperties();
        cacheProperties.getListLinks().setTtl(ttl);
        RedisListLinksCache cache =
                new RedisListLinksCache(redisTemplate, objectMapper, cacheProperties, scrapperLogger);
        return new TestFixture(
                new ScrapperChatUseCaseImpl(chatRepository, cache, scrapperLogger),
                new ScrapperLinkUseCaseImpl(chatRepository, linkRepository, cache, scrapperLogger),
                linkRepository);
    }

    private record TestFixture(
            ScrapperChatUseCaseImpl chatUseCase,
            ScrapperLinkUseCaseImpl linkUseCase,
            CountingScrapperLinkRepository linkRepository) {}

    private static final class CountingScrapperLinkRepository implements ScrapperLinkRepository {

        private final ScrapperLinkRepository delegate;
        private final AtomicInteger findAllByChatIdCount = new AtomicInteger();

        private CountingScrapperLinkRepository(ScrapperLinkRepository delegate) {
            this.delegate = delegate;
        }

        @Override
        public List<TrackedSubscription> findAllByChatId(long chatId, RepositoryPageRequest pageRequest) {
            findAllByChatIdCount.incrementAndGet();
            return delegate.findAllByChatId(chatId, pageRequest);
        }

        @Override
        public Optional<TrackedSubscription> addIfAbsent(
                long chatId, String url, List<String> tags, List<String> filters) {
            return delegate.addIfAbsent(chatId, url, tags, filters);
        }

        @Override
        public Optional<TrackedSubscription> remove(long chatId, String url) {
            return delegate.remove(chatId, url);
        }

        @Override
        public List<TrackedLinkSnapshot> findAllTrackedLinks(RepositoryPageRequest pageRequest) {
            return delegate.findAllTrackedLinks(pageRequest);
        }

        private int findAllByChatIdCount() {
            return findAllByChatIdCount.get();
        }
    }
}
