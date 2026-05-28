package backend.academy.linktracker.bot.telegram.command.handlers;

import backend.academy.linktracker.bot.telegram.command.CommandContext;
import backend.academy.linktracker.bot.telegram.command.TelegramBotCommand;
import backend.academy.linktracker.bot.telegram.command.TelegramCommandRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Handles `/help` command by returning auto-generated command list.
 */
@Component
@RequiredArgsConstructor
@TelegramBotCommand(name = "help", description = "список команд")
class HelpCommandHandler implements TelegramCommandHandler {

    private final ObjectProvider<TelegramCommandRegistry> commandRegistryProvider;

    /**
     * Returns help text generated from registered commands.
     *
     * @param context command processing context
     * @return auto-generated help message
     */
    @Override
    public String handle(CommandContext context) {
        return commandRegistryProvider.getObject().helpMessage();
    }
}
