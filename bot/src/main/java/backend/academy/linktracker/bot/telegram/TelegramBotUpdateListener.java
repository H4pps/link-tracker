package backend.academy.linktracker.bot.telegram;

import backend.academy.linktracker.bot.properties.TelegramProperties;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.request.SetMyCommands;
import jakarta.annotation.PreDestroy;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class TelegramBotUpdateListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(TelegramBotUpdateListener.class);

    private final TelegramBot telegramBot;
    private final TelegramProperties telegramProperties;
    private final TelegramCommandService commandService;

    @EventListener(ApplicationReadyEvent.class)
    void start() {
        registerCommandsMenu();
        if (!telegramProperties.isPollingEnabled()) {
            LOGGER.atInfo().addKeyValue("pollingEnabled", false).log("Telegram updates polling is disabled");
            return;
        }

        telegramBot.setUpdatesListener(this::processUpdates);
        LOGGER.atInfo()
                .addKeyValue("pollingEnabled", true)
                .addKeyValue(
                        "listenerSleepMillis",
                        telegramProperties.getUpdateListenerSleep().toMillis())
                .log("Telegram updates polling started");
    }

    @PreDestroy
    void shutdown() {
        telegramBot.removeGetUpdatesListener();
    }

    private int processUpdates(List<Update> updates) {
        updates.forEach(this::handleUpdate);
        return UpdatesListener.CONFIRMED_UPDATES_ALL;
    }

    private void handleUpdate(Update update) {
        var message = update.message();
        if (message == null || message.chat() == null || message.text() == null) {
            return;
        }

        String command = commandService.extractCommand(message.text());
        String reply = commandService.replyFor(message.text());
        var response = telegramBot.execute(new SendMessage(message.chat().id().longValue(), reply));

        LOGGER.atInfo()
                .addKeyValue("chatId", message.chat().id())
                .addKeyValue("command", command)
                .addKeyValue("responseOk", response.isOk())
                .log("Telegram command processed");
    }

    private void registerCommandsMenu() {
        try {
            var response = telegramBot.execute(new SetMyCommands(commandService.menuCommands()));
            LOGGER.atInfo()
                    .addKeyValue("responseOk", response.isOk())
                    .addKeyValue("errorCode", response.errorCode())
                    .log("Telegram commands menu registered");
        } catch (RuntimeException exception) {
            LOGGER.atWarn()
                    .setCause(exception)
                    .addKeyValue("operation", "setMyCommands")
                    .log("Telegram commands menu registration failed");
        }
    }
}
