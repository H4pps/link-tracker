package backend.academy.linktracker.scrapper.infrastructure.memory.orm;

import backend.academy.linktracker.scrapper.application.repository.ScrapperLinkRepository;
import backend.academy.linktracker.scrapper.domain.model.TrackedLinkSnapshot;
import backend.academy.linktracker.scrapper.domain.model.TrackedSubscription;
import backend.academy.linktracker.scrapper.infrastructure.memory.orm.entity.ChatEntity;
import backend.academy.linktracker.scrapper.infrastructure.memory.orm.entity.LinkEntity;
import backend.academy.linktracker.scrapper.infrastructure.memory.orm.entity.SubscriptionEntity;
import backend.academy.linktracker.scrapper.infrastructure.memory.orm.entity.SubscriptionFilterEntity;
import backend.academy.linktracker.scrapper.infrastructure.memory.orm.entity.TagEntity;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
    public List<TrackedSubscription> findAllByChatId(long chatId) {
        List<SubscriptionEntity> subscriptions = entityManager
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
                .setParameter("chatId", chatId)
                .getResultList();

        return subscriptions.stream().map(this::toTrackedSubscription).toList();
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
    public List<TrackedLinkSnapshot> findAllTrackedLinks() {
        List<Object[]> rows = entityManager
                .createQuery(
                        """
                        SELECT link.id, link.url, chat.chatId
                        FROM SubscriptionEntity subscription
                        JOIN subscription.link link
                        JOIN subscription.chat chat
                        ORDER BY link.id, chat.chatId
                        """,
                        Object[].class)
                .getResultList();

        Map<Long, AggregatedTrackedLink> aggregatedByLinkId = new LinkedHashMap<>();
        for (Object[] row : rows) {
            Long linkId = (Long) row[0];
            String linkUrl = (String) row[1];
            Long subscribedChatId = (Long) row[2];
            AggregatedTrackedLink aggregated = aggregatedByLinkId.computeIfAbsent(
                    linkId, ignored -> new AggregatedTrackedLink(linkUrl, new ArrayList<>()));
            aggregated.chatIds().add(subscribedChatId);
        }

        return aggregatedByLinkId.entrySet().stream()
                .map(entry -> new TrackedLinkSnapshot(
                        entry.getKey(), entry.getValue().url(), entry.getValue().chatIds()))
                .toList();
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

    private record AggregatedTrackedLink(String url, List<Long> chatIds) {}
}
