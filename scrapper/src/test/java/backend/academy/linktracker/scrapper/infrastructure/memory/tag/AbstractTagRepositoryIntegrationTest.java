package backend.academy.linktracker.scrapper.infrastructure.memory.tag;

import static org.assertj.core.api.Assertions.assertThat;

import backend.academy.linktracker.scrapper.application.pagination.RepositoryPageRequest;
import backend.academy.linktracker.scrapper.application.chat.ScrapperChatRepository;
import backend.academy.linktracker.scrapper.application.link.ScrapperLinkRepository;
import backend.academy.linktracker.scrapper.application.tag.TagDeleteStatus;
import backend.academy.linktracker.scrapper.application.tag.TagRenameStatus;
import backend.academy.linktracker.scrapper.application.tag.TagRepository;
import backend.academy.linktracker.scrapper.domain.model.Tag;
import backend.academy.linktracker.scrapper.domain.model.TrackedSubscription;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
abstract class AbstractTagRepositoryIntegrationTest {

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
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TagRepository tagRepository;

    @Autowired
    private ScrapperChatRepository chatRepository;

    @Autowired
    private ScrapperLinkRepository linkRepository;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute(
                "TRUNCATE TABLE link_update_checkpoints, subscription_filters, subscription_tags, subscriptions, "
                        + "tags, links, chats RESTART IDENTITY CASCADE");
    }

    @Test
    void createFindAndDuplicateBehaviorsAreConsistent() {
        Tag created = tagRepository.create("team").orElseThrow();

        assertThat(tagRepository.create("team")).isEmpty();
        assertThat(tagRepository.findById(created.id())).contains(created);
        assertThat(tagRepository.findByName("team")).contains(created);
    }

    @Test
    void listUsesDeterministicOrderWithPaging() {
        Tag first = tagRepository.create("alpha").orElseThrow();
        Tag second = tagRepository.create("beta").orElseThrow();
        Tag third = tagRepository.create("gamma").orElseThrow();

        assertThat(tagRepository.findAll(RepositoryPageRequest.all())).containsExactly(first, second, third);
        assertThat(tagRepository.findAll(new RepositoryPageRequest(2, 1))).containsExactly(second, third);
    }

    @Test
    void renameHandlesSuccessMissingAndDuplicate() {
        Tag source = tagRepository.create("source").orElseThrow();
        tagRepository.create("target").orElseThrow();

        assertThat(tagRepository.rename(source.id(), "renamed").status()).isEqualTo(TagRenameStatus.RENAMED);
        assertThat(tagRepository.findById(source.id())).contains(new Tag(source.id(), "renamed"));
        assertThat(tagRepository.rename(999_999L, "any").status()).isEqualTo(TagRenameStatus.MISSING);
        assertThat(tagRepository.rename(source.id(), "target").status()).isEqualTo(TagRenameStatus.DUPLICATE);
    }

    @Test
    void deleteHandlesUnusedMissingAndAttachedTags() {
        Tag reusable = tagRepository.create("reusable").orElseThrow();
        chatRepository.register(1001L);
        TrackedSubscription tracked = linkRepository
                .addIfAbsent(1001L, "https://example.com/reuse", List.of("reusable"), List.of("f1"))
                .orElseThrow();

        assertThat(tracked.tags()).containsExactly("reusable");
        assertThat(tagRepository.findByName("reusable"))
                .contains(new Tag(reusable.id(), "reusable"));
        assertThat(tagNameRowCount("reusable")).isEqualTo(1);

        Tag deletable = tagRepository.create("deletable").orElseThrow();
        assertThat(tagRepository.deleteIfUnused(deletable.id()).status()).isEqualTo(TagDeleteStatus.DELETED);
        assertThat(tagRepository.findById(deletable.id())).isEmpty();

        assertThat(tagRepository.deleteIfUnused(999_999L).status()).isEqualTo(TagDeleteStatus.MISSING);
        assertThat(tagRepository.deleteIfUnused(reusable.id()).status()).isEqualTo(TagDeleteStatus.ATTACHED);
    }

    private int tagNameRowCount(String tagName) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tags WHERE name = ?", Integer.class, tagName);
        return count == null ? 0 : count;
    }
}
