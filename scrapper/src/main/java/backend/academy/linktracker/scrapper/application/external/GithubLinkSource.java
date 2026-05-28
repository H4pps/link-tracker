package backend.academy.linktracker.scrapper.application.external;

/**
 * Parsed GitHub repository source.
 *
 * @param owner repository owner
 * @param repository repository name
 */
public record GithubLinkSource(String owner, String repository) implements LinkSource {}
