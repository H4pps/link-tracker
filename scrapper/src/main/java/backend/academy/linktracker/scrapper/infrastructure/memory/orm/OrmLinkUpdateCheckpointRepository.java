package backend.academy.linktracker.scrapper.infrastructure.memory.orm;

import backend.academy.linktracker.scrapper.application.update.LinkUpdateCheckpointRepository;
import backend.academy.linktracker.scrapper.infrastructure.memory.orm.entity.LinkEntity;
import backend.academy.linktracker.scrapper.infrastructure.memory.orm.entity.LinkUpdateCheckpointEntity;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.database.access-type", havingValue = "ORM")
public class OrmLinkUpdateCheckpointRepository implements LinkUpdateCheckpointRepository {

    private final EntityManager entityManager;

    @Override
    @Transactional(readOnly = true)
    public Optional<Instant> findByUrl(String url) {
        return entityManager.createQuery("""
                        SELECT checkpoint.lastSeenExternalUpdatedAt
                        FROM LinkUpdateCheckpointEntity checkpoint
                        JOIN checkpoint.link link
                        WHERE link.url = :url
                        """, Instant.class).setParameter("url", url).getResultList().stream()
                .findFirst();
    }

    @Override
    @Transactional
    public void save(String url, Instant timestamp) {
        LinkEntity link = entityManager
                .createQuery("SELECT link FROM LinkEntity link WHERE link.url = :url", LinkEntity.class)
                .setParameter("url", url)
                .getResultList()
                .stream()
                .findFirst()
                .orElse(null);
        if (link == null) {
            return;
        }

        LinkUpdateCheckpointEntity checkpoint = entityManager.find(LinkUpdateCheckpointEntity.class, link.getId());
        if (checkpoint == null) {
            checkpoint = new LinkUpdateCheckpointEntity();
            checkpoint.setLink(link);
            checkpoint.setLastSeenExternalUpdatedAt(timestamp);
            checkpoint.setCheckedAt(Instant.now());
            entityManager.persist(checkpoint);
            return;
        }

        checkpoint.setLastSeenExternalUpdatedAt(timestamp);
        checkpoint.setCheckedAt(Instant.now());
    }
}
