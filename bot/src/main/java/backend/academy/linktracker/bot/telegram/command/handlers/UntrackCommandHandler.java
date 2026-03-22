package backend.academy.linktracker.bot.telegram.command.handlers;

import backend.academy.linktracker.bot.telegram.command.TelegramBotCommand;
import org.springframework.stereotype.Component;

/**
 * Handles `/untrack` command.
 */
@Component
@TelegramBotCommand(name = "untrack", description = "прекратить отслеживание ссылки")
class UntrackCommandHandler implements TelegramCommandHandler {

    static final String USAGE_REPLY = "Использование: /untrack <url>";
    static final String ACCEPTED_REPLY_TEMPLATE =
            "Удаление ссылки зарегистрировано: %s. Полная синхронизация со Scrapper будет добавлена в следующей итерации.";

    private static final String WHITESPACE_REGEX = "\\s+";
    private static final int PARTS_LIMIT = 2;

    /**
     * Validates command arguments and returns deterministic feedback.
     *
     * @param messageText raw incoming message text
     * @return usage hint when URL is missing, otherwise acceptance stub message
     */
    @Override
    public String handle(String messageText) {
        String url = extractArgument(messageText);
        if (url.isBlank()) {
            return USAGE_REPLY;
        }
        return ACCEPTED_REPLY_TEMPLATE.formatted(url);
    }

    /**
     * Extracts command argument after the first whitespace.
     *
     * @param messageText raw incoming message text
     * @return argument text or empty string
     */
    private String extractArgument(String messageText) {
        if (messageText == null || messageText.isBlank()) {
            return "";
        }

        String[] parts = messageText.strip().split(WHITESPACE_REGEX, PARTS_LIMIT);
        if (parts.length < PARTS_LIMIT) {
            return "";
        }
        return parts[1].strip();
    }
}
