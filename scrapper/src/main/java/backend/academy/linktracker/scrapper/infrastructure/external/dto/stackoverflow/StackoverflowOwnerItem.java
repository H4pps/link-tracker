package backend.academy.linktracker.scrapper.infrastructure.external.dto.stackoverflow;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * StackOverflow owner subset for updates.
 *
 * @param displayName owner display name
 */
public record StackoverflowOwnerItem(
        @JsonProperty("display_name") String displayName) {}
