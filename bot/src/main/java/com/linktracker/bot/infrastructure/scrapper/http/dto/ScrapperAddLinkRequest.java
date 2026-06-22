package com.linktracker.bot.infrastructure.scrapper.http.dto;

import java.util.List;

/**
 * Scrapper add-link request payload.
 *
 * @param link tracked URL
 * @param tags optional tags
 * @param filters optional filters
 */
public record ScrapperAddLinkRequest(String link, List<String> tags, List<String> filters) {}
