package com.linktracker.scrapper.infrastructure.external.dto.github;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Subset of GitHub issues API response item used by scheduler.
 *
 * @param title issue or pull request title
 * @param createdAt issue or pull request creation timestamp in ISO-8601 format
 * @param body issue or pull request body text
 * @param user issue or pull request author
 * @param pullRequest non-null when item is a pull request
 */
public record GithubIssueItemResponse(
        String title,
        @JsonProperty("created_at") String createdAt,
        String body,
        GithubIssueUserResponse user,
        @JsonProperty("pull_request") GithubPullRequestResponse pullRequest) {}
