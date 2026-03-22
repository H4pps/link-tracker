package backend.academy.linktracker.bot.application.update;

/**
 * Application boundary for processing incoming link updates from scrapper service.
 */
public interface BotUpdateUseCase {

    /**
     * Accepts a validated link update command and performs processing.
     *
     * @param command validated link update payload
     */
    void processLinkUpdate(LinkUpdateCommand command);
}
