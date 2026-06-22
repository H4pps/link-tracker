package com.linktracker.bot.application.scrapper.command;

import java.util.List;

/**
 * Add-link command payload for scrapper gateway.
 *
 * @param url tracked URL
 * @param tags tags bound to URL
 * @param filters filters bound to URL
 */
public record AddScrapperLinkCommand(String url, List<String> tags, List<String> filters) {

    /**
     * Canonical constructor normalizing nullable collections.
     *
     * @param url tracked URL
     * @param tags tags list
     * @param filters filters list
     */
    public AddScrapperLinkCommand {
        tags = tags == null ? List.of() : List.copyOf(tags);
        filters = filters == null ? List.of() : List.copyOf(filters);
    }
}
