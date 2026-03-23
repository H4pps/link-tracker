package backend.academy.linktracker.bot.infrastructure.scrapper.http.dto;

import java.util.List;

/**
 * Scrapper tracked-link response payload.
 *
 * @param id link identifier
 * @param url tracked URL
 * @param tags tags
 * @param filters filters
 */
public record ScrapperLinkResponse(long id, String url, List<String> tags, List<String> filters) {}
