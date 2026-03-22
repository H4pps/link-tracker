package backend.academy.linktracker.bot.application.update;

import backend.academy.linktracker.bot.logging.BotLogger;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Temporary iteration-one implementation that logs accepted updates.
 */
@Component
@RequiredArgsConstructor
public class LoggingBotUpdateUseCase implements BotUpdateUseCase {

    private final BotLogger botLogger;

    /**
     * Logs accepted update metadata. Business notification handling is introduced later.
     *
     * @param command validated link update payload
     */
    @Override
    public void processLinkUpdate(LinkUpdateCommand command) {
        botLogger.logApiUpdateAccepted(
                command.id(), command.url(), command.tgChatIds().size());
    }
}
