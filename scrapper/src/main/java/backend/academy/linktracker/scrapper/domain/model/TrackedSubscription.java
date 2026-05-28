package backend.academy.linktracker.scrapper.domain.model;

import java.util.List;

/**
 * Immutable internal model for a tracked subscription stored in scrapper state.
 *
 * @param id generated link identifier
 * @param url tracked URL
 * @param tags immutable tag list
 * @param filters immutable filter list
 */
public record TrackedSubscription(long id, String url, List<String> tags, List<String> filters) {

    /**
     * Canonical constructor that stores immutable list copies.
     *
     * @param id generated link identifier
     * @param url tracked URL
     * @param tags tag list from command
     * @param filters filter list from command
     */
    public TrackedSubscription {
        tags = toImmutable(tags);
        filters = toImmutable(filters);
    }

    private static List<String> toImmutable(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return List.copyOf(values);
    }
}
