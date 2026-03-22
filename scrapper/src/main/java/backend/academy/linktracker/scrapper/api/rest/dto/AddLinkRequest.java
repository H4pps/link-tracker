package backend.academy.linktracker.scrapper.api.rest.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.hibernate.validator.constraints.URL;

/**
 * Request payload for add-link endpoint.
 *
 * @param link URL to track
 * @param tags optional tags
 * @param filters optional filters
 */
public record AddLinkRequest(@NotBlank @URL String link, List<@NotBlank String> tags, List<@NotBlank String> filters) {}
