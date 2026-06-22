package backend.academy.linktracker.ai.application.filter;

import static org.assertj.core.api.Assertions.assertThat;

import backend.academy.linktracker.ai.application.LinkUpdate;
import backend.academy.linktracker.ai.properties.AiAgentProperties;
import java.util.List;
import org.junit.jupiter.api.Test;

class UpdateFilterTest {

    private UpdateFilter filterWith(List<String> stopWords, List<String> excludedAuthors, int minLength) {
        AiAgentProperties properties = new AiAgentProperties();
        properties.getFiltering().setStopWords(stopWords);
        properties.getFiltering().setExcludedAuthors(excludedAuthors);
        properties.getFiltering().setMinLength(minLength);
        return new UpdateFilter(properties);
    }

    private LinkUpdate update(String description, String author) {
        return new LinkUpdate(1L, "https://example.com", description, author, List.of(10L));
    }

    @Test
    void rejectsUpdateContainingStopWord() {
        UpdateFilter filter = filterWith(List.of("spam", "ads"), List.of(), 1);

        FilterDecision decision = filter.evaluate(update("Buy cheap ADS now, limited offer!", "alice"));

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reason()).contains("stop-word");
    }

    @Test
    void rejectsUpdateFromExcludedAuthor() {
        UpdateFilter filter = filterWith(List.of(), List.of("bot-user"), 1);

        FilterDecision decision = filter.evaluate(update("A perfectly normal and long enough update", "Bot-User"));

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reason()).contains("excluded-author");
    }

    @Test
    void rejectsUpdateShorterThanMinLength() {
        UpdateFilter filter = filterWith(List.of(), List.of(), 20);

        FilterDecision decision = filter.evaluate(update("too short", "alice"));

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reason()).contains("min-length");
    }

    @Test
    void allowsUpdateThatPassesAllRules() {
        UpdateFilter filter = filterWith(List.of("spam"), List.of("bot-user"), 20);

        FilterDecision decision =
                filter.evaluate(update("A perfectly normal and long enough update about issues", "alice"));

        assertThat(decision.allowed()).isTrue();
    }
}
