package backend.academy.linktracker.bot.application.track.state;

import java.util.List;

/**
 * Mutable chat session snapshot for `/track` dialog.
 *
 * @param state current dialog state
 * @param url URL captured at link step
 * @param tags tags captured at tags step
 */
public record TrackDialogSession(TrackDialogState state, String url, List<String> tags) {

    /**
     * Factory for a new chat session at IDLE state.
     *
     * @return idle session
     */
    public static TrackDialogSession idle() {
        return new TrackDialogSession(TrackDialogState.IDLE, "", List.of());
    }

    /**
     * Canonical constructor normalizing null values.
     *
     * @param state current state
     * @param url URL value
     * @param tags tags list
     */
    public TrackDialogSession {
        state = state == null ? TrackDialogState.IDLE : state;
        url = url == null ? "" : url;
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}
