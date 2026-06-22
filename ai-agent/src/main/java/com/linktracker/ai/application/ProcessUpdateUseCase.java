package com.linktracker.ai.application;

import com.linktracker.ai.application.filter.FilterDecision;
import com.linktracker.ai.application.filter.UpdateFilter;
import com.linktracker.ai.application.summarization.Summarizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Orchestrates the AI Agent pipeline: filter (FR-2..4), then summarize (FR-5), then publish.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProcessUpdateUseCase {

    private static final String DEFAULT_PRIORITY = "NORMAL";

    private final UpdateFilter filter;
    private final Summarizer summarizer;
    private final ProcessedUpdatePublisher publisher;

    /**
     * Processes a single decoded update.
     *
     * @param update decoded update
     * @param messageId idempotency key propagated from the incoming event (nullable)
     */
    public void process(LinkUpdate update, String messageId) {
        FilterDecision decision = filter.evaluate(update);
        if (!decision.allowed()) {
            log.info("Dropped update id={} reason={}", update.id(), decision.reason());
            return;
        }

        String description = summarizer.summarize(update.description());
        ProcessedUpdate processed =
                new ProcessedUpdate(update.id(), update.url(), description, update.tgChatIds(), DEFAULT_PRIORITY);
        publisher.publish(processed, messageId);
        log.info("Published processed update id={}", update.id());
    }
}
