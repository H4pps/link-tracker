package com.linktracker.bot.application.scrapper.view;

import java.util.List;

/**
 * Tracked link projection returned by scrapper gateway.
 *
 * @param id link identifier
 * @param url tracked URL
 * @param tags attached tags
 * @param filters attached filters
 */
public record ScrapperLinkView(long id, String url, List<String> tags, List<String> filters) {

    /**
     * Canonical constructor normalizing nullable collections.
     *
     * @param id link identifier
     * @param url tracked URL
     * @param tags tags list
     * @param filters filters list
     */
    public ScrapperLinkView {
        tags = tags == null ? List.of() : List.copyOf(tags);
        filters = filters == null ? List.of() : List.copyOf(filters);
    }
}
