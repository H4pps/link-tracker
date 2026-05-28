package backend.academy.linktracker.bot.telegram.command.handlers;

import backend.academy.linktracker.bot.application.scrapper.ScrapperLinkGateway;
import backend.academy.linktracker.bot.application.scrapper.exception.ScrapperNotFoundException;
import backend.academy.linktracker.bot.application.scrapper.exception.ScrapperUnavailableException;
import backend.academy.linktracker.bot.telegram.command.CommandContext;
import backend.academy.linktracker.bot.telegram.command.TelegramBotCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Handles `/untrack` command.
 */
@Component
@RequiredArgsConstructor
@TelegramBotCommand(name = "untrack", description = "прекратить отслеживание ссылки")
class UntrackCommandHandler implements TelegramCommandHandler {

    static final String USAGE_REPLY = "Использование: /untrack <url>";
    private static final String NOT_FOUND_REPLY = "Ссылка не найдена в отслеживании.";
    private static final String SCRAPPER_UNAVAILABLE_REPLY = "Сервис Scrapper временно недоступен. Попробуйте позже.";

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
            return "Ссылка удалена из отслеживания: " + url;
        } catch (ScrapperNotFoundException exception) {
            return NOT_FOUND_REPLY;
        } catch (ScrapperUnavailableException exception) {
            return SCRAPPER_UNAVAILABLE_REPLY;
        }
    }
}
