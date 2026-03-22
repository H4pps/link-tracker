package backend.academy.linktracker.bot.telegram.command.handlers;

import backend.academy.linktracker.bot.telegram.command.TelegramBotCommand;
import org.springframework.stereotype.Component;

/**
 * Handles `/list` command.
 */
@Component
@TelegramBotCommand(name = "list", description = "показать отслеживаемые ссылки")
class ListCommandHandler implements TelegramCommandHandler {

    static final String EMPTY_LIST_REPLY = "Список отслеживаемых ссылок пока пуст.";
    static final String FILTERED_EMPTY_LIST_REPLY_TEMPLATE = "Список отслеживаемых ссылок с тегом \"%s\" пока пуст.";

    private static final String WHITESPACE_REGEX = "\\s+";
    private static final int PARTS_LIMIT = 2;

    /**
     * Returns deterministic empty-state response for list command.
     *
     * @param messageText raw incoming message text
     * @return empty list response with optional tag echo
     */
    @Override
    public String handle(String messageText) {
        String tag = extractArgument(messageText);
        if (tag.isBlank()) {
            return EMPTY_LIST_REPLY;
        }
        return FILTERED_EMPTY_LIST_REPLY_TEMPLATE.formatted(tag);
    }

    /**
     * Extracts optional tag argument from command message.
     *
     * @param messageText raw incoming message text
     * @return tag value or empty string
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
