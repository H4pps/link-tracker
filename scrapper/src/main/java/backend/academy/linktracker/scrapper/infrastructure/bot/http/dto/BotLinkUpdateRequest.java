package backend.academy.linktracker.scrapper.infrastructure.bot.http.dto;

import java.util.List;

/**
 * Bot `/updates` request payload.
 *
 * @param id representative link identifier
 * @param url tracked URL
 * @param description update description
 * @param tgChatIds subscribed chat identifiers
 */
public record BotLinkUpdateRequest(long id, String url, String description, List<Long> tgChatIds) {}
