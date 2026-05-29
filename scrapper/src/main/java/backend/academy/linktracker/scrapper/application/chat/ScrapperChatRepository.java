package backend.academy.linktracker.scrapper.application.chat;

/**
 * Repository boundary for chat registration state.
 */
public interface ScrapperChatRepository {

    /**
     * Registers chat in storage.
     *
     * @param chatId telegram chat identifier
     * @return true when chat was registered, false when it already existed
     */
    boolean register(long chatId);

    /**
     * Deletes chat from storage with all linked subscriptions.
     *
     * @param chatId telegram chat identifier
     * @return true when chat existed and was removed
     */
    boolean delete(long chatId);

    /**
     * Checks whether chat exists in storage.
     *
     * @param chatId telegram chat identifier
     * @return true when chat is registered
     */
    boolean exists(long chatId);
}
