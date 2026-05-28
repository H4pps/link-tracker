package backend.academy.linktracker.bot.application.track.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SupportedTrackUrlValidatorTest {

    private final SupportedTrackUrlValidator validator = new SupportedTrackUrlValidator();

    @Test
    void acceptsGithubRepositoryUrl() {
        assertThat(validator.isValid("https://github.com/octocat/Hello-World")).isTrue();
    }

    @Test
    void acceptsStackoverflowQuestionUrl() {
        assertThat(validator.isValid("https://stackoverflow.com/questions/12345/example"))
                .isTrue();
    }

    @Test
    void rejectsUnsupportedScheme() {
        assertThat(validator.isValid("ftp://github.com/octocat/Hello-World")).isFalse();
    }

    @Test
    void rejectsMalformedAndBlankUrls() {
        assertThat(validator.isValid("https://[invalid")).isFalse();
        assertThat(validator.isValid("   ")).isFalse();
    }

    @Test
    void rejectsUnsupportedStackoverflowPath() {
        assertThat(validator.isValid("https://stackoverflow.com/users/12345/example"))
                .isFalse();
    }
}
