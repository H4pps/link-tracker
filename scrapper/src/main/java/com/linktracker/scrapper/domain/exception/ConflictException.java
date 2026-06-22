package com.linktracker.scrapper.domain.exception;

/**
 * Signals conflict state where operation cannot be completed in current resource state.
 */
public class ConflictException extends RuntimeException {

    /**
     * Creates an exception with message.
     *
     * @param message failure description
     */
    public ConflictException(String message) {
        super(message);
    }
}
