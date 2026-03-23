package backend.academy.linktracker.bot.infrastructure.scrapper.http.dto;

import java.util.List;

/**
 * Scrapper list-links response payload.
 *
 * @param links tracked links
 * @param size number of links
 */
public record ScrapperListLinksResponse(List<ScrapperLinkResponse> links, int size) {}
