package com.linktracker.scrapper.application.update;

/**
 * Port for sending link updates to bot service.
 */
public interface BotNotificationSender {

    /**
     * Sends update payload to bot.
     *
     * @param notification update payload
     * @return true when bot accepted notification
     */
    boolean send(LinkUpdateNotification notification);
}
