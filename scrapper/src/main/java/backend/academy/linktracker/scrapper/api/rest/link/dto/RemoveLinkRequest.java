package backend.academy.linktracker.scrapper.api.rest.link.dto;

import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.URL;

/**
 * Request payload for remove-link endpoint.
 *
 * @param link URL to stop tracking
 */
public record RemoveLinkRequest(@NotBlank @URL String link) {}
