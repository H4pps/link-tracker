package backend.academy.linktracker.bot.application.telegram;

/**
 * Application port for sending outbound Telegram messages.
 */
public interface TelegramOutboundSender {

    /**
     * Sends text message to chat.
     *
     * @param chatId telegram chat identifier
     * @param text outbound message text
     * @return true when Telegram API accepted message
     */
    boolean sendMessage(long chatId, String text);
}
