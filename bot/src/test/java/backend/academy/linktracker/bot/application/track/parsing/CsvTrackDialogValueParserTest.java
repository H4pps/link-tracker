package backend.academy.linktracker.bot.application.track.parsing;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CsvTrackDialogValueParserTest {

    private final CsvTrackDialogValueParser parser = new CsvTrackDialogValueParser();

    @Test
    void parsesCommaSeparatedValues() {
        assertThat(parser.parseList("work, java")).containsExactly("work", "java");
    }

    @Test
    void returnsEmptyListForBlankOrNullInput() {
        assertThat(parser.parseList(" ")).isEmpty();
        assertThat(parser.parseList(null)).isEmpty();
    }

    @Test
    void trimsValuesAndSkipsEmptySegments() {
        assertThat(parser.parseList(" work, ,java,, backend ")).containsExactly("work", "java", "backend");
    }

    @Test
    void supportsUnicodeCommaSeparators() {
        assertThat(parser.parseList("work，java、backend،urgent")).containsExactly("work", "java", "backend", "urgent");
    }
}
