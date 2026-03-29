package backend.academy.linktracker.bot.telegram.command.handlers;

import backend.academy.linktracker.bot.telegram.command.TelegramBotCommand;
import org.springframework.stereotype.Component;

/**
 * Handles `/start` command.
 */
@Component
@TelegramBotCommand(name = "start", description = "начало работы")
class StartCommandHandler implements TelegramCommandHandler {

    static final String START_REPLY = "Добро пожаловать! Используйте /help, чтобы посмотреть доступные команды.";

    /**
     * Returns welcome text for `/start`.
     *
     * @param messageText raw incoming message text
     * @return welcome message
     */
    @Override
    public String handle(String messageText) {
        return START_REPLY;
    }
}
