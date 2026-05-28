package backend.academy.linktracker.scrapper.infrastructure.external.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Subset of GitHub repository API response used by scheduler.
 *
 * @param updatedAt repository update timestamp in ISO-8601 format
 */
public record GithubRepositoryResponse(
        @JsonProperty("updated_at") String updatedAt) {}
