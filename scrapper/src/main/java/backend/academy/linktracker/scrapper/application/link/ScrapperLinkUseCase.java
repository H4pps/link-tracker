package backend.academy.linktracker.scrapper.application.link;

import java.util.List;

/**
 * Application boundary for tracked links management.
 */
public interface ScrapperLinkUseCase {

    /**
     * Returns links tracked for a single chat.
     *
     * @param chatId telegram chat identifier
     * @return immutable list of tracked links
     */
    List<LinkView> listLinks(long chatId);

    /**
     * Adds tracked link for a chat.
     *
     * @param chatId telegram chat identifier
     * @param command link data from request body
     * @return created link view
     */
    LinkView addLink(long chatId, AddLinkCommand command);

    /**
     * Removes tracked link for a chat.
     *
     * @param chatId telegram chat identifier
     * @param command remove-link request data
     * @return removed link view
     */
    LinkView removeLink(long chatId, RemoveLinkCommand command);
}
