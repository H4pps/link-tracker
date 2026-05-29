package backend.academy.linktracker.scrapper.infrastructure.memory.sql;

import static org.assertj.core.api.Assertions.assertThat;

import backend.academy.linktracker.scrapper.ScrapperApplication;
import backend.academy.linktracker.scrapper.application.chat.ScrapperChatRepository;
import backend.academy.linktracker.scrapper.application.link.ScrapperLinkRepository;
import backend.academy.linktracker.scrapper.application.pagination.RepositoryPageRequest;
import backend.academy.linktracker.scrapper.application.tag.TagRepository;
import backend.academy.linktracker.scrapper.application.update.LinkUpdateCheckpointRepository;
import backend.academy.linktracker.scrapper.domain.model.TrackedLinkSnapshot;
import backend.academy.linktracker.scrapper.domain.model.TrackedSubscription;
import backend.academy.linktracker.scrapper.infrastructure.memory.inmemory.InMemoryLinkUpdateCheckpointRepository;
import backend.academy.linktracker.scrapper.infrastructure.memory.inmemory.InMemoryScrapperChatRepository;
import backend.academy.linktracker.scrapper.infrastructure.memory.inmemory.InMemoryScrapperLinkRepository;
import backend.academy.linktracker.scrapper.infrastructure.memory.inmemory.InMemoryScrapperStorage;
import backend.academy.linktracker.scrapper.infrastructure.memory.inmemory.InMemoryTagRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(
        classes = ScrapperApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
            "server.port=0",
            "app.grpc.server.port=0",
            "app.scheduler.enabled=false",
            "app.database.access-type=SQL",
            "app.github.token=test-github-token",
            "app.stackoverflow.key=test-stackoverflow-key",
            "app.stackoverflow.access-token=test-stackoverflow-access-token"
        })
class SqlScrapperRepositoriesIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18-alpine")
            .withDatabaseName("link_tracker")
            .withUsername("link_tracker")
            .withPassword("link_tracker");

    @DynamicPropertySource
    static void registerDatasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ScrapperChatRepository chatRepository;

    @Autowired
    private ScrapperLinkRepository linkRepository;

    @Autowired
    private LinkUpdateCheckpointRepository checkpointRepository;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute(
                "TRUNCATE TABLE link_update_checkpoints, subscription_filters, subscription_tags, subscriptions, "
                        + "tags, links, chats RESTART IDENTITY CASCADE");
    }

    @Test
    void chatRegisterDuplicateExistsAndDeleteAreConsistent() {
        assertThat(chatRepository.register(101L)).isTrue();
        assertThat(chatRepository.register(101L)).isFalse();
        assertThat(chatRepository.exists(101L)).isTrue();

        assertThat(chatRepository.delete(101L)).isTrue();
        assertThat(chatRepository.delete(101L)).isFalse();
        assertThat(chatRepository.exists(101L)).isFalse();
    }

    @Test
    void addListAndRemoveSubscriptionWithTagsAndFilters() {
        chatRepository.register(200L);

        Optional<TrackedSubscription> created =
                linkRepository.addIfAbsent(200L, "https://example.com/a", List.of("z", "a"), List.of("f2", "f1"));
        List<TrackedSubscription> listed = linkRepository.findAllByChatId(200L);
        Optional<TrackedSubscription> removed = linkRepository.remove(200L, "https://example.com/a");

        assertThat(created).isPresent();
        assertThat(listed)
                .containsExactly(new TrackedSubscription(
                        created.orElseThrow().id(), "https://example.com/a", List.of("a", "z"), List.of("f2", "f1")));
        assertThat(removed)
                .contains(new TrackedSubscription(
                        created.orElseThrow().id(), "https://example.com/a", List.of("a", "z"), List.of("f2", "f1")));
        assertThat(linkRepository.findAllByChatId(200L)).isEmpty();
        assertThat(linkRepository.remove(200L, "https://example.com/a")).isEmpty();
    }

    @Test
    void addIfAbsentReturnsEmptyForMissingChatAndDuplicateSubscription() {
        assertThat(linkRepository.addIfAbsent(301L, "https://example.com/missing", List.of("t"), List.of("f")))
                .isEmpty();

        chatRepository.register(301L);
        assertThat(linkRepository.addIfAbsent(301L, "https://example.com/dup", List.of("x"), List.of("f1")))
                .isPresent();
        assertThat(linkRepository.addIfAbsent(301L, "https://example.com/dup", List.of("y"), List.of("f2")))
                .isEmpty();
    }

    @Test
    void sameUrlForDifferentChatsUsesSingleGlobalLinkRow() {
        chatRepository.register(401L);
        chatRepository.register(402L);

        TrackedSubscription first = linkRepository
                .addIfAbsent(401L, "https://example.com/shared", List.of("team-a"), List.of("f1"))
                .orElseThrow();
        TrackedSubscription second = linkRepository
                .addIfAbsent(402L, "https://example.com/shared", List.of("team-b"), List.of("f2"))
                .orElseThrow();

        assertThat(first.id()).isEqualTo(second.id());
        assertThat(rowCount("links")).isEqualTo(1);
    }

    @Test
    void deletingChatCascadesSubscriptionsAndMetadataRows() {
        chatRepository.register(501L);
        linkRepository
                .addIfAbsent(501L, "https://example.com/cascade", List.of("alpha"), List.of("before"))
                .orElseThrow();

        assertThat(rowCount("subscriptions")).isEqualTo(1);
        assertThat(rowCount("subscription_tags")).isEqualTo(1);
        assertThat(rowCount("subscription_filters")).isEqualTo(1);

        assertThat(chatRepository.delete(501L)).isTrue();
        assertThat(rowCount("subscriptions")).isZero();
        assertThat(rowCount("subscription_tags")).isZero();
        assertThat(rowCount("subscription_filters")).isZero();
    }

    @Test
    void findAllTrackedLinksAggregatesByUrlWithSortedChatIds() {
        chatRepository.register(703L);
        chatRepository.register(701L);
        chatRepository.register(702L);

        linkRepository
                .addIfAbsent(703L, "https://example.com/shared", List.of("z"), List.of("f3"))
                .orElseThrow();
        linkRepository
                .addIfAbsent(701L, "https://example.com/shared", List.of("x"), List.of("f1"))
                .orElseThrow();
        TrackedSubscription unique = linkRepository
                .addIfAbsent(702L, "https://example.com/solo", List.of("y"), List.of("f2"))
                .orElseThrow();

        Map<String, TrackedLinkSnapshot> snapshotsByUrl = linkRepository.findAllTrackedLinks().stream()
                .collect(java.util.stream.Collectors.toMap(TrackedLinkSnapshot::url, snapshot -> snapshot));

        assertThat(snapshotsByUrl).hasSize(2);
        assertThat(snapshotsByUrl.get("https://example.com/shared").chatIds()).containsExactly(701L, 703L);
        assertThat(snapshotsByUrl.get("https://example.com/solo"))
                .isEqualTo(new TrackedLinkSnapshot(unique.id(), "https://example.com/solo", List.of(702L)));
    }

    @Test
    void findAllByChatIdSupportsDeterministicPagingAndUnpagedCompatibility() {
        chatRepository.register(900L);

        TrackedSubscription first = linkRepository
                .addIfAbsent(900L, "https://example.com/1", List.of("tag-2", "tag-1"), List.of("f-2", "f-1"))
                .orElseThrow();
        TrackedSubscription second = linkRepository
                .addIfAbsent(900L, "https://example.com/2", List.of("tag-4", "tag-3"), List.of("f-4", "f-3"))
                .orElseThrow();
        TrackedSubscription third = linkRepository
                .addIfAbsent(900L, "https://example.com/3", List.of("tag-6", "tag-5"), List.of("f-6", "f-5"))
                .orElseThrow();

        List<TrackedSubscription> secondPage = linkRepository.findAllByChatId(900L, new RepositoryPageRequest(2, 1));

        assertThat(secondPage)
                .containsExactly(
                        new TrackedSubscription(
                                second.id(), second.url(), List.of("tag-3", "tag-4"), List.of("f-4", "f-3")),
                        new TrackedSubscription(
                                third.id(), third.url(), List.of("tag-5", "tag-6"), List.of("f-6", "f-5")));
        assertThat(linkRepository.findAllByChatId(900L))
                .containsExactly(
                        new TrackedSubscription(
                                first.id(), first.url(), List.of("tag-1", "tag-2"), List.of("f-2", "f-1")),
                        new TrackedSubscription(
                                second.id(), second.url(), List.of("tag-3", "tag-4"), List.of("f-4", "f-3")),
                        new TrackedSubscription(
                                third.id(), third.url(), List.of("tag-5", "tag-6"), List.of("f-6", "f-5")));
    }

    @Test
    void findAllTrackedLinksSupportsDeterministicPagingAndUnpagedCompatibility() {
        chatRepository.register(910L);
        chatRepository.register(911L);
        chatRepository.register(912L);

        TrackedSubscription first = linkRepository
                .addIfAbsent(910L, "https://example.com/alpha", List.of("a"), List.of())
                .orElseThrow();
        linkRepository
                .addIfAbsent(912L, "https://example.com/alpha", List.of("z"), List.of())
                .orElseThrow();
        TrackedSubscription second = linkRepository
                .addIfAbsent(911L, "https://example.com/beta", List.of("b"), List.of())
                .orElseThrow();
        TrackedSubscription third = linkRepository
                .addIfAbsent(912L, "https://example.com/gamma", List.of("c"), List.of())
                .orElseThrow();

        List<TrackedLinkSnapshot> page = linkRepository.findAllTrackedLinks(new RepositoryPageRequest(2, 1));

        assertThat(page)
                .containsExactly(
                        new TrackedLinkSnapshot(second.id(), "https://example.com/beta", List.of(911L)),
                        new TrackedLinkSnapshot(third.id(), "https://example.com/gamma", List.of(912L)));
        assertThat(linkRepository.findAllTrackedLinks())
                .containsExactly(
                        new TrackedLinkSnapshot(first.id(), "https://example.com/alpha", List.of(910L, 912L)),
                        new TrackedLinkSnapshot(second.id(), "https://example.com/beta", List.of(911L)),
                        new TrackedLinkSnapshot(third.id(), "https://example.com/gamma", List.of(912L)));
    }

    @Test
    void checkpointSaveReadAndUpdateWorkOnlyForTrackedLinks() {
        Instant first = Instant.parse("2025-01-10T00:00:00Z");
        Instant second = Instant.parse("2025-02-10T00:00:00Z");

        checkpointRepository.save("https://example.com/missing", first);
        assertThat(checkpointRepository.findByUrl("https://example.com/missing"))
                .isEmpty();

        chatRepository.register(801L);
        linkRepository
                .addIfAbsent(801L, "https://example.com/tracked", List.of(), List.of())
                .orElseThrow();

        assertThat(checkpointRepository.findByUrl("https://example.com/tracked"))
                .isEmpty();

        checkpointRepository.save("https://example.com/tracked", first);
        assertThat(checkpointRepository.findByUrl("https://example.com/tracked"))
                .contains(first);

        checkpointRepository.save("https://example.com/tracked", second);
        assertThat(checkpointRepository.findByUrl("https://example.com/tracked"))
                .contains(second);
        assertThat(rowCount("link_update_checkpoints")).isEqualTo(1);
    }

    @Test
    void sqlModeLoadsSqlRepositoriesAndSkipsInMemoryBeans() {
        assertSingleRepositoryImplementation(ScrapperChatRepository.class, "SqlScrapperChatRepository");
        assertSingleRepositoryImplementation(ScrapperLinkRepository.class, "SqlScrapperLinkRepository");
        assertSingleRepositoryImplementation(TagRepository.class, "SqlTagRepository");
        assertSingleRepositoryImplementation(LinkUpdateCheckpointRepository.class, "SqlLinkUpdateCheckpointRepository");

        assertThat(applicationContext.getBeansOfType(InMemoryScrapperStorage.class))
                .isEmpty();
        assertThat(applicationContext.getBeansOfType(InMemoryScrapperChatRepository.class))
                .isEmpty();
        assertThat(applicationContext.getBeansOfType(InMemoryScrapperLinkRepository.class))
                .isEmpty();
        assertThat(applicationContext.getBeansOfType(InMemoryLinkUpdateCheckpointRepository.class))
                .isEmpty();
        assertThat(applicationContext.getBeansOfType(InMemoryTagRepository.class))
                .isEmpty();
    }

    private int rowCount(String tableName) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Integer.class);
        return count == null ? 0 : count;
    }

    private <T> void assertSingleRepositoryImplementation(Class<T> repositoryType, String expectedSimpleClassName) {
        List<String> implementationSimpleNames = applicationContext.getBeansOfType(repositoryType).values().stream()
                .map(bean -> AopUtils.getTargetClass(bean).getSimpleName())
                .sorted(Comparator.naturalOrder())
                .toList();

        assertThat(implementationSimpleNames).containsExactly(expectedSimpleClassName);
    }
}
