package backend.academy.linktracker.bot.application.track.validation;

import java.net.URI;
import java.util.Arrays;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Validator accepting GitHub repositories and StackOverflow question URLs.
 */
@Component
public class SupportedTrackUrlValidator implements TrackUrlValidator {

    @Override
    public boolean isValid(String candidate) {
        try {
            URI uri = URI.create(candidate);
            if (uri.getScheme() == null || uri.getHost() == null) {
                return false;
            }

            String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
            if (!("http".equals(scheme) || "https".equals(scheme))) {
                return false;
            }

            String host = uri.getHost().toLowerCase(Locale.ROOT);
            String[] segments = Arrays.stream(uri.getPath().split("/"))
                    .filter(part -> !part.isBlank())
                    .toArray(String[]::new);

            boolean github = ("github.com".equals(host) || "www.github.com".equals(host)) && segments.length >= 2;
            boolean stackoverflow = ("stackoverflow.com".equals(host) || "www.stackoverflow.com".equals(host))
                    && segments.length >= 2
                    && "questions".equals(segments[0])
                    && segments[1].chars().allMatch(Character::isDigit);
            return github || stackoverflow;
        } catch (RuntimeException exception) {
            return false;
        }
    }
}
