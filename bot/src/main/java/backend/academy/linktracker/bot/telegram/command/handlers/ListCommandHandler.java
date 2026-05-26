package backend.academy.linktracker.bot.telegram.command.handlers;

import backend.academy.linktracker.bot.application.scrapper.ScrapperLinkGateway;
import backend.academy.linktracker.bot.application.scrapper.exception.ScrapperNotFoundException;
import backend.academy.linktracker.bot.application.scrapper.exception.ScrapperUnavailableException;
import backend.academy.linktracker.bot.application.scrapper.view.ScrapperLinkView;
import backend.academy.linktracker.bot.telegram.command.CommandContext;
import backend.academy.linktracker.bot.telegram.command.TelegramBotCommand;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Handles `/list` command.
 */
@Component
@RequiredArgsConstructor
@TelegramBotCommand(name = "list", description = "показать отслеживаемые ссылки")
class ListCommandHandler implements TelegramCommandHandler {

    static final String EMPTY_LIST_REPLY = "Список отслеживаемых ссылок пока пуст.";
    static final String FILTERED_EMPTY_LIST_REPLY_TEMPLATE = "Список отслеживаемых ссылок с тегом \"%s\" пока пуст.";
    private static final String CHAT_NOT_REGISTERED_REPLY = "Чат не зарегистрирован. Используйте /start.";
    private static final String SCRAPPER_UNAVAILABLE_REPLY = "Сервис Scrapper временно недоступен. Попробуйте позже.";

    private final ScrapperLinkGateway scrapperGateway;

    /**
     * Returns deterministic empty-state response for list command.
     *
     * @param context command processing context
     * @return formatted list response with optional tag filtering
     */
    @Override
    public String handle(CommandContext context) {
        String tag = context.parsedCommand().argument().strip();
        try {
            List<ScrapperLinkView> links = scrapperGateway.listLinks(context.chatId());
            if (!tag.isBlank()) {
                String normalizedTag = tag.toLowerCase(Locale.ROOT);
                links = links.stream()
                        .filter(link -> link.tags().stream()
                                .map(value -> value.toLowerCase(Locale.ROOT))
                                .anyMatch(normalizedTag::equals))
                        .toList();
            }
            if (links.isEmpty()) {
                return tag.isBlank() ? EMPTY_LIST_REPLY : FILTERED_EMPTY_LIST_REPLY_TEMPLATE.formatted(tag);
            }
            return formatLinks(links);
        } catch (ScrapperNotFoundException exception) {
            return CHAT_NOT_REGISTERED_REPLY;
        } catch (ScrapperUnavailableException exception) {
            return SCRAPPER_UNAVAILABLE_REPLY;
        }
    }

    private String formatLinks(List<ScrapperLinkView> links) {
        StringBuilder builder = new StringBuilder("Отслеживаемые ссылки:");
        for (int index = 0; index < links.size(); index++) {
            ScrapperLinkView link = links.get(index);
            builder.append(System.lineSeparator())
                    .append(index + 1)
                    .append(". ")
                    .append(link.url());
        }
        return builder.toString();
    }
}
