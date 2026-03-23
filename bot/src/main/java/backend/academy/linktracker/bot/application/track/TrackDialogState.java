package backend.academy.linktracker.bot.application.track;

/**
 * Chat-scoped state of `/track` dialog.
 */
public enum TrackDialogState {
    IDLE,
    AWAITING_LINK,
    AWAITING_TAGS,
    AWAITING_FILTERS
}
