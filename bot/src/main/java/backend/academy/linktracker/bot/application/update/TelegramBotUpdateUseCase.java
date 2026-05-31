package backend.academy.linktracker.bot.application.update;

import backend.academy.linktracker.bot.application.telegram.TelegramOutboundSender;
import backend.academy.linktracker.bot.logging.BotLogger;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Production implementation that delivers incoming scrapper updates to Telegram chats.
 */
@Component
@RequiredArgsConstructor
public class TelegramBotUpdateUseCase implements BotUpdateUseCase {

    private final TelegramOutboundSender outboundSender;
    private final BotLogger botLogger;

    /**
     * Sends update notification to every chat from payload.
     *
     * @param command validated link update payload
     */
    @Override
    public void processLinkUpdate(LinkUpdateCommand command) {
        String message = formatMessage(command);
        RuntimeException firstFailure = null;
        for (Long chatId : command.tgChatIds()) {
            if (chatId == null) {
                continue;
            }
            try {
                botLogger.logUpdateNotificationSendAttempt(chatId, command.url());
                boolean sent = outboundSender.sendMessage(chatId, message);
                botLogger.logUpdateNotificationSendResult(chatId, command.url(), sent);
                if (!sent && firstFailure == null) {
                    firstFailure = new IllegalStateException("Failed to deliver update to chat " + chatId);
                }
            } catch (RuntimeException exception) {
                botLogger.logApiRequestFailed("/updates", 500, "TELEGRAM_SEND_FAILED", exception);
                if (firstFailure == null) {
                    firstFailure = exception;
                }
            }
        }
        if (firstFailure != null) {
            throw firstFailure;
        }
    }

    private String formatMessage(LinkUpdateCommand command) {
        String base = "Обновление по ссылке: " + command.url();
        if (command.description() == null || command.description().isBlank()) {
            return base;
        }
        return base + System.lineSeparator() + command.description().strip();
    }
}
