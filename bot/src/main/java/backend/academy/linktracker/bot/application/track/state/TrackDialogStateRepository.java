package backend.academy.linktracker.bot.application.track.state;

/**
 * Persistence boundary for chat-scoped `/track` dialog sessions.
 */
public interface TrackDialogStateRepository {

    /**
     * Reads chat session or returns default IDLE state when chat has no session.
     *
     * @param chatId telegram chat identifier
     * @return existing or default session
     */
    TrackDialogSession findByChatId(long chatId);

    /**
     * Stores chat session.
     *
     * @param chatId telegram chat identifier
     * @param session session snapshot
     */
    void save(long chatId, TrackDialogSession session);

    /**
     * Clears chat session back to IDLE.
     *
     * @param chatId telegram chat identifier
     */
    void clear(long chatId);
}
