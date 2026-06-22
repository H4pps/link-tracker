package com.linktracker.bot.telegram.command.handlers;

import com.linktracker.bot.application.scrapper.ScrapperLinkGateway;
import com.linktracker.bot.application.scrapper.exception.ScrapperNotFoundException;
import com.linktracker.bot.application.scrapper.exception.ScrapperUnavailableException;
import com.linktracker.bot.application.scrapper.view.ScrapperLinkView;
import com.linktracker.bot.telegram.command.CommandContext;
import com.linktracker.bot.telegram.command.TelegramBotCommand;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Handles `/list` command.
 */
@Component
@RequiredArgsConstructor
@TelegramBotCommand(name = "list", description = "show tracked links")
class ListCommandHandler implements TelegramCommandHandler {

    static final String EMPTY_LIST_REPLY = "No tracked links yet.";
    static final String FILTERED_EMPTY_LIST_REPLY_TEMPLATE = "No tracked links with tag \"%s\" yet.";
    private static final String CHAT_NOT_REGISTERED_REPLY = "Chat is not registered. Use /start.";
    private static final String SCRAPPER_UNAVAILABLE_REPLY =
            "Scrapper service is temporarily unavailable. Try again later.";

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
        StringBuilder builder = new StringBuilder("Tracked links:");
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
