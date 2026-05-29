package backend.academy.linktracker.scrapper.infrastructure.memory.orm;

import backend.academy.linktracker.scrapper.application.pagination.RepositoryPageRequest;
import backend.academy.linktracker.scrapper.application.tag.TagDeleteResult;
import backend.academy.linktracker.scrapper.application.tag.TagDeleteStatus;
import backend.academy.linktracker.scrapper.application.tag.TagRenameResult;
import backend.academy.linktracker.scrapper.application.tag.TagRenameStatus;
import backend.academy.linktracker.scrapper.application.tag.TagRepository;
import backend.academy.linktracker.scrapper.domain.model.Tag;
import backend.academy.linktracker.scrapper.infrastructure.memory.orm.entity.TagEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * ORM implementation of standalone tag repository.
 */
@Repository
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.database.access-type", havingValue = "ORM")
public class OrmTagRepository implements TagRepository {

    private final EntityManager entityManager;

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public Optional<Tag> create(String name) {
        if (findEntityByName(name).isPresent()) {
            return Optional.empty();
        }
        TagEntity entity = new TagEntity();
        entity.setName(name);
        entityManager.persist(entity);
        entityManager.flush();
        return Optional.of(toTag(entity));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<Tag> findById(long id) {
        return findEntityById(id).map(this::toTag);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<Tag> findByName(String name) {
        return findEntityByName(name).map(this::toTag);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public List<Tag> findAll(RepositoryPageRequest pageRequest) {
        TypedQuery<TagEntity> query = entityManager.createQuery(
                """
                SELECT tag
                FROM TagEntity tag
                ORDER BY tag.id
                """,
                TagEntity.class);
        applyPaging(query, pageRequest);
        return query.getResultList().stream().map(this::toTag).toList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public TagRenameResult rename(long id, String name) {
        TagEntity entity = findEntityById(id).orElse(null);
        if (entity == null) {
            return new TagRenameResult(TagRenameStatus.MISSING);
        }
        Long duplicates = entityManager
                .createQuery(
                        """
                        SELECT COUNT(tag)
                        FROM TagEntity tag
                        WHERE tag.name = :name
                          AND tag.id <> :id
                        """,
                        Long.class)
                .setParameter("name", name)
                .setParameter("id", id)
                .getSingleResult();
        if (duplicates != null && duplicates > 0) {
            return new TagRenameResult(TagRenameStatus.DUPLICATE);
        }
        entity.setName(name);
        return new TagRenameResult(TagRenameStatus.RENAMED);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public TagDeleteResult deleteIfUnused(long id) {
        TagEntity entity = findEntityById(id).orElse(null);
        if (entity == null) {
            return new TagDeleteResult(TagDeleteStatus.MISSING);
        }
        Long attachedCount = entityManager
                .createQuery(
                        """
                        SELECT COUNT(subscription)
                        FROM SubscriptionEntity subscription
                        JOIN subscription.tags tag
                        WHERE tag.id = :tagId
                        """,
                        Long.class)
                .setParameter("tagId", id)
                .getSingleResult();
        if (attachedCount != null && attachedCount > 0) {
            return new TagDeleteResult(TagDeleteStatus.ATTACHED);
        }
        entityManager.remove(entity);
        return new TagDeleteResult(TagDeleteStatus.DELETED);
    }

    private Optional<TagEntity> findEntityById(long id) {
        return Optional.ofNullable(entityManager.find(TagEntity.class, id));
    }

    private Optional<TagEntity> findEntityByName(String name) {
        return entityManager
                .createQuery("SELECT tag FROM TagEntity tag WHERE tag.name = :name", TagEntity.class)
                .setParameter("name", name)
                .getResultList()
                .stream()
                .findFirst();
    }

    private Tag toTag(TagEntity entity) {
        return new Tag(entity.getId(), entity.getName());
    }

    private <T> void applyPaging(TypedQuery<T> query, RepositoryPageRequest pageRequest) {
        query.setFirstResult((int) Math.min(pageRequest.offset(), Integer.MAX_VALUE));
        if (pageRequest.bounded()) {
            query.setMaxResults(pageRequest.limit());
        }
    }
}
