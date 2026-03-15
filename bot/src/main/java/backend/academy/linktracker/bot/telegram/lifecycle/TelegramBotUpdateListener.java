package backend.academy.linktracker.bot.telegram.lifecycle;

import backend.academy.linktracker.bot.properties.TelegramProperties;
import backend.academy.linktracker.bot.telegram.command.TelegramCommandProcessor;
import backend.academy.linktracker.bot.telegram.logging.BotLogger;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.SendMessage;
import jakarta.annotation.PreDestroy;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Starts and handles Telegram long-polling updates lifecycle.
 */
@Component
@RequiredArgsConstructor
class TelegramBotUpdateListener {

    private final TelegramBot telegramBot;
    private final TelegramProperties telegramProperties;
    private final TelegramCommandProcessor commandProcessor;
    private final BotLogger botLogger;

    /**
     * Starts Telegram updates polling when application is ready.
     */
    @EventListener(ApplicationReadyEvent.class)
    void start() {
        if (!telegramProperties.isPollingEnabled()) {
            botLogger.logPollingDisabled();
            return;
        }

        telegramBot.setUpdatesListener(this::processUpdates);
        botLogger.logPollingStarted(telegramProperties.getUpdateListenerSleep().toMillis());
    }

    /**
     * Stops updates listener during application shutdown.
     */
    @PreDestroy
    void shutdown() {
        telegramBot.removeGetUpdatesListener();
    }

    /**
     * Processes a batch of updates received from Telegram API.
     *
     * @param updates updates returned by Telegram
     * @return acknowledgement constant for processed updates
     */
    private int processUpdates(List<Update> updates) {
        updates.forEach(this::handleUpdate);
        return UpdatesListener.CONFIRMED_UPDATES_ALL;
    }

    /**
     * Handles a single update by routing command and sending reply.
     *
     * @param update incoming Telegram update
     */
    private void handleUpdate(Update update) {
        var message = update.message();
        if (message == null || message.chat() == null || message.text() == null) {
            return;
        }

        TelegramCommandProcessor.CommandProcessingResult processingResult = commandProcessor.process(message.text());

        long chatId = message.chat().id();
        var response = telegramBot.execute(new SendMessage(chatId, processingResult.reply()));
        botLogger.logCommandProcessed(
                chatId, processingResult.commandForLog(), processingResult.inputCommand(), response.isOk());
    }
}
