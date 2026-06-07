package backend.academy.linktracker.ai.application.summarization;

import backend.academy.linktracker.ai.properties.AiAgentProperties;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * AI summarizer backed by a Spring AI {@link ChatClient} (OpenAI-compatible). Active when
 * {@code ai-agent.summarization.mode=ai}. Falls back to truncation if the model returns nothing.
 */
@Component
@ConditionalOnProperty(prefix = "ai-agent.summarization", name = "mode", havingValue = "ai")
public class AiSummarizer implements Summarizer {

    private static final String PROMPT_PREFIX = "Summarize the following update in 2-3 sentences:\n\n";
    private static final String ELLIPSIS = "...";

    private final ChatClient chatClient;
    private final AiAgentProperties properties;

    /**
     * Production constructor wiring the autoconfigured Spring AI {@link ChatClient.Builder}.
     *
     * @param chatClientBuilder Spring AI chat client builder
     * @param properties AI Agent properties
     */
    public AiSummarizer(ChatClient.Builder chatClientBuilder, AiAgentProperties properties) {
        this(chatClientBuilder.build(), properties);
    }

    /**
     * Test-friendly constructor accepting a ready {@link ChatClient}.
     *
     * @param chatClient chat client
     * @param properties AI Agent properties
     */
    AiSummarizer(ChatClient chatClient, AiAgentProperties properties) {
        this.chatClient = chatClient;
        this.properties = properties;
    }

    @Override
    public String summarize(String text) {
        if (text == null) {
            return null;
        }
        int threshold = properties.getSummarization().getThreshold();
        if (text.length() <= threshold) {
            return text;
        }
        String summary = chatClient.prompt().user(PROMPT_PREFIX + text).call().content();
        if (summary == null || summary.isBlank()) {
            return text.substring(0, threshold) + ELLIPSIS;
        }
        return summary;
    }
}
