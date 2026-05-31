package backend.academy.linktracker.scrapper.infrastructure.external.dto.github;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * GitHub user subset with login.
 *
 * @param login GitHub user login
 */
public record GithubIssueUserResponse(@JsonProperty("login") String login) {}
