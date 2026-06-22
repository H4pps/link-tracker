package com.linktracker.scrapper.application.chat;

/**
 * Application boundary for chat registration management.
 */
public interface ScrapperChatUseCase {

    /**
     * Registers chat for link tracking.
     *
     * @param chatId telegram chat identifier
     */
    void registerChat(long chatId);

    /**
     * Deletes chat and all related tracking state.
     *
     * @param chatId telegram chat identifier
     */
    void deleteChat(long chatId);
}
