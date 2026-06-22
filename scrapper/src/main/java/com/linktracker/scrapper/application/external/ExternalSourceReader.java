package com.linktracker.scrapper.application.external;

import com.linktracker.scrapper.application.external.link.LinkSource;
import com.linktracker.scrapper.application.external.update.ExternalUpdate;
import java.util.Optional;

/**
 * Reads latest update metadata from external source.
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
     * Fetches latest update metadata for source item.
     *
     * @param source parsed source descriptor
     * @return latest update metadata, or empty when the source has nothing to report yet
     * @throws ExternalSourceException when the external source cannot be queried
     */
    Optional<ExternalUpdate> fetchLatestUpdate(LinkSource source);
}
