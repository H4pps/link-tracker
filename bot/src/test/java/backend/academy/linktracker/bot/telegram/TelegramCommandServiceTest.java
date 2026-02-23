package backend.academy.linktracker.bot.telegram;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TelegramCommandServiceTest {

    private final TelegramCommandService commandService = new TelegramCommandService();

    @Test
    void resolvesStartCommand() {
        assertThat(commandService.replyFor("/start")).isEqualTo(TelegramCommandService.START_REPLY);
    }

    @Test
    void resolvesHelpCommand() {
        assertThat(commandService.replyFor("/help")).isEqualTo(TelegramCommandService.HELP_REPLY);
    }

    @Test
    void resolvesUnknownCommand() {
        assertThat(commandService.replyFor("/unknown")).isEqualTo(TelegramCommandService.UNKNOWN_REPLY);
    }

    @Test
    void resolvesCommandWithMentionAndArguments() {
        assertThat(commandService.replyFor("/help@linktrackerbot extra")).isEqualTo(TelegramCommandService.HELP_REPLY);
    }
}
