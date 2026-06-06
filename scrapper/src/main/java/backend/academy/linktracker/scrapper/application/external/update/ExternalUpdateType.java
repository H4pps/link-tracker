package backend.academy.linktracker.scrapper.application.external.update;

/**
 * Supported external update kinds tracked by scrapper.
 */
public enum ExternalUpdateType {
    GITHUB_ISSUE("GitHub Issue"),
    GITHUB_PULL_REQUEST("GitHub Pull Request"),
    STACKOVERFLOW_ANSWER("StackOverflow Answer"),
    STACKOVERFLOW_COMMENT("StackOverflow Comment");

    private final String displayLabel;

    ExternalUpdateType(String displayLabel) {
        this.displayLabel = displayLabel;
    }

    public String displayLabel() {
        return displayLabel;
    }
}
