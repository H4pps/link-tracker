package backend.academy.linktracker.bot.application.scrapper.exception;

/**
 * Signals not-found errors returned by scrapper.
 */
public class ScrapperNotFoundException extends ScrapperGatewayException {

    /**
     * Creates exception with message and cause.
     *
     * @param message failure description
     * @param cause root cause
     */
    public ScrapperNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
