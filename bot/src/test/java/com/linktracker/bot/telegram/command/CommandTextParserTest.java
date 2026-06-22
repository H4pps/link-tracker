package com.linktracker.bot.telegram.command;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CommandTextParserTest {

    private final CommandTextParser parser = new CommandTextParser();

    @Test
    void parsesCommandWithoutArguments() {
        ParsedCommand parsedCommand = parser.parse("/help");

        assertThat(parsedCommand.inputCommand()).isEqualTo("/help");
        assertThat(parsedCommand.normalizedCommand()).isEqualTo("help");
        assertThat(parsedCommand.argument()).isEmpty();
    }

    @Test
    void parsesCommandWithMention() {
        ParsedCommand parsedCommand = parser.parse("/help@linktrackerbot");

        assertThat(parsedCommand.inputCommand()).isEqualTo("/help@linktrackerbot");
        assertThat(parsedCommand.normalizedCommand()).isEqualTo("help");
        assertThat(parsedCommand.argument()).isEmpty();
    }

    @Test
    void parsesCommandWithArguments() {
        ParsedCommand parsedCommand = parser.parse("/help extra args");

        assertThat(parsedCommand.inputCommand()).isEqualTo("/help");
        assertThat(parsedCommand.normalizedCommand()).isEqualTo("help");
        assertThat(parsedCommand.argument()).isEqualTo("extra args");
    }

    @Test
    void parsesUnknownText() {
        ParsedCommand parsedCommand = parser.parse("hello there");

        assertThat(parsedCommand.inputCommand()).isEqualTo("hello");
        assertThat(parsedCommand.normalizedCommand()).isEqualTo("hello");
        assertThat(parsedCommand.argument()).isEqualTo("there");
    }

    @Test
    void parsesNullText() {
        ParsedCommand parsedCommand = parser.parse(null);

        assertThat(parsedCommand.inputCommand()).isEmpty();
        assertThat(parsedCommand.normalizedCommand()).isEmpty();
        assertThat(parsedCommand.argument()).isEmpty();
    }

    @Test
    void parsesBlankText() {
        ParsedCommand parsedCommand = parser.parse("   ");

        assertThat(parsedCommand.inputCommand()).isEmpty();
        assertThat(parsedCommand.normalizedCommand()).isEmpty();
        assertThat(parsedCommand.argument()).isEmpty();
    }
}
