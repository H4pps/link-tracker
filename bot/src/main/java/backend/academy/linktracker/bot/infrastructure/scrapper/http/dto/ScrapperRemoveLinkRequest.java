package backend.academy.linktracker.bot.infrastructure.scrapper.http.dto;

/**
 * Scrapper remove-link request payload.
 *
 * @param link tracked URL
 */
public record ScrapperRemoveLinkRequest(String link) {}
