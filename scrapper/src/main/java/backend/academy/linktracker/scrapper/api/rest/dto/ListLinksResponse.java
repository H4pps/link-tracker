package backend.academy.linktracker.scrapper.api.rest.dto;

import java.util.List;

/**
 * API response payload for list-links endpoint.
 *
 * @param links tracked links for chat
 * @param size tracked link count
 */
public record ListLinksResponse(List<LinkResponse> links, int size) {}
