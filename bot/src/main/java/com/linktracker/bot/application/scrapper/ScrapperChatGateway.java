package com.linktracker.bot.application.scrapper;

/**
 * Bot-side application port for scrapper chat operations.
 */
public interface ScrapperChatGateway {

    /**
     * Registers telegram chat in scrapper.
     *
     * @param chatId telegram chat identifier
     */
    void registerChat(long chatId);

    /**
     * Removes telegram chat from scrapper.
     *
     * @param chatId telegram chat identifier
     */
    void deleteChat(long chatId);
}
