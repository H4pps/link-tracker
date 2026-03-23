package backend.academy.linktracker.scrapper.application.external;

/**
 * Parsed StackOverflow question source.
 *
 * @param questionId question identifier
 */
public record StackoverflowQuestionLinkSource(long questionId) implements LinkSource {}
