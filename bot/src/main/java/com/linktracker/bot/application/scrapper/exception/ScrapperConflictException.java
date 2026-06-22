package com.linktracker.bot.application.scrapper.exception;

/**
 * Signals conflict errors returned by scrapper.
 */
public class ScrapperConflictException extends ScrapperGatewayException {

    /**
     * Creates exception with message and cause.
     *
     * @param message failure description
     * @param cause root cause
     */
    public ScrapperConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
