package backend.academy.linktracker.ai.application.summarization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import backend.academy.linktracker.ai.properties.AiAgentProperties;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

class AiSummarizerTest {

    private AiAgentProperties propertiesWithThreshold(int threshold) {
        AiAgentProperties properties = new AiAgentProperties();
        properties.getSummarization().setThreshold(threshold);
        return properties;
    }

    @Test
    void summarizesLongTextViaChatClient() {
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(chatClient
                        .prompt()
                        .user(org.mockito.ArgumentMatchers.anyString())
                        .call()
                        .content())
                .thenReturn("A concise AI summary.");
        AiSummarizer summarizer = new AiSummarizer(chatClient, propertiesWithThreshold(10));

        String result = summarizer.summarize("This long text definitely exceeds the configured threshold value");

        assertThat(result).isEqualTo("A concise AI summary.");
    }

    @Test
    void leavesShortTextUnchangedWithoutCallingChatClient() {
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        AiSummarizer summarizer = new AiSummarizer(chatClient, propertiesWithThreshold(500));

        String result = summarizer.summarize("short update");

        assertThat(result).isEqualTo("short update");
        verifyNoInteractions(chatClient);
    }
}
