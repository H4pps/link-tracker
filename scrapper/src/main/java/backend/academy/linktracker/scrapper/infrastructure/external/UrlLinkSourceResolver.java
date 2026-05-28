package backend.academy.linktracker.scrapper.infrastructure.external;

import backend.academy.linktracker.scrapper.application.external.GithubLinkSource;
import backend.academy.linktracker.scrapper.application.external.LinkSource;
import backend.academy.linktracker.scrapper.application.external.LinkSourceResolver;
import backend.academy.linktracker.scrapper.application.external.StackoverflowQuestionLinkSource;
import java.net.URI;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * URL-based resolver for GitHub repositories and StackOverflow questions.
 */
@Component
public class UrlLinkSourceResolver implements LinkSourceResolver {

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<LinkSource> resolve(String url) {
        try {
            URI uri = URI.create(url);
            if (uri.getHost() == null) {
                return Optional.empty();
            }
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            String[] pathSegments = Arrays.stream(uri.getPath().split("/"))
                    .filter(segment -> !segment.isBlank())
                    .toArray(String[]::new);

            if (("github.com".equals(host) || "www.github.com".equals(host)) && pathSegments.length >= 2) {
                return Optional.of(new GithubLinkSource(pathSegments[0], pathSegments[1]));
            }
            if (("stackoverflow.com".equals(host) || "www.stackoverflow.com".equals(host))
                    && pathSegments.length >= 2
                    && "questions".equals(pathSegments[0])
                    && pathSegments[1].chars().allMatch(Character::isDigit)) {
                return Optional.of(new StackoverflowQuestionLinkSource(Long.parseLong(pathSegments[1])));
            }
            return Optional.empty();
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }
}
