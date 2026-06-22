package backend.academy.linktracker.scrapper.infrastructure.memory.orm;

import backend.academy.linktracker.scrapper.application.update.LinkUpdateOutboxEvent;
import backend.academy.linktracker.scrapper.application.update.LinkUpdateOutboxPayloadCodec;
import backend.academy.linktracker.scrapper.application.update.LinkUpdateOutboxRepository;
import backend.academy.linktracker.scrapper.infrastructure.memory.orm.entity.LinkUpdateOutboxEntity;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.database.access-type", havingValue = "ORM")
public class OrmLinkUpdateOutboxRepository implements LinkUpdateOutboxRepository {

    private final EntityManager entityManager;

    @Override
    @Transactional
    public void save(LinkUpdateOutboxEvent event) {
        LinkUpdateOutboxEntity entity = new LinkUpdateOutboxEntity();
        entity.setMessageId(event.messageId());
        entity.setPayloadId(event.id());
        entity.setPayloadUrl(event.url());
        entity.setPayloadDescription(event.description());
        entity.setPayloadAuthor(event.author());
        entity.setPayloadTgChatIds(LinkUpdateOutboxPayloadCodec.encodeChatIds(event.tgChatIds()));
        entity.setStatus(LinkUpdateOutboxEvent.Status.PENDING.name());
        entity.setAttempts(0);
        entity.setNextAttemptAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        entityManager.persist(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LinkUpdateOutboxEvent> findPending(Instant dueAt, int limit) {
        return entityManager
                .createQuery("""
                        SELECT outbox
                        FROM LinkUpdateOutboxEntity outbox
                        WHERE outbox.status = :status
                          AND outbox.nextAttemptAt <= :dueAt
                        ORDER BY outbox.nextAttemptAt ASC, outbox.id ASC
                        """, LinkUpdateOutboxEntity.class)
                .setParameter("status", LinkUpdateOutboxEvent.Status.PENDING.name())
                .setParameter("dueAt", dueAt)
                .setMaxResults(limit)
                .getResultList()
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public void markSent(long outboxId) {
        LinkUpdateOutboxEntity entity = entityManager.find(LinkUpdateOutboxEntity.class, outboxId);
        if (entity == null) {
            return;
        }
        entity.setStatus(LinkUpdateOutboxEvent.Status.SENT.name());
        entity.setSentAt(Instant.now());
        entity.setLastError(null);
        entity.setUpdatedAt(Instant.now());
    }

    @Override
    @Transactional
    public void markFailed(long outboxId, String lastError, Instant nextAttemptAt) {
        LinkUpdateOutboxEntity entity = entityManager.find(LinkUpdateOutboxEntity.class, outboxId);
        if (entity == null) {
            return;
        }
        entity.setAttempts(entity.getAttempts() + 1);
        entity.setLastError(lastError);
        entity.setNextAttemptAt(nextAttemptAt);
        entity.setUpdatedAt(Instant.now());
    }

    private LinkUpdateOutboxEvent toDomain(LinkUpdateOutboxEntity entity) {
        return new LinkUpdateOutboxEvent(
                entity.getId(),
                entity.getMessageId(),
                entity.getPayloadId(),
                entity.getPayloadUrl(),
                entity.getPayloadDescription(),
                entity.getPayloadAuthor(),
                LinkUpdateOutboxPayloadCodec.decodeChatIds(entity.getPayloadTgChatIds()),
                LinkUpdateOutboxEvent.Status.valueOf(entity.getStatus()),
                entity.getAttempts(),
                entity.getLastError(),
                entity.getNextAttemptAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getSentAt());
    }
}
