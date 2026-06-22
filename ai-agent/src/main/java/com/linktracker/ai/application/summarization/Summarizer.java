package com.linktracker.ai.application.summarization;

/**
 * Summarizes update text when it exceeds the configured threshold (FR-5).
 *
 * <p>Implementations are mutually exclusive and selected via {@code ai-agent.summarization.mode}.
 */
public interface Summarizer {

    /**
     * Summarizes the given text if it exceeds the configured threshold, otherwise returns it unchanged.
     *
     * @param text original update text
     * @return summarized text, or the original text when short enough
     */
    String summarize(String text);
}
