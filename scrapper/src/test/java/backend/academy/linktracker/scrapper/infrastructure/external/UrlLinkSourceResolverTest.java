package backend.academy.linktracker.scrapper.infrastructure.external;

import static org.assertj.core.api.Assertions.assertThat;

import backend.academy.linktracker.scrapper.application.external.GithubLinkSource;
import backend.academy.linktracker.scrapper.application.external.StackoverflowQuestionLinkSource;
import org.junit.jupiter.api.Test;

class UrlLinkSourceResolverTest {

    private final UrlLinkSourceResolver resolver = new UrlLinkSourceResolver();

    @Test
    void resolvesGithubRepositoryUrl() {
        var source = resolver.resolve("https://github.com/octocat/Hello-World");

        assertThat(source).containsInstanceOf(GithubLinkSource.class);
    }

    @Test
    void resolvesStackoverflowQuestionUrl() {
        var source = resolver.resolve("https://stackoverflow.com/questions/123456/title");

        assertThat(source).containsInstanceOf(StackoverflowQuestionLinkSource.class);
    }

    @Test
    void returnsEmptyForUnsupportedUrl() {
        assertThat(resolver.resolve("https://example.com")).isEmpty();
    }
}
