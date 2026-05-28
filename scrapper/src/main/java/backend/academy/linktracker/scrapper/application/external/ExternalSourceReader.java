package backend.academy.linktracker.scrapper.application.external;

import java.time.Instant;

/**
 * Reads last-update timestamp from external source.
 */
public interface ExternalSourceReader {

    /**
     * Checks whether reader can process provided source descriptor.
     *
     * @param source parsed source descriptor
     * @return true when source type is supported by this reader
     */
    boolean supports(LinkSource source);

    /**
     * Fetches latest update timestamp for source item.
     *
     * @param source parsed source descriptor
     * @return external update timestamp
     */
    Instant fetchLastUpdated(LinkSource source);
}
