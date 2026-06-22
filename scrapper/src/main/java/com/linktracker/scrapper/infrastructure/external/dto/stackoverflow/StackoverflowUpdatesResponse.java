package com.linktracker.scrapper.infrastructure.external.dto.stackoverflow;

import java.util.List;

/**
 * StackOverflow updates API response subset.
 *
 * @param items response items
 */
public record StackoverflowUpdatesResponse(List<StackoverflowUpdateItem> items) {}
