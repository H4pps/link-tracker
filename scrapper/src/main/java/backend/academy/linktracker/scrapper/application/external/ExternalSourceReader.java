package backend.academy.linktracker.scrapper.application.external;

import backend.academy.linktracker.scrapper.application.external.link.LinkSource;
import backend.academy.linktracker.scrapper.application.external.update.ExternalUpdate;

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
     * @return external update metadata
     */
    ExternalUpdate fetchLatestUpdate(LinkSource source);
}
