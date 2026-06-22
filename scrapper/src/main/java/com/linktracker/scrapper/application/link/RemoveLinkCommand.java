package com.linktracker.scrapper.application.link;

/**
 * Command for removing tracked link from chat.
 *
 * @param link URL to stop tracking
 */
public record RemoveLinkCommand(String link) {}
