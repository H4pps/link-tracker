package com.linktracker.bot.telegram.command.handlers;

import com.linktracker.bot.telegram.command.CommandContext;

/**
 * Contract for a single Telegram command handler.
 */
public interface TelegramCommandHandler {

    /**
     * Handles a Telegram command and returns text that should be sent back to the user.
     */
    String handle(CommandContext context);
}
