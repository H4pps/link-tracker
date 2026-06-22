package com.linktracker.bot.telegram.command.handlers;

import com.linktracker.bot.application.scrapper.ScrapperChatGateway;
import com.linktracker.bot.application.scrapper.exception.ScrapperConflictException;
import com.linktracker.bot.application.scrapper.exception.ScrapperUnavailableException;
import com.linktracker.bot.telegram.command.CommandContext;
import com.linktracker.bot.telegram.command.TelegramBotCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Handles `/start` command.
 */
@Component
@RequiredArgsConstructor
@TelegramBotCommand(name = "start", description = "start using the bot")
class StartCommandHandler implements TelegramCommandHandler {

    static final String START_REPLY = "Welcome! Use /help to see available commands.";
    private static final String SCRAPPER_UNAVAILABLE_REPLY =
            "Scrapper service is temporarily unavailable. Try again later.";

    private final ScrapperChatGateway scrapperGateway;

    /**
     * Returns welcome text for `/start`.
     *
     * @param context command processing context
     * @return welcome message
     */
    @Override
    public String handle(CommandContext context) {
        try {
            scrapperGateway.registerChat(context.chatId());
            return START_REPLY;
        } catch (ScrapperConflictException ignored) {
            return START_REPLY;
        } catch (ScrapperUnavailableException exception) {
            return SCRAPPER_UNAVAILABLE_REPLY;
        }
    }
}
