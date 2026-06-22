package com.linktracker.bot.application.track.validation;

/**
 * Validation boundary for URLs accepted by `/track` dialog.
 */
public interface TrackUrlValidator {

    /**
     * Checks whether URL is acceptable for tracking.
     *
     * @param candidate URL candidate
     * @return true when URL is supported
     */
    boolean isValid(String candidate);
}
