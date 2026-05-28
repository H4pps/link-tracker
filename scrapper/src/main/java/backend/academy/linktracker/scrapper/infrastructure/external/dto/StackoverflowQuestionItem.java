package backend.academy.linktracker.scrapper.infrastructure.external.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * StackOverflow question item subset.
 *
 * @param lastActivityDate last activity unix timestamp in seconds
 */
public record StackoverflowQuestionItem(
        @JsonProperty("last_activity_date") Long lastActivityDate) {}
