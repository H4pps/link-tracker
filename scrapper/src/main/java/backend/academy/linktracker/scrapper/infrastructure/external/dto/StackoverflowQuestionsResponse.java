package backend.academy.linktracker.scrapper.infrastructure.external.dto;

import java.util.List;

/**
 * Subset of StackOverflow questions API response used by scheduler.
 *
 * @param items response items
 */
public record StackoverflowQuestionsResponse(List<StackoverflowQuestionItem> items) {}
