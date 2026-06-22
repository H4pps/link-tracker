package backend.academy.linktracker.scrapper.infrastructure.bot.kafka.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import backend.academy.linktracker.messaging.RawLinkUpdateEvent;
import backend.academy.linktracker.scrapper.application.update.LinkUpdateOutboxEvent;
import java.util.List;
import org.junit.jupiter.api.Test;

class LinkUpdateOutboxEventMapperTest {

    private final LinkUpdateOutboxEventMapper mapper = new LinkUpdateOutboxEventMapper();

    @Test
    void mapsOutboxEventToAvroRawLinkUpdateEvent() {
        LinkUpdateOutboxEvent outboxEvent =
                LinkUpdateOutboxEvent.pending(11L, "https://example.com", "changed", "octocat", List.of(100L, 200L));

        RawLinkUpdateEvent event = mapper.toEvent(outboxEvent);

        assertThat(event.getId()).isEqualTo(11L);
        assertThat(String.valueOf(event.getUrl())).isEqualTo("https://example.com");
        assertThat(String.valueOf(event.getDescription())).isEqualTo("changed");
        assertThat(String.valueOf(event.getAuthor())).isEqualTo("octocat");
        assertThat(event.getTgChatIds()).containsExactly(100L, 200L);
    }
}
