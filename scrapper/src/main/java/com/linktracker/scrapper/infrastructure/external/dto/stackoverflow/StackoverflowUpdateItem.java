package com.linktracker.scrapper.infrastructure.external.dto.stackoverflow;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * StackOverflow answer/comment item subset used by scheduler.
 *
 * @param creationDate creation unix timestamp in seconds
 * @param body answer or comment body
 * @param owner answer or comment owner
 */
public record StackoverflowUpdateItem(
        @JsonProperty("creation_date") Long creationDate, String body, StackoverflowOwnerItem owner) {}
