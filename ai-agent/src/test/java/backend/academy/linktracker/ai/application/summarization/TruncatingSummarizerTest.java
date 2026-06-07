package backend.academy.linktracker.ai.application.summarization;

import static org.assertj.core.api.Assertions.assertThat;

import backend.academy.linktracker.ai.properties.AiAgentProperties;
import org.junit.jupiter.api.Test;

class TruncatingSummarizerTest {

    private TruncatingSummarizer summarizerWithThreshold(int threshold) {
        AiAgentProperties properties = new AiAgentProperties();
        properties.getSummarization().setThreshold(threshold);
        return new TruncatingSummarizer(properties);
    }

    @Test
    void summarizesLongTextByTruncatingAndDoesNotPassOriginalThrough() {
        TruncatingSummarizer summarizer = summarizerWithThreshold(10);
        String original = "This is a fairly long update description that exceeds the threshold";

        String result = summarizer.summarize(original);

        assertThat(result).isNotEqualTo(original);
        assertThat(result).hasSize(13); // 10 chars + "..."
        assertThat(result).isEqualTo("This is a ...");
        assertThat(result).doesNotContain("threshold");
    }

    @Test
    void leavesShortTextUnchanged() {
        TruncatingSummarizer summarizer = summarizerWithThreshold(500);
        String original = "short update";

        String result = summarizer.summarize(original);

        assertThat(result).isEqualTo(original);
    }
}
