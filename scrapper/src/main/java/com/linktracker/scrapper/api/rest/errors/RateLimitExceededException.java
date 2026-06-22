package com.linktracker.scrapper.api.rest.errors;

/**
 * Raised when a REST client exceeds the configured per-IP request limit.
 */
public class RateLimitExceededException extends RuntimeException {

    public RateLimitExceededException(String message) {
        super(message);
    }
}
