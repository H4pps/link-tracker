package backend.academy.linktracker.scrapper.application.link;

import java.util.List;
import java.util.Optional;

/**
 * Cache boundary for unpaged list-links responses.
 */
public interface ListLinksCache {

    /**
     * Reads cached links by chat id.
     *
     * @param chatId telegram chat identifier
     * @return cached links or empty when absent
     */
    Optional<List<LinkView>> get(long chatId);

    /**
     * Stores links by chat id.
     *
     * @param chatId telegram chat identifier
     * @param links sorted list-links payload
     */
    void put(long chatId, List<LinkView> links);

    /**
     * Removes cached links by chat id.
     *
     * @param chatId telegram chat identifier
     */
    void evict(long chatId);
}
