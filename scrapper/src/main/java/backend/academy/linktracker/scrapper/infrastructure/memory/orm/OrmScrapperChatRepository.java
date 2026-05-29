package backend.academy.linktracker.scrapper.infrastructure.memory.orm;

import backend.academy.linktracker.scrapper.application.chat.ScrapperChatRepository;
import backend.academy.linktracker.scrapper.infrastructure.memory.orm.entity.ChatEntity;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.database.access-type", havingValue = "ORM")
public class OrmScrapperChatRepository implements ScrapperChatRepository {

    private final EntityManager entityManager;

    @Override
    @Transactional
    public boolean register(long chatId) {
        if (exists(chatId)) {
            return false;
        }

        ChatEntity chat = new ChatEntity();
        chat.setChatId(chatId);
        entityManager.persist(chat);
        return true;
    }

    @Override
    @Transactional
    public boolean delete(long chatId) {
        int deleted = entityManager.createQuery("DELETE FROM ChatEntity chat WHERE chat.chatId = :chatId")
                .setParameter("chatId", chatId)
                .executeUpdate();
        return deleted > 0;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean exists(long chatId) {
        Long count = entityManager.createQuery(
                        "SELECT COUNT(chat) FROM ChatEntity chat WHERE chat.chatId = :chatId", Long.class)
                .setParameter("chatId", chatId)
                .getSingleResult();
        return count != null && count > 0;
    }
}
