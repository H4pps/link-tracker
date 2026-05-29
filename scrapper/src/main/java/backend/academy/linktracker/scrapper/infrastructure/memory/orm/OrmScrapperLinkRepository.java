package backend.academy.linktracker.scrapper.infrastructure.memory.orm;

import backend.academy.linktracker.scrapper.application.pagination.RepositoryPageRequest;
import backend.academy.linktracker.scrapper.application.link.ScrapperLinkRepository;
import backend.academy.linktracker.scrapper.domain.model.TrackedLinkSnapshot;
import backend.academy.linktracker.scrapper.domain.model.TrackedSubscription;
import backend.academy.linktracker.scrapper.infrastructure.memory.orm.entity.ChatEntity;
import backend.academy.linktracker.scrapper.infrastructure.memory.orm.entity.LinkEntity;
import backend.academy.linktracker.scrapper.infrastructure.memory.orm.entity.SubscriptionEntity;
import backend.academy.linktracker.scrapper.infrastructure.memory.orm.entity.SubscriptionFilterEntity;
import backend.academy.linktracker.scrapper.infrastructure.memory.orm.entity.TagEntity;
import backend.academy.linktracker.scrapper.infrastructure.memory.orm.projection.OrmLinkChatRow;
import backend.academy.linktracker.scrapper.infrastructure.memory.orm.projection.OrmLinkRow;
import backend.academy.linktracker.scrapper.infrastructure.memory.orm.projection.OrmSubscriptionFilterRow;
import backend.academy.linktracker.scrapper.infrastructure.memory.orm.projection.OrmSubscriptionTagRow;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.database.access-type", havingValue = "ORM")
public class OrmScrapperLinkRepository implements ScrapperLinkRepository {

    private final EntityManager entityManager;

    @Override
    @Transactional(readOnly = true)
    public List<TrackedSubscription> findAllByChatId(long chatId, RepositoryPageRequest pageRequest) {
        TypedQuery<SubscriptionEntity> query = entityManager
                .createQuery(
                        """
                        SELECT subscription
                        FROM SubscriptionEntity subscription
                        JOIN FETCH subscription.link link
                        JOIN subscription.chat chat
                WHERE chat.chatId = :chatId
                ORDER BY link.id
                """,
                        SubscriptionEntity.class)
                .setParameter("chatId", chatId);
        applyPaging(query, pageRequest);
        List<SubscriptionEntity> subscriptions = query.getResultList();
        if (subscriptions.isEmpty()) {
            return List.of();
        }

        List<Long> subscriptionIds = subscriptions.stream().map(SubscriptionEntity::getId).toList();
        Map<Long, List<String>> tagsBySubscriptionId = findTagsBySubscriptionIds(subscriptionIds);
        Map<Long, List<String>> filtersBySubscriptionId = findFiltersBySubscriptionIds(subscriptionIds);
        return subscriptions.stream()
                .map(subscription -> toTrackedSubscription(subscription, tagsBySubscriptionId, filtersBySubscriptionId))
                .toList();
    }

    @Override
    @Transactional
    public Optional<TrackedSubscription> addIfAbsent(long chatId, String url, List<String> tags, List<String> filters) {
        ChatEntity chat = findChatByExternalId(chatId).orElse(null);
        if (chat == null) {
            return Optional.empty();
        }

        LinkEntity link = resolveLink(url);
        if (hasSubscription(chat.getId(), link.getId())) {
            return Optional.empty();
        }

        SubscriptionEntity subscription = new SubscriptionEntity();
        subscription.setChat(chat);
        subscription.setLink(link);

        for (String tag : new LinkedHashSet<>(normalize(tags))) {
            subscription.getTags().add(resolveTag(tag));
        }

        for (String filter : new LinkedHashSet<>(normalize(filters))) {
            SubscriptionFilterEntity filterEntity = new SubscriptionFilterEntity();
            filterEntity.setSubscription(subscription);
            filterEntity.setValue(filter);
            subscription.getFilters().add(filterEntity);
        }

        entityManager.persist(subscription);
        entityManager.flush();
        return Optional.of(toTrackedSubscription(subscription));
    }

    @Override
    @Transactional
    public Optional<TrackedSubscription> remove(long chatId, String url) {
        SubscriptionEntity subscription = findSubscriptionByChatAndUrl(chatId, url).orElse(null);
        if (subscription == null) {
            return Optional.empty();
        }

        TrackedSubscription removed = toTrackedSubscription(subscription);
        entityManager.remove(subscription);
        return Optional.of(removed);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TrackedLinkSnapshot> findAllTrackedLinks(RepositoryPageRequest pageRequest) {
        TypedQuery<OrmLinkRow> query = entityManager.createQuery(
                """
                SELECT DISTINCT new backend.academy.linktracker.scrapper.infrastructure.memory.orm.projection.OrmLinkRow(
                    link.id,
                    link.url
                )
                FROM SubscriptionEntity subscription
                JOIN subscription.link link
                ORDER BY link.id
                """,
                OrmLinkRow.class);
        applyPaging(query, pageRequest);
        List<OrmLinkRow> linkRows = query.getResultList();
        if (linkRows.isEmpty()) {
            return List.of();
        }

        List<Long> linkIds = linkRows.stream().map(OrmLinkRow::linkId).toList();
        Map<Long, List<Long>> chatIdsByLinkId = findChatIdsByLinkIds(linkIds);
        List<TrackedLinkSnapshot> snapshots = new ArrayList<>(linkRows.size());
        for (OrmLinkRow row : linkRows) {
            snapshots.add(new TrackedLinkSnapshot(
                    row.linkId(), row.url(), List.copyOf(chatIdsByLinkId.getOrDefault(row.linkId(), List.of()))));
        }
        return snapshots;
    }

    private TrackedSubscription toTrackedSubscription(
            SubscriptionEntity subscription,
            Map<Long, List<String>> tagsBySubscriptionId,
            Map<Long, List<String>> filtersBySubscriptionId) {
        Long subscriptionId = subscription.getId();
        return new TrackedSubscription(
                subscription.getLink().getId(),
                subscription.getLink().getUrl(),
                List.copyOf(tagsBySubscriptionId.getOrDefault(subscriptionId, List.of())),
                List.copyOf(filtersBySubscriptionId.getOrDefault(subscriptionId, List.of())));
    }

    private TrackedSubscription toTrackedSubscription(SubscriptionEntity subscription) {
        List<String> tags = subscription.getTags().stream().map(TagEntity::getName).sorted().toList();
        List<String> filters =
                subscription.getFilters().stream().map(SubscriptionFilterEntity::getValue).toList();
        return new TrackedSubscription(
                subscription.getLink().getId(), subscription.getLink().getUrl(), tags, filters);
    }

    private Optional<ChatEntity> findChatByExternalId(long externalChatId) {
        return entityManager
                .createQuery("SELECT chat FROM ChatEntity chat WHERE chat.chatId = :chatId", ChatEntity.class)
                .setParameter("chatId", externalChatId)
                .getResultList()
                .stream()
                .findFirst();
    }

    private LinkEntity resolveLink(String url) {
        return entityManager
                .createQuery("SELECT link FROM LinkEntity link WHERE link.url = :url", LinkEntity.class)
                .setParameter("url", url)
                .getResultList()
                .stream()
                .findFirst()
                .orElseGet(() -> {
                    LinkEntity link = new LinkEntity();
                    link.setUrl(url);
                    entityManager.persist(link);
                    return link;
                });
    }

    private TagEntity resolveTag(String name) {
        return entityManager
                .createQuery("SELECT tag FROM TagEntity tag WHERE tag.name = :name", TagEntity.class)
                .setParameter("name", name)
                .getResultList()
                .stream()
                .findFirst()
                .orElseGet(() -> {
                    TagEntity tag = new TagEntity();
                    tag.setName(name);
                    entityManager.persist(tag);
                    return tag;
                });
    }

    private boolean hasSubscription(long internalChatId, long linkId) {
        Long count = entityManager
                .createQuery(
                        """
                        SELECT COUNT(subscription)
                        FROM SubscriptionEntity subscription
                        WHERE subscription.chat.id = :chatId
                          AND subscription.link.id = :linkId
                        """,
                        Long.class)
                .setParameter("chatId", internalChatId)
                .setParameter("linkId", linkId)
                .getSingleResult();
        return count != null && count > 0;
    }

    private Optional<SubscriptionEntity> findSubscriptionByChatAndUrl(long chatId, String url) {
        return entityManager
                .createQuery(
                        """
                        SELECT subscription
                        FROM SubscriptionEntity subscription
                        JOIN subscription.chat chat
                        JOIN subscription.link link
                        WHERE chat.chatId = :chatId
                          AND link.url = :url
                        """,
                        SubscriptionEntity.class)
                .setParameter("chatId", chatId)
                .setParameter("url", url)
                .getResultList()
                .stream()
                .findFirst();
    }

    private List<String> normalize(List<String> values) {
        return values == null ? List.of() : values;
    }

    private Map<Long, List<String>> findTagsBySubscriptionIds(List<Long> subscriptionIds) {
        if (subscriptionIds.isEmpty()) {
            return Map.of();
        }
        List<OrmSubscriptionTagRow> rows = entityManager
                .createQuery(
                        """
                        SELECT new backend.academy.linktracker.scrapper.infrastructure.memory.orm.projection.OrmSubscriptionTagRow(
                            subscription.id,
                            tag.name
                        )
                        FROM SubscriptionEntity subscription
                        JOIN subscription.tags tag
                        WHERE subscription.id IN :subscriptionIds
                        ORDER BY subscription.id, tag.name
                        """,
                        OrmSubscriptionTagRow.class)
                .setParameter("subscriptionIds", subscriptionIds)
                .getResultList();
        Map<Long, List<String>> tagsBySubscriptionId = new HashMap<>();
        for (OrmSubscriptionTagRow row : rows) {
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
        List<OrmSubscriptionFilterRow> rows = entityManager
                .createQuery(
                        """
                        SELECT new backend.academy.linktracker.scrapper.infrastructure.memory.orm.projection.OrmSubscriptionFilterRow(
                            filter.subscription.id,
                            filter.value
                        )
                        FROM SubscriptionFilterEntity filter
                        WHERE filter.subscription.id IN :subscriptionIds
                        ORDER BY filter.subscription.id, filter.id
                        """,
                        OrmSubscriptionFilterRow.class)
                .setParameter("subscriptionIds", subscriptionIds)
                .getResultList();
        Map<Long, List<String>> filtersBySubscriptionId = new HashMap<>();
        for (OrmSubscriptionFilterRow row : rows) {
            filtersBySubscriptionId
                    .computeIfAbsent(row.subscriptionId(), ignored -> new ArrayList<>())
                    .add(row.value());
        }
        return filtersBySubscriptionId;
    }

    private Map<Long, List<Long>> findChatIdsByLinkIds(List<Long> linkIds) {
        List<OrmLinkChatRow> rows = entityManager
                .createQuery(
                        """
                        SELECT new backend.academy.linktracker.scrapper.infrastructure.memory.orm.projection.OrmLinkChatRow(
                            link.id,
                            chat.chatId
                        )
                        FROM SubscriptionEntity subscription
                        JOIN subscription.link link
                        JOIN subscription.chat chat
                        WHERE link.id IN :linkIds
                        ORDER BY link.id, chat.chatId
                        """,
                        OrmLinkChatRow.class)
                .setParameter("linkIds", linkIds)
                .getResultList();
        Map<Long, List<Long>> chatIdsByLinkId = new HashMap<>();
        for (OrmLinkChatRow row : rows) {
            chatIdsByLinkId
                    .computeIfAbsent(row.linkId(), ignored -> new ArrayList<>())
                    .add(row.chatId());
        }
        return chatIdsByLinkId;
    }

    private <T> void applyPaging(TypedQuery<T> query, RepositoryPageRequest pageRequest) {
        query.setFirstResult((int) Math.min(pageRequest.offset(), Integer.MAX_VALUE));
        if (pageRequest.bounded()) {
            query.setMaxResults(pageRequest.limit());
        }
    }
}
