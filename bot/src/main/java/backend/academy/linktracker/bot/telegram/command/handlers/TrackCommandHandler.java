package backend.academy.linktracker.bot.telegram.command.handlers;

import backend.academy.linktracker.bot.telegram.command.TelegramBotCommand;
import org.springframework.stereotype.Component;

/**
 * Handles `/track` command entry point.
 */
@Component
@TelegramBotCommand(name = "track", description = "начать отслеживание ссылки")
class TrackCommandHandler implements TelegramCommandHandler {

    static final String TRACK_REPLY = "Введите ссылку, которую хотите отслеживать. Для отмены используйте /cancel.";

    /**
     * Returns prompt for starting link tracking dialog.
     *
     * @param messageText raw incoming message text
     * @return dialog prompt
     */
    @Override
    public String handle(String messageText) {
        return TRACK_REPLY;
    }
}
