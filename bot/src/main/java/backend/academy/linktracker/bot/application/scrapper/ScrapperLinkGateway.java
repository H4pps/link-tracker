package backend.academy.linktracker.bot.application.scrapper;

import backend.academy.linktracker.bot.application.scrapper.command.AddScrapperLinkCommand;
import backend.academy.linktracker.bot.application.scrapper.view.ScrapperLinkView;
import java.util.List;

/**
 * Bot-side application port for scrapper link operations.
 */
public interface ScrapperLinkGateway {

    /**
     * Lists links tracked for chat.
     *
     * @param chatId telegram chat identifier
     * @return tracked links list
     */
    List<ScrapperLinkView> listLinks(long chatId);

    /**
     * Adds tracked link in scrapper for chat.
     *
     * @param chatId telegram chat identifier
     * @param command add-link payload
     * @return created link projection
     */
    ScrapperLinkView addLink(long chatId, AddScrapperLinkCommand command);

    /**
     * Removes tracked link from scrapper for chat.
     *
     * @param chatId telegram chat identifier
     * @param url tracked URL
     * @return removed link projection
     */
    ScrapperLinkView removeLink(long chatId, String url);
}
