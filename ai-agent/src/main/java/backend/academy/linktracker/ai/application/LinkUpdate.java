package backend.academy.linktracker.ai.application;

import java.util.List;

/**
 * Decoded link update consumed from {@code link.raw-updates}, decoupled from the Avro transport type.
 *
 * @param id link update identifier
 * @param url tracked URL
 * @param description update text
 * @param author update author display name
 * @param tgChatIds target Telegram chat identifiers
 */
public record LinkUpdate(long id, String url, String description, String author, List<Long> tgChatIds) {

    /**
     * Canonical constructor normalizing nullable values.
     */
    public LinkUpdate {
        description = description == null ? "" : description;
        author = author == null ? "" : author;
        tgChatIds = tgChatIds == null ? List.of() : List.copyOf(tgChatIds);
    }
}
