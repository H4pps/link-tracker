package backend.academy.linktracker.scrapper.infrastructure.memory.sql;

import backend.academy.linktracker.scrapper.application.update.LinkUpdateCheckpointRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * SQL implementation of link update checkpoints repository.
 */
@Repository
@ConditionalOnProperty(name = "app.database.access-type", havingValue = "SQL", matchIfMissing = true)
public class SqlLinkUpdateCheckpointRepository implements LinkUpdateCheckpointRepository {

    private final JdbcTemplate jdbcTemplate;

    public SqlLinkUpdateCheckpointRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<Instant> findByUrl(String url) {
        return jdbcTemplate
                .query(
                        """
                        SELECT link_update_checkpoints.last_seen_external_updated_at
                        FROM link_update_checkpoints
                        JOIN links ON links.id = link_update_checkpoints.link_id
                        WHERE links.url = ?
                        """,
                        (resultSet, rowNum) -> {
                            Timestamp timestamp = resultSet.getTimestamp("last_seen_external_updated_at");
                            return timestamp == null ? null : timestamp.toInstant();
                        },
                        url)
                .stream()
                .findFirst();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void save(String url, Instant timestamp) {
        jdbcTemplate.update("""
                WITH tracked_link AS (
                    SELECT id
                    FROM links
                    WHERE url = ?
                )
                INSERT INTO link_update_checkpoints (link_id, last_seen_external_updated_at, checked_at)
                SELECT tracked_link.id, ?, NOW()
                FROM tracked_link
                ON CONFLICT (link_id) DO UPDATE SET
                    last_seen_external_updated_at = EXCLUDED.last_seen_external_updated_at,
                    checked_at = NOW()
                """, url, Timestamp.from(timestamp));
    }
}
