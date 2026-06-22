package com.linktracker.bot.application.update;

import java.util.List;

/**
 * Immutable command containing data about a link update sent by scrapper.
 *
 * @param id link identifier
 * @param url tracked link URL
 * @param description update description
 * @param tgChatIds telegram chat identifiers to notify
 */
public record LinkUpdateCommand(Long id, String url, String description, List<Long> tgChatIds) {}
