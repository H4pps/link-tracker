package com.linktracker.scrapper.infrastructure.memory.inmemory;

import com.linktracker.scrapper.application.update.LinkUpdateCheckpointRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/**
 * Thread-safe in-memory checkpoint store.
 */
@Repository
@ConditionalOnProperty(name = "app.database.access-type", havingValue = "MEMORY")
public class InMemoryLinkUpdateCheckpointRepository implements LinkUpdateCheckpointRepository {

    private final ConcurrentMap<String, Instant> checkpointsByUrl = new ConcurrentHashMap<>();

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<Instant> findByUrl(String url) {
        return Optional.ofNullable(checkpointsByUrl.get(url));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void save(String url, Instant timestamp) {
        checkpointsByUrl.put(url, timestamp);
    }
}
