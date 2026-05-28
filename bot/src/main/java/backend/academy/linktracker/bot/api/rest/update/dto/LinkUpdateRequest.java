package backend.academy.linktracker.bot.api.rest.update.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.hibernate.validator.constraints.URL;

/**
 * Request DTO for scrapper-to-bot notification endpoint.
 *
 * @param id link identifier
 * @param url tracked link URL
 * @param description optional update description
 * @param tgChatIds non-empty list of telegram chat identifiers
 */
public record LinkUpdateRequest(
        @NotNull @Positive Long id,
        @NotNull @URL String url,
        String description,
        @NotEmpty List<@NotNull @Positive Long> tgChatIds) {}
