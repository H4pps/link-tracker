package com.linktracker.scrapper.application.link;

import java.util.List;

/**
 * Immutable projection of tracked link exposed by application layer.
 *
 * @param id internal link identifier
 * @param url tracked URL
 * @param tags associated tags
 * @param filters associated filters
 */
public record LinkView(long id, String url, List<String> tags, List<String> filters) {}
