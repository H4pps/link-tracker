package backend.academy.linktracker.scrapper.application.update;

import java.util.List;

/**
 * Notification payload sent from scrapper to bot.
 *
 * @param id representative link identifier
 * @param url tracked URL
 * @param description optional message
 * @param tgChatIds target chat IDs
 */
public record LinkUpdateNotification(long id, String url, String description, List<Long> tgChatIds) {

    /**
     * Canonical constructor normalizing nullable collection.
     *
     * @param id representative link identifier
     * @param url tracked URL
     * @param description optional message
     * @param tgChatIds target chat IDs
     */
    public LinkUpdateNotification {
        tgChatIds = tgChatIds == null ? List.of() : List.copyOf(tgChatIds);
    }
}
