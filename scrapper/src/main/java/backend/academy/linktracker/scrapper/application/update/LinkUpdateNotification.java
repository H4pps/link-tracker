package backend.academy.linktracker.scrapper.application.update;

import java.util.List;

/**
 * Notification payload sent from scrapper to bot.
 *
 * @param id representative link identifier
 * @param url tracked URL
 * @param description optional message
 * @param author update author display name
 * @param tgChatIds target chat IDs
 */
public record LinkUpdateNotification(long id, String url, String description, String author, List<Long> tgChatIds) {

    /**
     * Canonical constructor normalizing nullable collection and author.
     *
     * @param id representative link identifier
     * @param url tracked URL
     * @param description optional message
     * @param author update author display name
     * @param tgChatIds target chat IDs
     */
    public LinkUpdateNotification {
        author = author == null ? "" : author;
        tgChatIds = tgChatIds == null ? List.of() : List.copyOf(tgChatIds);
    }

    /**
     * Convenience constructor for notifications without a known author.
     *
     * @param id representative link identifier
     * @param url tracked URL
     * @param description optional message
     * @param tgChatIds target chat IDs
     */
    public LinkUpdateNotification(long id, String url, String description, List<Long> tgChatIds) {
        this(id, url, description, "", tgChatIds);
    }
}
