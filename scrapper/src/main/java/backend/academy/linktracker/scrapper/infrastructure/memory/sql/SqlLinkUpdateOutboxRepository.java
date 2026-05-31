package backend.academy.linktracker.scrapper.infrastructure.memory.sql;

import backend.academy.linktracker.scrapper.application.update.LinkUpdateOutboxEvent;
import backend.academy.linktracker.scrapper.application.update.LinkUpdateOutboxPayloadCodec;
import backend.academy.linktracker.scrapper.application.update.LinkUpdateOutboxRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * SQL implementation of Kafka outbox repository.
 */
@Repository
@ConditionalOnProperty(name = "app.database.access-type", havingValue = "SQL", matchIfMissing = true)
public class SqlLinkUpdateOutboxRepository implements LinkUpdateOutboxRepository {

    private final JdbcTemplate jdbcTemplate;

    public SqlLinkUpdateOutboxRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public void save(LinkUpdateOutboxEvent event) {
        jdbcTemplate.update(
                """
                INSERT INTO link_update_outbox (
                    payload_id,
                    payload_url,
                    payload_description,
                    payload_tg_chat_ids,
                    status,
                    attempts,
                    next_attempt_at,
                    created_at,
                    updated_at
                ) VALUES (?, ?, ?, ?, 'PENDING', 0, NOW(), NOW(), NOW())
                """,
                event.id(),
                event.url(),
                event.description(),
                LinkUpdateOutboxPayloadCodec.encodeChatIds(event.tgChatIds()));
    }

    @Override
    @Transactional
    public List<LinkUpdateOutboxEvent> findPending(Instant dueAt, int limit) {
        return jdbcTemplate.query(
                """
                SELECT
                    id,
                    payload_id,
                    payload_url,
                    payload_description,
                    payload_tg_chat_ids,
                    status,
                    attempts,
                    last_error,
                    next_attempt_at,
                    created_at,
                    updated_at,
                    sent_at
                FROM link_update_outbox
                WHERE status = 'PENDING'
                  AND next_attempt_at <= ?
                ORDER BY next_attempt_at ASC, id ASC
                LIMIT ?
                FOR UPDATE SKIP LOCKED
                """,
                (resultSet, rowNum) -> new LinkUpdateOutboxEvent(
                        resultSet.getLong("id"),
                        resultSet.getLong("payload_id"),
                        resultSet.getString("payload_url"),
                        resultSet.getString("payload_description"),
                        LinkUpdateOutboxPayloadCodec.decodeChatIds(resultSet.getString("payload_tg_chat_ids")),
                        LinkUpdateOutboxEvent.Status.valueOf(resultSet.getString("status")),
                        resultSet.getInt("attempts"),
                        resultSet.getString("last_error"),
                        timestampToInstant(resultSet.getTimestamp("next_attempt_at")),
                        timestampToInstant(resultSet.getTimestamp("created_at")),
                        timestampToInstant(resultSet.getTimestamp("updated_at")),
                        timestampToInstant(resultSet.getTimestamp("sent_at"))),
                Timestamp.from(dueAt),
                limit);
    }

    @Override
    @Transactional
    public void markSent(long outboxId) {
        jdbcTemplate.update("""
                UPDATE link_update_outbox
                SET status = 'SENT',
                    sent_at = NOW(),
                    updated_at = NOW(),
                    last_error = NULL
                WHERE id = ?
                """, outboxId);
    }

    @Override
    @Transactional
    public void markFailed(long outboxId, String lastError, Instant nextAttemptAt) {
        jdbcTemplate.update("""
                UPDATE link_update_outbox
                SET attempts = attempts + 1,
                    last_error = ?,
                    next_attempt_at = ?,
                    updated_at = NOW()
                WHERE id = ?
                """, lastError, Timestamp.from(nextAttemptAt), outboxId);
    }

    private Instant timestampToInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
