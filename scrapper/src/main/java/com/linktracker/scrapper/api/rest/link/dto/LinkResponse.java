package com.linktracker.scrapper.api.rest.link.dto;

import java.util.List;

/**
 * API response item representing tracked link.
 *
 * @param id internal link identifier
 * @param url tracked URL
 * @param tags associated tags
 * @param filters associated filters
 */
public record LinkResponse(long id, String url, List<String> tags, List<String> filters) {}
