package com.linktracker.scrapper.application.update;

import static org.assertj.core.api.Assertions.assertThat;

import com.linktracker.scrapper.application.external.update.ExternalUpdate;
import com.linktracker.scrapper.application.external.update.ExternalUpdateType;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExternalUpdateDescriptionFormatterTest {

    private static final int PREVIEW_LIMIT = 200;
    private static final Instant CREATED_AT = Instant.parse("2024-06-01T10:11:12Z");

    private ExternalUpdateDescriptionFormatter formatter;

    @BeforeEach
    void setUp() {
        formatter = new ExternalUpdateDescriptionFormatter();
    }

    @Test
    void formatsGithubIssueDisplayType() {
        String description = formatter.format(update(ExternalUpdateType.GITHUB_ISSUE, "preview"));

        assertThat(description).contains("Type: GitHub Issue");
    }

    @Test
    void formatsGithubPullRequestDisplayType() {
        String description = formatter.format(update(ExternalUpdateType.GITHUB_PULL_REQUEST, "preview"));

        assertThat(description).contains("Type: GitHub Pull Request");
    }

    @Test
    void formatsStackoverflowAnswerDisplayType() {
        String description = formatter.format(update(ExternalUpdateType.STACKOVERFLOW_ANSWER, "preview"));

        assertThat(description).contains("Type: StackOverflow Answer");
    }

    @Test
    void formatsStackoverflowCommentDisplayType() {
        String description = formatter.format(update(ExternalUpdateType.STACKOVERFLOW_COMMENT, "preview"));

        assertThat(description).contains("Type: StackOverflow Comment");
    }

    @Test
    void truncatesPreviewToTwoHundredCharacters() {
        String longPreview = "a".repeat(PREVIEW_LIMIT) + "TRUNCATED";
        String description = formatter.format(update(ExternalUpdateType.GITHUB_ISSUE, longPreview));

        assertThat(description).contains("Preview: " + "a".repeat(PREVIEW_LIMIT));
        assertThat(description).doesNotContain("TRUNCATED");
    }

    @Test
    void handlesNullTitleAuthorAndPreviewAsEmptyStrings() {
        ExternalUpdate update = new ExternalUpdate(ExternalUpdateType.GITHUB_ISSUE, CREATED_AT, null, null, null);

        String description = formatter.format(update);

        assertThat(description).contains("Type: GitHub Issue");
        assertThat(description).contains("Title: ");
        assertThat(description).contains("Author: ");
        assertThat(description).contains("Created at: " + CREATED_AT);
        assertThat(description).contains("Preview:");
        assertThat(description).doesNotContain("Title: null");
        assertThat(description).doesNotContain("Author: null");
        assertThat(description).doesNotContain("Preview: null");
    }

    private ExternalUpdate update(ExternalUpdateType type, String preview) {
        return new ExternalUpdate(type, CREATED_AT, "title", "author", preview);
    }
}
