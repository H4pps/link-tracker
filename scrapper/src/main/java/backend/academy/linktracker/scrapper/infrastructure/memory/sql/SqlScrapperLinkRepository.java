package backend.academy.linktracker.scrapper.infrastructure.memory.sql;

import backend.academy.linktracker.scrapper.application.repository.ScrapperLinkRepository;
import backend.academy.linktracker.scrapper.domain.model.TrackedLinkSnapshot;
import backend.academy.linktracker.scrapper.domain.model.TrackedSubscription;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * SQL implementation of link repository.
 */
@Repository
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.database.access-type", havingValue = "SQL", matchIfMissing = true)
public class SqlScrapperLinkRepository implements ScrapperLinkRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * {@inheritDoc}
     */
    @Override
    public List<TrackedSubscription> findAllByChatId(long chatId) {
        List<SubscriptionRow> rows = jdbcTemplate.query(
                """
                SELECT subscriptions.id AS subscription_id,
                       links.id AS link_id,
                       links.url AS url
                FROM subscriptions
                JOIN chats ON chats.id = subscriptions.chat_id
                JOIN links ON links.id = subscriptions.link_id
                WHERE chats.chat_id = ?
                ORDER BY links.id
                """,
                (resultSet, rowNum) -> new SubscriptionRow(
                        resultSet.getLong("subscription_id"), resultSet.getLong("link_id"), resultSet.getString("url")),
                chatId);

        return rows.stream().map(this::toTrackedSubscription).toList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public Optional<TrackedSubscription> addIfAbsent(long chatId, String url, List<String> tags, List<String> filters) {
        Long internalChatId = findInternalChatId(chatId).orElse(null);
        if (internalChatId == null) {
            return Optional.empty();
        }

        long linkId = resolveLinkId(url);
        Long subscriptionId = insertSubscription(internalChatId, linkId).orElse(null);
        if (subscriptionId == null) {
            return Optional.empty();
        }

        for (String tag : normalize(tags).stream().distinct().toList()) {
            long tagId = resolveTagId(tag);
            jdbcTemplate.update("""
                    INSERT INTO subscription_tags (subscription_id, tag_id)
                    VALUES (?, ?)
                    ON CONFLICT (subscription_id, tag_id) DO NOTHING
                    """, subscriptionId, tagId);
        }

        for (String filter : normalize(filters).stream().distinct().toList()) {
            jdbcTemplate.update("""
                    INSERT INTO subscription_filters (subscription_id, value)
                    VALUES (?, ?)
                    ON CONFLICT (subscription_id, value) DO NOTHING
                    """, subscriptionId, filter);
        }

        return Optional.of(toTrackedSubscription(new SubscriptionRow(subscriptionId, linkId, url)));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public Optional<TrackedSubscription> remove(long chatId, String url) {
        SubscriptionRow row = findSubscriptionByChatAndUrl(chatId, url).orElse(null);
        if (row == null) {
            return Optional.empty();
        }

        TrackedSubscription removed = toTrackedSubscription(row);
        jdbcTemplate.update("DELETE FROM subscriptions WHERE id = ?", row.subscriptionId());
        return Optional.of(removed);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<TrackedLinkSnapshot> findAllTrackedLinks() {
        List<TrackedLinkRow> rows = jdbcTemplate.query(
                """
                SELECT links.id AS link_id,
                       links.url AS url,
                       chats.chat_id AS chat_id
                FROM links
                JOIN subscriptions ON subscriptions.link_id = links.id
                JOIN chats ON chats.id = subscriptions.chat_id
                ORDER BY links.id, chats.chat_id
                """,
                (resultSet, rowNum) -> new TrackedLinkRow(
                        resultSet.getLong("link_id"), resultSet.getString("url"), resultSet.getLong("chat_id")));

        Map<Long, AggregatedTrackedLink> aggregatedByLinkId = new LinkedHashMap<>();
        for (TrackedLinkRow row : rows) {
            AggregatedTrackedLink aggregated = aggregatedByLinkId.computeIfAbsent(
                    row.linkId(), ignored -> new AggregatedTrackedLink(row.url(), new ArrayList<>()));
            aggregated.chatIds().add(row.chatId());
        }

        return aggregatedByLinkId.entrySet().stream()
                .map(entry -> new TrackedLinkSnapshot(
                        entry.getKey(), entry.getValue().url(), entry.getValue().chatIds()))
                .toList();
    }

    private TrackedSubscription toTrackedSubscription(SubscriptionRow row) {
        return new TrackedSubscription(
                row.linkId(),
                row.url(),
                findTagsBySubscriptionId(row.subscriptionId()),
                findFiltersBySubscriptionId(row.subscriptionId()));
    }

    private Optional<Long> findInternalChatId(long externalChatId) {
        List<Long> ids = jdbcTemplate.query(
                "SELECT id FROM chats WHERE chat_id = ?",
                (resultSet, rowNum) -> resultSet.getLong("id"),
                externalChatId);
        return ids.stream().findFirst();
    }

    private long resolveLinkId(String url) {
        jdbcTemplate.update("INSERT INTO links (url) VALUES (?) ON CONFLICT (url) DO NOTHING", url);
        return jdbcTemplate.queryForObject("SELECT id FROM links WHERE url = ?", Long.class, url);
    }

    private Optional<Long> insertSubscription(long internalChatId, long linkId) {
        int inserted = jdbcTemplate.update("""
                INSERT INTO subscriptions (chat_id, link_id)
                VALUES (?, ?)
                ON CONFLICT (chat_id, link_id) DO NOTHING
                """, internalChatId, linkId);
        if (inserted == 0) {
            return Optional.empty();
        }

        Long subscriptionId = jdbcTemplate.queryForObject(
                "SELECT id FROM subscriptions WHERE chat_id = ? AND link_id = ?", Long.class, internalChatId, linkId);
        return Optional.ofNullable(subscriptionId);
    }

    private long resolveTagId(String tagName) {
        jdbcTemplate.update("INSERT INTO tags (name) VALUES (?) ON CONFLICT (name) DO NOTHING", tagName);
        return jdbcTemplate.queryForObject("SELECT id FROM tags WHERE name = ?", Long.class, tagName);
    }

    private List<String> findTagsBySubscriptionId(long subscriptionId) {
        return jdbcTemplate.query("""
                SELECT tags.name
                FROM subscription_tags
                JOIN tags ON tags.id = subscription_tags.tag_id
                WHERE subscription_tags.subscription_id = ?
                ORDER BY tags.name
                """, (resultSet, rowNum) -> resultSet.getString("name"), subscriptionId);
    }

    private List<String> findFiltersBySubscriptionId(long subscriptionId) {
        return jdbcTemplate.query("""
                SELECT value
                FROM subscription_filters
                WHERE subscription_id = ?
                ORDER BY id
                """, (resultSet, rowNum) -> resultSet.getString("value"), subscriptionId);
    }

    private Optional<SubscriptionRow> findSubscriptionByChatAndUrl(long chatId, String url) {
        List<SubscriptionRow> rows = jdbcTemplate.query(
                """
                SELECT subscriptions.id AS subscription_id,
                       links.id AS link_id,
                       links.url AS url
                FROM subscriptions
                JOIN chats ON chats.id = subscriptions.chat_id
                JOIN links ON links.id = subscriptions.link_id
                WHERE chats.chat_id = ?
                  AND links.url = ?
                """,
                (resultSet, rowNum) -> new SubscriptionRow(
                        resultSet.getLong("subscription_id"), resultSet.getLong("link_id"), resultSet.getString("url")),
                chatId,
                url);
        return rows.stream().findFirst();
    }

    private List<String> normalize(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values;
    }

    private record SubscriptionRow(long subscriptionId, long linkId, String url) {}

    private record TrackedLinkRow(long linkId, String url, long chatId) {}

    private record AggregatedTrackedLink(String url, List<Long> chatIds) {}
}
