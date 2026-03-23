package backend.academy.linktracker.bot.application.scrapper.exception;

/**
 * Base runtime exception thrown by scrapper gateway adapter.
 */
public class ScrapperGatewayException extends RuntimeException {

    /**
     * Creates exception with message and cause.
     *
     * @param message failure description
     * @param cause root cause
     */
    public ScrapperGatewayException(String message, Throwable cause) {
        super(message, cause);
    }
}
