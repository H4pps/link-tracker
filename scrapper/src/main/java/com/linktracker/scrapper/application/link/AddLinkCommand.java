package com.linktracker.scrapper.application.link;

import java.util.List;

/**
 * Command for adding tracked link for chat.
 *
 * @param link URL to track
 * @param tags optional tag list
 * @param filters optional filter list
 */
public record AddLinkCommand(String link, List<String> tags, List<String> filters) {}
