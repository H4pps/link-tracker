package backend.academy.linktracker.scrapper.application.external.update;

/**
 * Supported external update kinds tracked by scrapper.
 */
public enum ExternalUpdateType {
    GITHUB_ISSUE,
    GITHUB_PULL_REQUEST,
    STACKOVERFLOW_ANSWER,
    STACKOVERFLOW_COMMENT
}
