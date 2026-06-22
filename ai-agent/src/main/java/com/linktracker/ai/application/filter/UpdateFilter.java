package com.linktracker.ai.application.filter;

import com.linktracker.ai.application.LinkUpdate;
import com.linktracker.ai.properties.AiAgentProperties;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Applies configured filtering rules: stop-words (FR-2), excluded authors (FR-3) and minimum length (FR-4).
 */
@Component
@RequiredArgsConstructor
public class UpdateFilter {

    private final AiAgentProperties properties;

    /**
     * Evaluates an update against all filtering rules.
     *
     * @param update decoded update
     * @return decision describing whether the update is allowed and why
     */
    public FilterDecision evaluate(LinkUpdate update) {
        AiAgentProperties.Filtering filtering = properties.getFiltering();
        String description = update.description();
        String author = update.author();
        String normalizedDescription = description.toLowerCase(Locale.ROOT);

        for (String stopWord : filtering.getStopWords()) {
            if (stopWord != null
                    && !stopWord.isBlank()
                    && normalizedDescription.contains(stopWord.toLowerCase(Locale.ROOT))) {
                return FilterDecision.reject("stop-word: " + stopWord);
            }
        }

        for (String excludedAuthor : filtering.getExcludedAuthors()) {
            if (excludedAuthor != null && excludedAuthor.equalsIgnoreCase(author)) {
                return FilterDecision.reject("excluded-author: " + author);
            }
        }

        if (description.length() < filtering.getMinLength()) {
            return FilterDecision.reject("min-length: " + description.length() + " < " + filtering.getMinLength());
        }

        return FilterDecision.allow();
    }
}
