package backend.academy.linktracker.bot.telegram.lifecycle;

import backend.academy.linktracker.bot.logging.BotLogger;
import backend.academy.linktracker.bot.telegram.command.TelegramCommandRegistry;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.request.SetMyCommands;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Registers Telegram command menu during application startup.
 */
@Component
@RequiredArgsConstructor
class BotCommandRegistrar {

    private final TelegramBot telegramBot;
    private final TelegramCommandRegistry commandRegistry;
    private final BotLogger botLogger;

    /**
     * Registers command menu in Telegram on application startup.
     */
    @EventListener(ApplicationReadyEvent.class)
    void registerCommandsMenu() {
        try {
            var response = telegramBot.execute(new SetMyCommands(commandRegistry.menuCommands()));
            botLogger.logCommandsMenuRegistered(response.isOk(), response.errorCode());
        } catch (RuntimeException exception) {
            botLogger.logCommandsMenuRegistrationFailed(exception);
        }
    }
}
