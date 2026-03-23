package backend.academy.linktracker.scrapper.application.external;

/**
 * Runtime exception for external source communication/parsing failures.
 */
public class ExternalSourceException extends RuntimeException {

    /**
     * Creates exception with message and cause.
     *
     * @param message failure description
     * @param cause root cause
     */
    public ExternalSourceException(String message, Throwable cause) {
        super(message, cause);
    }
}
