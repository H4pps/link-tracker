package backend.academy.linktracker.bot.application.scrapper.exception;

/**
 * Signals transport-level or unknown gateway failures when calling scrapper.
 */
public class ScrapperUnavailableException extends ScrapperGatewayException {

    /**
     * Creates exception with message and cause.
     *
     * @param message failure description
     * @param cause root cause
     */
    public ScrapperUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
