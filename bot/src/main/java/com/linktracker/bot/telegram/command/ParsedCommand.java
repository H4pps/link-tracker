package com.linktracker.bot.telegram.command;

/**
 * Parsed representation of a command token extracted from a Telegram message.
 *
 * @param inputCommand raw first token from the message text
 * @param normalizedCommand normalized command name used for routing
 * @param argument optional argument payload after the first token
 */
public record ParsedCommand(String inputCommand, String normalizedCommand, String argument) {}
