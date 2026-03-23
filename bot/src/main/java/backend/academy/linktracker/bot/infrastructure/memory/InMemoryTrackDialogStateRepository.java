package backend.academy.linktracker.bot.infrastructure.memory;

import backend.academy.linktracker.bot.application.track.TrackDialogSession;
import backend.academy.linktracker.bot.application.track.TrackDialogStateRepository;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Repository;

/**
 * Thread-safe in-memory repository for chat dialog sessions.
 */
@Repository
public class InMemoryTrackDialogStateRepository implements TrackDialogStateRepository {

    private final ConcurrentMap<Long, TrackDialogSession> sessionsByChat = new ConcurrentHashMap<>();

    /**
     * {@inheritDoc}
     */
    @Override
    public TrackDialogSession findByChatId(long chatId) {
        return sessionsByChat.getOrDefault(chatId, TrackDialogSession.idle());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void save(long chatId, TrackDialogSession session) {
        sessionsByChat.put(chatId, session);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void clear(long chatId) {
        sessionsByChat.remove(chatId);
    }
}
