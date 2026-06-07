package backend.academy.linktracker.ai.application.summarization;

import backend.academy.linktracker.ai.properties.AiAgentProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Stub summarizer: truncates text to the threshold and appends an ellipsis. Active by default.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ai-agent.summarization", name = "mode", havingValue = "stub", matchIfMissing = true)
public class TruncatingSummarizer implements Summarizer {

    private static final String ELLIPSIS = "...";

    private final AiAgentProperties properties;

    @Override
    public String summarize(String text) {
        if (text == null) {
            return null;
        }
        int threshold = properties.getSummarization().getThreshold();
        if (text.length() <= threshold) {
            return text;
        }
        return text.substring(0, threshold) + ELLIPSIS;
    }
}
