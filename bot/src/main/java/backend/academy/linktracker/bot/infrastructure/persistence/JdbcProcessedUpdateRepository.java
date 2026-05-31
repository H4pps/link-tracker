package backend.academy.linktracker.bot.infrastructure.persistence;

import backend.academy.linktracker.bot.application.update.ProcessedUpdateRepository;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * JDBC-backed idempotency store using the {@code processed_link_updates} table.
 */
@Repository
public class JdbcProcessedUpdateRepository implements ProcessedUpdateRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcProcessedUpdateRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean isProcessed(UUID messageId) {
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM processed_link_updates WHERE message_id = ?)", Boolean.class, messageId);
        return Boolean.TRUE.equals(exists);
    }

    @Override
    public void markProcessed(UUID messageId) {
        jdbcTemplate.update(
                "INSERT INTO processed_link_updates (message_id) VALUES (?) ON CONFLICT (message_id) DO NOTHING",
                messageId);
    }
}
