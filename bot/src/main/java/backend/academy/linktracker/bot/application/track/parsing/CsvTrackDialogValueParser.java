package backend.academy.linktracker.bot.application.track.parsing;

import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Parses comma-separated tag and filter values from `/track` dialog.
 */
@Component
public class CsvTrackDialogValueParser implements TrackDialogValueParser {

    private static final String SEPARATOR_REGEX = "[,，、،]+";

    @Override
    public List<String> parseList(String input) {
        String normalized = normalize(input);
        if (normalized.isBlank()) {
            return List.of();
        }

        return Arrays.stream(normalized.split(SEPARATOR_REGEX))
                .map(String::strip)
                .filter(value -> !value.isBlank())
                .toList();
    }

    private String normalize(String input) {
        if (input == null) {
            return "";
        }
        return input.strip();
    }
}
