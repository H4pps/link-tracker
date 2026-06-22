package com.linktracker.ai.application;

import java.util.List;

/**
 * Result of processing a raw update, ready to publish to {@code link.processed-updates}.
 *
 * @param id link update identifier
 * @param url tracked URL
 * @param description processed (possibly summarized) text
 * @param tgChatIds target Telegram chat identifiers
 * @param priority delivery priority (placeholder until prioritization is implemented)
 */
public record ProcessedUpdate(long id, String url, String description, List<Long> tgChatIds, String priority) {

    /**
     * Canonical constructor normalizing nullable values.
     */
    public ProcessedUpdate {
        description = description == null ? "" : description;
        priority = priority == null ? "NORMAL" : priority;
        tgChatIds = tgChatIds == null ? List.of() : List.copyOf(tgChatIds);
    }
}
