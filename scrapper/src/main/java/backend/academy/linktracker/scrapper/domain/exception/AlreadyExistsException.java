package backend.academy.linktracker.scrapper.domain.exception;

/**
 * Signals conflict state where requested resource already exists.
 */
public class AlreadyExistsException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates an exception with message.
     *
     * @param message failure description
     */
    public AlreadyExistsException(String message) {
        super(message);
    }
}
