package backend.academy.linktracker.bot.telegram.command;

import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Parses raw incoming Telegram message text into normalized command metadata.
 */
@Component
class CommandTextParser {

    static final String COMMAND_SPLIT_REGEX = "\\s+";
    static final int COMMAND_SPLIT_LIMIT = 2;
    private static final char COMMAND_PREFIX = '/';
    private static final char MENTION_SEPARATOR = '@';

    /**
     * Extracts the first token and normalizes it into command form.
     *
     * @param messageText raw message text from Telegram
     * @return parsed command details used for routing and logging
     */
    ParsedCommand parse(String messageText) {
        if (messageText == null || messageText.isBlank()) {
            return new ParsedCommand("", "");
        }

        String firstToken = messageText.strip().split(COMMAND_SPLIT_REGEX, COMMAND_SPLIT_LIMIT)[0];
        String commandToken = removeMention(firstToken);
        String normalizedCommand = normalizeCommand(commandToken);

        return new ParsedCommand(firstToken, normalizedCommand);
    }

    /**
     * Removes optional bot mention suffix from command token.
     *
     * @param commandToken first token from a message
     * @return token without `@botname` suffix
     */
    private String removeMention(String commandToken) {
        int mentionSeparatorIndex = commandToken.indexOf(MENTION_SEPARATOR);
        if (mentionSeparatorIndex <= 0) {
            return commandToken;
        }

        return commandToken.substring(0, mentionSeparatorIndex);
    }

    /**
     * Converts command token to lowercase and strips leading slash when present.
     *
     * @param commandToken first token from a message
     * @return normalized command name
     */
    private String normalizeCommand(String commandToken) {
        String commandWithoutPrefix = commandToken;
        if (!commandWithoutPrefix.isEmpty() && commandWithoutPrefix.charAt(0) == COMMAND_PREFIX) {
            commandWithoutPrefix = commandWithoutPrefix.substring(1);
        }

        return commandWithoutPrefix.toLowerCase(Locale.ROOT);
    }
}
