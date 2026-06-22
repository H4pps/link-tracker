package backend.academy.linktracker.ai.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Typed properties for AI Agent filtering and summarization behavior.
 */
@ConfigurationProperties(prefix = "ai-agent")
@Validated
@Getter
@Setter
@NoArgsConstructor
public class AiAgentProperties {

    @Valid
    @NotNull
    private Filtering filtering = new Filtering();

    @Valid
    @NotNull
    private Summarization summarization = new Summarization();

    /**
     * Filtering rules applied to incoming updates.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class Filtering {

        /**
         * Updates whose text contains any of these words (case-insensitive) are dropped.
         */
        @NotNull
        private List<String> stopWords = List.of();

        /**
         * Updates authored by any of these authors are dropped.
         */
        @NotNull
        private List<String> excludedAuthors = List.of();

        /**
         * Updates shorter than this length are dropped.
         */
        @Min(0)
        private int minLength = 20;
    }

    /**
     * Summarization configuration.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class Summarization {

        /**
         * Summarizer implementation to use: truncating stub or AI API.
         */
        @NotNull
        private SummarizationMode mode = SummarizationMode.STUB;

        /**
         * Texts longer than this threshold are summarized.
         */
        @Min(1)
        private int threshold = 500;
    }

    /**
     * Supported summarization strategies.
     */
    public enum SummarizationMode {
        STUB,
        AI
    }
}
