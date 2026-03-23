package backend.academy.linktracker.scrapper.domain.exception;

/**
 * Signals that requested resource was not found.
 */
public class NotFoundException extends RuntimeException {

    /**
     * Creates an exception with message.
     *
     * @param message failure description
     */
    public NotFoundException(String message) {
        super(message);
    }
}
