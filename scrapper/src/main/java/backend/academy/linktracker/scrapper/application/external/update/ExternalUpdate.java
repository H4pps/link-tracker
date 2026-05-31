package backend.academy.linktracker.scrapper.application.external.update;

import java.time.Instant;

/**
 * External source update metadata used by scheduler.
 *
 * @param type update type
 * @param createdAt external update creation timestamp
 * @param title update title or question topic
 * @param author update author display name
 * @param preview update text preview
 */
public record ExternalUpdate(ExternalUpdateType type, Instant createdAt, String title, String author, String preview) {}
