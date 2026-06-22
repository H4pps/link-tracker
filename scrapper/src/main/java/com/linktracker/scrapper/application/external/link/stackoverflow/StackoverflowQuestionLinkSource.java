package com.linktracker.scrapper.application.external.link.stackoverflow;

import com.linktracker.scrapper.application.external.link.LinkSource;

/**
 * Parsed StackOverflow question source.
 *
 * @param questionId question identifier
 */
public record StackoverflowQuestionLinkSource(long questionId) implements LinkSource {}
