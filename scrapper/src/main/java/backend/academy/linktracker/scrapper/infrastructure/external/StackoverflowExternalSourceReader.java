package backend.academy.linktracker.scrapper.infrastructure.external;

import backend.academy.linktracker.scrapper.application.external.ExternalSourceException;
import backend.academy.linktracker.scrapper.application.external.ExternalSourceReader;
import backend.academy.linktracker.scrapper.application.external.link.LinkSource;
import backend.academy.linktracker.scrapper.application.external.link.stackoverflow.StackoverflowQuestionLinkSource;
import backend.academy.linktracker.scrapper.application.external.update.ExternalUpdate;
import backend.academy.linktracker.scrapper.application.external.update.ExternalUpdateType;
import backend.academy.linktracker.scrapper.infrastructure.external.dto.stackoverflow.StackoverflowQuestionItem;
import backend.academy.linktracker.scrapper.infrastructure.external.dto.stackoverflow.StackoverflowQuestionsResponse;
import backend.academy.linktracker.scrapper.infrastructure.external.dto.stackoverflow.StackoverflowUpdateItem;
import backend.academy.linktracker.scrapper.infrastructure.external.dto.stackoverflow.StackoverflowUpdatesResponse;
import backend.academy.linktracker.scrapper.logging.ScrapperLogger;
import backend.academy.linktracker.scrapper.properties.StackoverflowProperties;
import java.time.Instant;
import java.util.Optional;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Manual StackOverflow API reader for latest answer/comment updates.
 */
@Component
public class StackoverflowExternalSourceReader implements ExternalSourceReader {

    private final RestClient restClient;
    private final String key;
    private final String accessToken;
    private final ScrapperLogger scrapperLogger;

    /**
     * Creates StackOverflow reader with configured rest client.
     *
     * @param restClientBuilder spring rest client builder
     * @param stackoverflowProperties StackOverflow API properties
     * @param scrapperLogger structured logger
     */
    public StackoverflowExternalSourceReader(
            RestClient.Builder restClientBuilder,
            StackoverflowProperties stackoverflowProperties,
            ScrapperLogger scrapperLogger) {
        this.scrapperLogger = scrapperLogger;
        this.key = stackoverflowProperties.getKey();
        this.accessToken = stackoverflowProperties.getAccessToken();
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(stackoverflowProperties.getConnectTimeout());
        requestFactory.setReadTimeout(stackoverflowProperties.getReadTimeout());
        this.restClient = restClientBuilder
                .baseUrl(stackoverflowProperties.getBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean supports(LinkSource source) {
        return source instanceof StackoverflowQuestionLinkSource;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<ExternalUpdate> fetchLatestUpdate(LinkSource source) {
        StackoverflowQuestionLinkSource stackoverflowSource = cast(source);
        try {
            long questionId = stackoverflowSource.questionId();
            String questionTitle = fetchQuestionTitle(questionId);
            StackoverflowUpdateItem latestAnswer = fetchLatestAnswer(questionId);
            StackoverflowUpdateItem latestComment = fetchLatestComment(questionId);

            if (latestAnswer == null && latestComment == null) {
                return Optional.empty();
            }

            if (isMoreRecent(latestAnswer, latestComment)) {
                return Optional.of(mapUpdate(ExternalUpdateType.STACKOVERFLOW_ANSWER, questionTitle, latestAnswer));
            }
            return Optional.of(mapUpdate(ExternalUpdateType.STACKOVERFLOW_COMMENT, questionTitle, latestComment));
        } catch (RuntimeException exception) {
            scrapperLogger.logExternalFetchFailed(
                    "stackoverflow",
                    "https://stackoverflow.com/questions/" + stackoverflowSource.questionId(),
                    exception.getClass().getSimpleName());
            if (exception instanceof ExternalSourceException externalSourceException) {
                throw externalSourceException;
            }
            throw new ExternalSourceException("Failed to fetch StackOverflow update metadata", exception);
        }
    }

    private String fetchQuestionTitle(long questionId) {
        StackoverflowQuestionsResponse response = restClient
                .get()
                .uri(
                        "/2.3/questions/{id}?site=stackoverflow&key={key}&access_token={accessToken}",
                        questionId,
                        key,
                        accessToken)
                .retrieve()
                .body(StackoverflowQuestionsResponse.class);
        if (response == null || response.items() == null || response.items().isEmpty()) {
            throw new ExternalSourceException("StackOverflow question response has no items", null);
        }

        StackoverflowQuestionItem question = response.items().getFirst();
        if (question == null || question.title() == null || question.title().isBlank()) {
            throw new ExternalSourceException("StackOverflow question response missing title", null);
        }
        return question.title();
    }

    private StackoverflowUpdateItem fetchLatestAnswer(long questionId) {
        StackoverflowUpdatesResponse response = restClient
                .get()
                .uri(
                        "/2.3/questions/{id}/answers?site=stackoverflow&sort=creation&order=desc&pagesize=1"
                                + "&filter=withbody&key={key}&access_token={accessToken}",
                        questionId,
                        key,
                        accessToken)
                .retrieve()
                .body(StackoverflowUpdatesResponse.class);
        return readLatestUpdateItem(response);
    }

    private StackoverflowUpdateItem fetchLatestComment(long questionId) {
        StackoverflowUpdatesResponse response = restClient
                .get()
                .uri(
                        "/2.3/questions/{id}/comments?site=stackoverflow&sort=creation&order=desc&pagesize=1"
                                + "&filter=withbody&key={key}&access_token={accessToken}",
                        questionId,
                        key,
                        accessToken)
                .retrieve()
                .body(StackoverflowUpdatesResponse.class);
        return readLatestUpdateItem(response);
    }

    private StackoverflowUpdateItem readLatestUpdateItem(StackoverflowUpdatesResponse response) {
        if (response == null || response.items() == null || response.items().isEmpty()) {
            return null;
        }
        return response.items().getFirst();
    }

    private boolean isMoreRecent(StackoverflowUpdateItem first, StackoverflowUpdateItem second) {
        if (first == null) {
            return false;
        }
        if (second == null) {
            return true;
        }
        long firstCreatedAt = readCreationDate(first);
        long secondCreatedAt = readCreationDate(second);
        return firstCreatedAt >= secondCreatedAt;
    }

    private ExternalUpdate mapUpdate(
            ExternalUpdateType type, String questionTitle, StackoverflowUpdateItem updateItem) {
        if (updateItem == null
                || updateItem.owner() == null
                || updateItem.owner().displayName() == null
                || updateItem.owner().displayName().isBlank()) {
            throw new ExternalSourceException("StackOverflow update response missing owner", null);
        }
        long createdAt = readCreationDate(updateItem);
        return new ExternalUpdate(
                type,
                Instant.ofEpochSecond(createdAt),
                questionTitle,
                updateItem.owner().displayName(),
                updateItem.body() == null ? "" : updateItem.body());
    }

    private long readCreationDate(StackoverflowUpdateItem updateItem) {
        if (updateItem.creationDate() == null || updateItem.creationDate() <= 0) {
            throw new ExternalSourceException("StackOverflow update response missing creation_date", null);
        }
        return updateItem.creationDate();
    }

    private StackoverflowQuestionLinkSource cast(LinkSource source) {
        if (!(source instanceof StackoverflowQuestionLinkSource stackoverflowSource)) {
            throw new IllegalArgumentException("Expected StackoverflowQuestionLinkSource, got: "
                    + source.getClass().getSimpleName());
        }
        return stackoverflowSource;
    }
}
