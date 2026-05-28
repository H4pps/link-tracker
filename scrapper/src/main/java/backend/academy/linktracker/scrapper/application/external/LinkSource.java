package backend.academy.linktracker.scrapper.application.external;

/**
 * Resolved external source descriptor for tracked URL.
 */
public sealed interface LinkSource permits GithubLinkSource, StackoverflowQuestionLinkSource {}
