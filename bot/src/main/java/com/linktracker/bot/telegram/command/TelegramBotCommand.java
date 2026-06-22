package com.linktracker.bot.telegram.command;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares Telegram command metadata for a command handler implementation.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface TelegramBotCommand {

    /**
     * Returns the command name without the leading slash.
     */
    String name();

    /**
     * Returns a human-readable command description for Telegram menu/help.
     */
    String description();
}
