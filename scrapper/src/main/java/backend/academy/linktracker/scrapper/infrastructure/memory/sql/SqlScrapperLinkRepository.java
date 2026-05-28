package backend.academy.linktracker.scrapper.infrastructure.memory.sql;

import backend.academy.linktracker.scrapper.application.repository.RepositoryPageRequest;
import backend.academy.linktracker.scrapper.application.repository.ScrapperLinkRepository;
import backend.academy.linktracker.scrapper.domain.model.TrackedLinkSnapshot;
import backend.academy.linktracker.scrapper.domain.model.TrackedSubscription;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
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
    @Transactional(readOnly = true)
    public List<TrackedSubscription> findAllByChatId(long chatId, RepositoryPageRequest pageRequest) {
        String sql = applyPagination(
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
                pageRequest);
        List<Object> arguments = new ArrayList<>();
        arguments.add(chatId);
        arguments.addAll(paginationArguments(pageRequest));
        List<SubscriptionRow> rows = jdbcTemplate.query(
                sql,
                (resultSet, rowNum) -> new SubscriptionRow(
                        resultSet.getLong("subscription_id"), resultSet.getLong("link_id"), resultSet.getString("url")),
                arguments.toArray());
        return toTrackedSubscriptions(rows);
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

        SubscriptionRow row = new SubscriptionRow(subscriptionId, linkId, url);
        return Optional.of(toTrackedSubscription(
                row,
                findTagsBySubscriptionIds(List.of(subscriptionId)),
                findFiltersBySubscriptionIds(List.of(subscriptionId))));
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

        TrackedSubscription removed = toTrackedSubscription(
                row,
                findTagsBySubscriptionIds(List.of(row.subscriptionId())),
                findFiltersBySubscriptionIds(List.of(row.subscriptionId())));
        jdbcTemplate.update("DELETE FROM subscriptions WHERE id = ?", row.subscriptionId());
        return Optional.of(removed);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public List<TrackedLinkSnapshot> findAllTrackedLinks(RepositoryPageRequest pageRequest) {
        String linksSql = applyPagination(
                """
                SELECT DISTINCT links.id AS link_id,
                                links.url AS url
                FROM links
                JOIN subscriptions ON subscriptions.link_id = links.id
                ORDER BY links.id
                """,
                pageRequest);
        List<LinkRow> linkRows = jdbcTemplate.query(
                linksSql,
                (resultSet, rowNum) -> new LinkRow(resultSet.getLong("link_id"), resultSet.getString("url")),
                paginationArguments(pageRequest).toArray());
        if (linkRows.isEmpty()) {
            return List.of();
        }

        List<Long> linkIds = linkRows.stream().map(LinkRow::linkId).toList();
        String placeholders = placeholders(linkIds.size());
        List<LinkChatRow> chatRows = jdbcTemplate.query(
                """
                SELECT links.id AS link_id,
                       chats.chat_id AS chat_id
                FROM links
                JOIN subscriptions ON subscriptions.link_id = links.id
                JOIN chats ON chats.id = subscriptions.chat_id
                WHERE links.id IN (%s)
                ORDER BY links.id, chats.chat_id
                """
                        .formatted(placeholders),
                (resultSet, rowNum) -> new LinkChatRow(resultSet.getLong("link_id"), resultSet.getLong("chat_id")),
                linkIds.toArray());

        Map<Long, List<Long>> chatIdsByLinkId = new HashMap<>();
        for (LinkChatRow row : chatRows) {
            chatIdsByLinkId.computeIfAbsent(row.linkId(), ignored -> new ArrayList<>()).add(row.chatId());
        }

        List<TrackedLinkSnapshot> snapshots = new ArrayList<>(linkRows.size());
        for (LinkRow row : linkRows) {
            snapshots.add(new TrackedLinkSnapshot(
                    row.linkId(), row.url(), List.copyOf(chatIdsByLinkId.getOrDefault(row.linkId(), List.of()))));
        }
        return snapshots;
    }

    private List<TrackedSubscription> toTrackedSubscriptions(List<SubscriptionRow> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Long> subscriptionIds = rows.stream().map(SubscriptionRow::subscriptionId).toList();
        Map<Long, List<String>> tagsBySubscriptionId = findTagsBySubscriptionIds(subscriptionIds);
        Map<Long, List<String>> filtersBySubscriptionId = findFiltersBySubscriptionIds(subscriptionIds);
        return rows.stream()
                .map(row -> toTrackedSubscription(row, tagsBySubscriptionId, filtersBySubscriptionId))
                .toList();
    }

    private TrackedSubscription toTrackedSubscription(
            SubscriptionRow row,
            Map<Long, List<String>> tagsBySubscriptionId,
            Map<Long, List<String>> filtersBySubscriptionId) {
        return new TrackedSubscription(
                row.linkId(),
                row.url(),
                List.copyOf(tagsBySubscriptionId.getOrDefault(row.subscriptionId(), List.of())),
                List.copyOf(filtersBySubscriptionId.getOrDefault(row.subscriptionId(), List.of())));
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

    private Map<Long, List<String>> findTagsBySubscriptionIds(List<Long> subscriptionIds) {
        if (subscriptionIds.isEmpty()) {
            return Map.of();
        }
        List<TagRow> rows = jdbcTemplate.query(
                """
                SELECT tags.name
                     , subscription_tags.subscription_id AS subscription_id
                FROM subscription_tags
                JOIN tags ON tags.id = subscription_tags.tag_id
                WHERE subscription_tags.subscription_id IN (%s)
                ORDER BY subscription_tags.subscription_id, tags.name
                """
                        .formatted(placeholders(subscriptionIds.size())),
                (resultSet, rowNum) -> new TagRow(resultSet.getLong("subscription_id"), resultSet.getString("name")),
                subscriptionIds.toArray());
        Map<Long, List<String>> tagsBySubscriptionId = new HashMap<>();
        for (TagRow row : rows) {
            tagsBySubscriptionId
                    .computeIfAbsent(row.subscriptionId(), ignored -> new ArrayList<>())
                    .add(row.tagName());
        }
        return tagsBySubscriptionId;
    }

    private Map<Long, List<String>> findFiltersBySubscriptionIds(List<Long> subscriptionIds) {
        if (subscriptionIds.isEmpty()) {
            return Map.of();
        }
        List<FilterRow> rows = jdbcTemplate.query(
                """
                SELECT value
                     , subscription_id
                FROM subscription_filters
                WHERE subscription_id IN (%s)
                ORDER BY subscription_id, id
                """
                        .formatted(placeholders(subscriptionIds.size())),
                (resultSet, rowNum) -> new FilterRow(resultSet.getLong("subscription_id"), resultSet.getString("value")),
                subscriptionIds.toArray());
        Map<Long, List<String>> filtersBySubscriptionId = new HashMap<>();
        for (FilterRow row : rows) {
            filtersBySubscriptionId
                    .computeIfAbsent(row.subscriptionId(), ignored -> new ArrayList<>())
                    .add(row.value());
        }
        return filtersBySubscriptionId;
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

    private String applyPagination(String sql, RepositoryPageRequest pageRequest) {
        StringBuilder builder = new StringBuilder(sql);
        if (pageRequest.bounded()) {
            builder.append("\nLIMIT ?");
        }
        if (pageRequest.offset() > 0) {
            builder.append("\nOFFSET ?");
        }
        return builder.toString();
    }

    private List<Object> paginationArguments(RepositoryPageRequest pageRequest) {
        List<Object> arguments = new ArrayList<>();
        if (pageRequest.bounded()) {
            arguments.add(pageRequest.limit());
        }
        if (pageRequest.offset() > 0) {
            arguments.add(pageRequest.offset());
        }
        return arguments;
    }

    private String placeholders(int size) {
        return String.join(", ", Collections.nCopies(size, "?"));
    }

    private record SubscriptionRow(long subscriptionId, long linkId, String url) {}

    private record TagRow(long subscriptionId, String tagName) {}

    private record FilterRow(long subscriptionId, String value) {}

    private record LinkRow(long linkId, String url) {}

    private record LinkChatRow(long linkId, long chatId) {}
}
