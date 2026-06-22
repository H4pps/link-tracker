package com.linktracker.bot.telegram.command.handlers;

import com.linktracker.bot.application.scrapper.ScrapperLinkGateway;
import com.linktracker.bot.application.scrapper.exception.ScrapperNotFoundException;
import com.linktracker.bot.application.scrapper.exception.ScrapperUnavailableException;
import com.linktracker.bot.telegram.command.CommandContext;
import com.linktracker.bot.telegram.command.TelegramBotCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Handles `/untrack` command.
 */
@Component
@RequiredArgsConstructor
@TelegramBotCommand(name = "untrack", description = "stop tracking a link")
class UntrackCommandHandler implements TelegramCommandHandler {

    static final String USAGE_REPLY = "Usage: /untrack <url>";
    private static final String NOT_FOUND_REPLY = "Link is not tracked.";
    private static final String SCRAPPER_UNAVAILABLE_REPLY =
            "Scrapper service is temporarily unavailable. Try again later.";

    private final ScrapperLinkGateway scrapperGateway;

    /**
     * Validates command arguments and returns deterministic feedback.
     *
     * @param context command processing context
     * @return usage hint when URL is missing, otherwise remove-link result
     */
    @Override
    public String handle(CommandContext context) {
        String url = context.parsedCommand().argument();
        if (url.isBlank()) {
            return USAGE_REPLY;
        }

        try {
            scrapperGateway.removeLink(context.chatId(), url);
            return "Link removed from tracking: " + url;
        } catch (ScrapperNotFoundException exception) {
            return NOT_FOUND_REPLY;
        } catch (ScrapperUnavailableException exception) {
            return SCRAPPER_UNAVAILABLE_REPLY;
        }
    }
}
