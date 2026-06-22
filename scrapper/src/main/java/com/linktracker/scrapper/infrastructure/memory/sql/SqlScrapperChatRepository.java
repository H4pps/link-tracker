package com.linktracker.scrapper.infrastructure.memory.sql;

import com.linktracker.scrapper.application.chat.ScrapperChatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * SQL implementation of chat repository.
 */
@Repository
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.database.access-type", havingValue = "SQL", matchIfMissing = true)
public class SqlScrapperChatRepository implements ScrapperChatRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean register(long chatId) {
        int inserted =
                jdbcTemplate.update("INSERT INTO chats (chat_id) VALUES (?) ON CONFLICT (chat_id) DO NOTHING", chatId);
        return inserted > 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean delete(long chatId) {
        int deleted = jdbcTemplate.update("DELETE FROM chats WHERE chat_id = ?", chatId);
        return deleted > 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean exists(long chatId) {
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM chats WHERE chat_id = ?)", Boolean.class, chatId);
        return Boolean.TRUE.equals(exists);
    }
}
