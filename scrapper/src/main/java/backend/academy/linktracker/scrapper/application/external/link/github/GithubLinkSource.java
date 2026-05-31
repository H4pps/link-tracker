package backend.academy.linktracker.scrapper.application.external.link.github;

import backend.academy.linktracker.scrapper.application.external.link.LinkSource;

/**
 * Parsed GitHub repository source.
 *
 * @param owner repository owner
 * @param repository repository name
 */
public record GithubLinkSource(String owner, String repository) implements LinkSource {}
