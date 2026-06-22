package com.linktracker.bot.telegram.command.handlers;

import com.linktracker.bot.application.track.service.TrackDialogService;
import com.linktracker.bot.telegram.command.CommandContext;
import com.linktracker.bot.telegram.command.TelegramBotCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Handles `/track` command entry point.
 */
@Component
@RequiredArgsConstructor
@TelegramBotCommand(name = "track", description = "start tracking a link")
class TrackCommandHandler implements TelegramCommandHandler {

    private final TrackDialogService trackDialogService;

    /**
     * Returns prompt for starting link tracking dialog.
     *
     * @param context command processing context
     * @return dialog prompt
     */
    @Override
    public String handle(CommandContext context) {
        return trackDialogService.start(context.chatId());
    }
}
