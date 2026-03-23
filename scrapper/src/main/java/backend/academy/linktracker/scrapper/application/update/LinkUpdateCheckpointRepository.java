package backend.academy.linktracker.scrapper.application.update;

import java.time.Instant;
import java.util.Optional;

/**
 * Port for storing per-URL last-seen update checkpoints.
 */
public interface LinkUpdateCheckpointRepository {

    /**
     * Reads last seen timestamp for URL.
     *
     * @param url tracked URL
     * @return checkpoint value when present
     */
    Optional<Instant> findByUrl(String url);

    /**
     * Stores checkpoint for URL.
     *
     * @param url tracked URL
     * @param timestamp timestamp from external source
     */
    void save(String url, Instant timestamp);
}
