package com.linktracker.scrapper.domain.model;

import java.util.List;

/**
 * Scheduler read-model projection for globally tracked URL.
 *
 * @param id representative link identifier
 * @param url tracked URL
 * @param chatIds sorted chat IDs subscribed to URL
 */
public record TrackedLinkSnapshot(long id, String url, List<Long> chatIds) {

    /**
     * Canonical constructor normalizing nullable collections.
     *
     * @param id representative link identifier
     * @param url tracked URL
     * @param chatIds subscribed chat IDs
     */
    public TrackedLinkSnapshot {
        chatIds = chatIds == null ? List.of() : List.copyOf(chatIds);
    }
}
