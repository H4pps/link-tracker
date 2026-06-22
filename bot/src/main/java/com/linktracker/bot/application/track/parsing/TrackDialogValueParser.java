package com.linktracker.bot.application.track.parsing;

import java.util.List;

/**
 * Parser for list-like values collected during `/track` dialog.
 */
public interface TrackDialogValueParser {

    /**
     * Parses user-entered list values.
     *
     * @param input raw user input
     * @return parsed values
     */
    List<String> parseList(String input);
}
