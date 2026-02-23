package backend.academy.linktracker.bot.telegram;

import com.pengrad.telegrambot.model.BotCommand;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
class TelegramCommandService {

    static final String START_COMMAND = "/start";
    static final String HELP_COMMAND = "/help";

    static final String START_REPLY = "Добро пожаловать! Используйте /help, чтобы посмотреть доступные команды.";
    static final String HELP_REPLY = """
            Доступные команды:
            /start - начало работы
            /help - список доступных команд
            """;
    static final String UNKNOWN_REPLY =
            "Неизвестная команда. Воспользуйтесь /help, чтобы посмотреть список доступных команд.";

    private static final Map<String, String> COMMAND_TO_REPLY =
            Map.of(START_COMMAND, START_REPLY, HELP_COMMAND, HELP_REPLY);

    String replyFor(String messageText) {
        return COMMAND_TO_REPLY.getOrDefault(extractCommand(messageText), UNKNOWN_REPLY);
    }

    String extractCommand(String messageText) {
        if (messageText == null || messageText.isBlank()) {
            return "";
        }

        String firstToken = messageText.strip().split("\\s+", 2)[0];
        int mentionSeparatorIndex = firstToken.indexOf('@');
        if (mentionSeparatorIndex <= 0) {
            return firstToken;
        }

        return firstToken.substring(0, mentionSeparatorIndex);
    }

    BotCommand[] menuCommands() {
        return new BotCommand[] {
            new BotCommand(START_COMMAND, "начало работы"), new BotCommand(HELP_COMMAND, "список команд")
        };
    }
}
