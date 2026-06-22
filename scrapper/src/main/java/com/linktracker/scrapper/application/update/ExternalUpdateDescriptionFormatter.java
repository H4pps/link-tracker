package com.linktracker.scrapper.application.update;

import com.linktracker.scrapper.application.external.update.ExternalUpdate;

/**
 * Builds notification descriptions from external update metadata.
 */
class ExternalUpdateDescriptionFormatter {

    private static final int PREVIEW_LIMIT = 200;
    private static final String DESCRIPTION_TEMPLATE = "Type: %s%nTitle: %s%nAuthor: %s%nCreated at: %s%nPreview: %s";

    String format(ExternalUpdate update) {
        String type = update.type() == null ? "" : update.type().displayLabel();
        String title = safe(update.title());
        String author = safe(update.author());
        String createdAt = update.createdAt() == null ? "" : update.createdAt().toString();
        String preview = truncate(safe(update.preview()));

        return DESCRIPTION_TEMPLATE.formatted(type, title, author, createdAt, preview);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String truncate(String value) {
        return value.substring(0, Math.min(PREVIEW_LIMIT, value.length()));
    }
}
