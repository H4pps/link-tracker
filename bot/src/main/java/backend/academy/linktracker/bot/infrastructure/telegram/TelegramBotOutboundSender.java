package backend.academy.linktracker.bot.infrastructure.telegram;

import backend.academy.linktracker.bot.application.telegram.TelegramOutboundSender;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.request.SendMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Telegram outbound sender implementation backed by {@link TelegramBot}.
 */
@Component
@RequiredArgsConstructor
public class TelegramBotOutboundSender implements TelegramOutboundSender {

    private final TelegramBot telegramBot;

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean sendMessage(long chatId, String text) {
        return telegramBot.execute(new SendMessage(chatId, text)).isOk();
    }
}
