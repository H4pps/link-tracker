package backend.academy.linktracker.scrapper.infrastructure.memory.migration;

import static org.assertj.core.api.Assertions.assertThat;

import backend.academy.linktracker.scrapper.ScrapperApplication;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
            "app.github.token=test-github-token",
            "app.stackoverflow.key=test-stackoverflow-key",
            "app.stackoverflow.access-token=test-stackoverflow-access-token"
        })
class ScrapperMigrationIntegrationTest {

    private static final Set<String> EXPECTED_TABLES = Set.of(
            "chats",
            "links",
            "subscriptions",
            "tags",
            "subscription_tags",
            "subscription_filters",
            "link_update_checkpoints",
            "link_update_outbox");

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

    @Test
    void liquibaseAppliesSchemaForScrapperStorage() {
        assertThat(existingTables()).containsExactlyInAnyOrderElementsOf(EXPECTED_TABLES);

        assertThat(indexExists("idx_subscriptions_link_id")).isTrue();
        assertThat(indexExists("idx_subscription_tags_tag_id")).isTrue();
        assertThat(indexExists("idx_link_update_checkpoints_checked_at")).isTrue();
        assertThat(indexExists("idx_link_update_outbox_polling")).isTrue();
        assertThat(indexExists("idx_link_update_outbox_sent_at")).isTrue();
        assertThat(columnIsRequired("links", "updated_at")).isTrue();
        assertThat(columnDefault("links", "updated_at")).containsIgnoringCase("now()");
        assertThat(columnIsRequired("link_update_outbox", "status")).isTrue();

        assertThat(constraintExists("uq_chats_chat_id")).isTrue();
        assertThat(constraintExists("uq_links_url")).isTrue();
        assertThat(constraintExists("uq_subscriptions_chat_link")).isTrue();
        assertThat(constraintExists("uq_tags_name")).isTrue();
        assertThat(constraintExists("uq_subscription_filters_subscription_id_value"))
                .isTrue();
        assertThat(constraintExists("pk_subscription_tags")).isTrue();
        assertThat(constraintExists("chk_link_update_outbox_status")).isTrue();

        assertThat(foreignKeyDeleteAction("fk_subscriptions_chat_id")).isEqualTo("c");
        assertThat(foreignKeyDeleteAction("fk_subscriptions_link_id")).isEqualTo("c");
        assertThat(foreignKeyDeleteAction("fk_subscription_tags_subscription_id"))
                .isEqualTo("c");
        assertThat(foreignKeyDeleteAction("fk_subscription_tags_tag_id")).isEqualTo("r");
        assertThat(foreignKeyDeleteAction("fk_subscription_filters_subscription_id"))
                .isEqualTo("c");
        assertThat(foreignKeyDeleteAction("fk_link_update_checkpoints_link_id")).isEqualTo("c");

        assertThat(appliedChangeSetCount("001-create-scrapper-storage")).isEqualTo(1);
        assertThat(appliedChangeSetCount("002-create-link-update-outbox")).isEqualTo(1);
        assertThat(appliedChangeSetCount("003-add-outbox-message-id")).isEqualTo(1);
        assertThat(columnIsRequired("link_update_outbox", "message_id")).isTrue();
        assertThat(indexExists("idx_link_update_outbox_message_id")).isTrue();
    }

    private List<String> existingTables() {
        return jdbcTemplate.queryForList("""
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_type = 'BASE TABLE'
                  AND table_name IN (
                    'chats',
                    'links',
                    'subscriptions',
                    'tags',
                    'subscription_tags',
                    'subscription_filters',
                    'link_update_checkpoints',
                    'link_update_outbox'
                  )
                """, String.class);
    }

    private boolean indexExists(String indexName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_indexes WHERE schemaname = 'public' AND indexname = ?",
                Integer.class,
                indexName);
        return count != null && count > 0;
    }

    private boolean constraintExists(String constraintName) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM pg_constraint constraint_def
                JOIN pg_namespace schema_def
                  ON schema_def.oid = constraint_def.connamespace
                WHERE schema_def.nspname = 'public'
                  AND constraint_def.conname = ?
                """, Integer.class, constraintName);
        return count != null && count > 0;
    }

    private boolean columnIsRequired(String tableName, String columnName) {
        String isNullable = jdbcTemplate.queryForObject("""
                SELECT is_nullable
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = ?
                  AND column_name = ?
                """, String.class, tableName, columnName);
        return "NO".equals(isNullable);
    }

    private String columnDefault(String tableName, String columnName) {
        return jdbcTemplate.queryForObject("""
                SELECT column_default
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = ?
                  AND column_name = ?
                """, String.class, tableName, columnName);
    }

    private String foreignKeyDeleteAction(String constraintName) {
        return jdbcTemplate.queryForObject("""
                SELECT constraint_def.confdeltype
                FROM pg_constraint constraint_def
                JOIN pg_namespace schema_def
                  ON schema_def.oid = constraint_def.connamespace
                WHERE schema_def.nspname = 'public'
                  AND constraint_def.conname = ?
                  AND constraint_def.contype = 'f'
                """, String.class, constraintName);
    }

    private int appliedChangeSetCount(String changeSetId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM databasechangelog WHERE id = ?", Integer.class, changeSetId);
        return count == null ? 0 : count;
    }
}
