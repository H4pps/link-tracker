package backend.academy.linktracker.bot.telegram.command.handlers;

import backend.academy.linktracker.bot.application.scrapper.ScrapperChatGateway;
import backend.academy.linktracker.bot.application.scrapper.exception.ScrapperConflictException;
import backend.academy.linktracker.bot.application.scrapper.exception.ScrapperUnavailableException;
import backend.academy.linktracker.bot.telegram.command.CommandContext;
import backend.academy.linktracker.bot.telegram.command.TelegramBotCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Handles `/start` command.
 */
@Component
@RequiredArgsConstructor
@TelegramBotCommand(name = "start", description = "начало работы")
class StartCommandHandler implements TelegramCommandHandler {

    static final String START_REPLY = "Добро пожаловать! Используйте /help, чтобы посмотреть доступные команды.";
    private static final String SCRAPPER_UNAVAILABLE_REPLY = "Сервис Scrapper временно недоступен. Попробуйте позже.";

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
