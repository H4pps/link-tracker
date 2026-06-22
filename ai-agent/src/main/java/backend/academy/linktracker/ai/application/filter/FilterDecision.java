package backend.academy.linktracker.ai.application.filter;

/**
 * Outcome of evaluating an update against the filtering rules.
 *
 * @param allowed whether the update should proceed to summarization and publishing
 * @param reason human-readable reason, useful for logging when an update is dropped
 */
public record FilterDecision(boolean allowed, String reason) {

    private static final FilterDecision ALLOWED = new FilterDecision(true, "allowed");

    /**
     * @return a decision allowing the update to proceed
     */
    public static FilterDecision allow() {
        return ALLOWED;
    }

    /**
     * @param reason why the update is dropped
     * @return a decision rejecting the update
     */
    public static FilterDecision reject(String reason) {
        return new FilterDecision(false, reason);
    }
}
