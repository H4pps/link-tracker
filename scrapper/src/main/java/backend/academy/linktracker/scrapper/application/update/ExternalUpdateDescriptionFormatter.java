package backend.academy.linktracker.scrapper.application.update;

import backend.academy.linktracker.scrapper.application.external.update.ExternalUpdate;
import backend.academy.linktracker.scrapper.application.external.update.ExternalUpdateType;

/**
 * Builds notification descriptions from external update metadata.
 */
class ExternalUpdateDescriptionFormatter {

    private static final int PREVIEW_LIMIT = 200;
    private static final String DESCRIPTION_TEMPLATE = "Type: %s%nTitle: %s%nAuthor: %s%nCreated at: %s%nPreview: %s";

    String format(ExternalUpdate update) {
        String type = displayType(update.type());
        String title = safe(update.title());
        String author = safe(update.author());
        String createdAt = update.createdAt() == null ? "" : update.createdAt().toString();
        String preview = truncate(safe(update.preview()));

        return DESCRIPTION_TEMPLATE.formatted(type, title, author, createdAt, preview);
    }

    private String displayType(ExternalUpdateType type) {
        if (type == null) {
            return "";
        }
        return switch (type) {
            case GITHUB_ISSUE -> "GitHub Issue";
            case GITHUB_PULL_REQUEST -> "GitHub Pull Request";
            case STACKOVERFLOW_ANSWER -> "StackOverflow Answer";
            case STACKOVERFLOW_COMMENT -> "StackOverflow Comment";
        };
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String truncate(String value) {
        return value.substring(0, Math.min(PREVIEW_LIMIT, value.length()));
    }
}
