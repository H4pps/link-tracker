package backend.academy.linktracker.bot.configuration;

import backend.academy.linktracker.bot.properties.TelegramProperties;
import com.pengrad.telegrambot.TelegramBot;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares Spring beans related to Telegram client integration.
 */
@Configuration
public class TelegramConfiguration {

    /**
     * Builds a configured Telegram client from application properties.
     *
     * @param properties Telegram connection and polling properties
     * @return configured Telegram client
     */
    @Bean
    public TelegramBot telegramBot(TelegramProperties properties) {
        var builder = new TelegramBot.Builder(properties.getToken())
                .apiUrl(properties.getUrl())
                .updateListenerSleep(properties.getUpdateListenerSleep().toMillis());

        if (properties.isDebug()) {
            builder.debug();
        }

        return builder.build();
    }
}
