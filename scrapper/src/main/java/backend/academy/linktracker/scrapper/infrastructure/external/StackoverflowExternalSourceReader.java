package backend.academy.linktracker.scrapper.infrastructure.external;

import backend.academy.linktracker.scrapper.application.external.ExternalSourceException;
import backend.academy.linktracker.scrapper.application.external.ExternalSourceReader;
import backend.academy.linktracker.scrapper.application.external.LinkSource;
import backend.academy.linktracker.scrapper.application.external.StackoverflowQuestionLinkSource;
import backend.academy.linktracker.scrapper.infrastructure.external.dto.StackoverflowQuestionItem;
import backend.academy.linktracker.scrapper.infrastructure.external.dto.StackoverflowQuestionsResponse;
import backend.academy.linktracker.scrapper.logging.ScrapperLogger;
import backend.academy.linktracker.scrapper.properties.StackoverflowProperties;
import java.time.Instant;
import java.util.List;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Manual StackOverflow API reader for question update timestamps.
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
    public Instant fetchLastUpdated(LinkSource source) {
        StackoverflowQuestionLinkSource stackoverflowSource = cast(source);
        try {
            StackoverflowQuestionsResponse response = restClient
                    .get()
                    .uri(
                            "/2.3/questions/{id}?site=stackoverflow&key={key}&access_token={accessToken}",
                            stackoverflowSource.questionId(),
                            key,
                            accessToken)
                    .retrieve()
                    .body(StackoverflowQuestionsResponse.class);
            if (response == null || response.items() == null || response.items().isEmpty()) {
                throw new ExternalSourceException("StackOverflow response has no items", null);
            }
            List<StackoverflowQuestionItem> items = response.items();
            Long lastActivityDate = items.getFirst().lastActivityDate();
            if (lastActivityDate == null || lastActivityDate <= 0) {
                throw new ExternalSourceException("StackOverflow response missing last_activity_date", null);
            }
            return Instant.ofEpochSecond(lastActivityDate);
        } catch (RuntimeException exception) {
            scrapperLogger.logExternalFetchFailed(
                    "stackoverflow",
                    "https://stackoverflow.com/questions/" + stackoverflowSource.questionId(),
                    exception.getClass().getSimpleName());
            if (exception instanceof ExternalSourceException externalSourceException) {
                throw externalSourceException;
            }
            throw new ExternalSourceException("Failed to fetch StackOverflow question metadata", exception);
        }
    }

    private StackoverflowQuestionLinkSource cast(LinkSource source) {
        if (!(source instanceof StackoverflowQuestionLinkSource stackoverflowSource)) {
            throw new IllegalArgumentException("Expected StackoverflowQuestionLinkSource, got: "
                    + source.getClass().getSimpleName());
        }
        return stackoverflowSource;
    }
}
