package com.linktracker.scrapper.application.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.linktracker.scrapper.ScrapperApplication;
import com.linktracker.scrapper.application.chat.ScrapperChatUseCase;
import com.linktracker.scrapper.application.external.update.ExternalUpdate;
import com.linktracker.scrapper.application.external.update.ExternalUpdateType;
import com.linktracker.scrapper.application.link.AddLinkCommand;
import com.linktracker.scrapper.application.link.LinkView;
import com.linktracker.scrapper.application.link.RemoveLinkCommand;
import com.linktracker.scrapper.application.link.ScrapperLinkUseCase;
import com.linktracker.scrapper.application.pagination.RepositoryPageRequest;
import com.linktracker.scrapper.application.update.BotNotificationSender;
import com.linktracker.scrapper.application.update.LinkUpdateCheckpointRepository;
import com.linktracker.scrapper.application.update.LinkUpdateNotification;
import com.linktracker.scrapper.application.update.LinkUpdateSchedulerUseCase;
import com.linktracker.scrapper.domain.exception.AlreadyExistsException;
import com.linktracker.scrapper.domain.exception.NotFoundException;
import com.linktracker.scrapper.infrastructure.external.GithubExternalSourceReader;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
abstract class AbstractScrapperApplicationIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18-alpine")
            .withDatabaseName("link_tracker")
            .withUsername("link_tracker")
            .withPassword("link_tracker");

    @DynamicPropertySource
    static void registerDatasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ScrapperChatUseCase chatUseCase;

    @Autowired
    private ScrapperLinkUseCase linkUseCase;

    @Autowired
    private LinkUpdateSchedulerUseCase schedulerUseCase;

    @Autowired
    private LinkUpdateCheckpointRepository checkpointRepository;

    @MockitoBean
    private GithubExternalSourceReader githubExternalSourceReader;

    @MockitoBean
    private BotNotificationSender botNotificationSender;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute(
                "TRUNCATE TABLE link_update_outbox, link_update_checkpoints, subscription_filters, subscription_tags, subscriptions, "
                        + "tags, links, chats RESTART IDENTITY CASCADE");
    }

    @Test
    void useCasesSupportRegisterAddListPageDuplicateRemoveAndMissingCases() {
        long chatId = 101L;
        String firstUrl = "https://github.com/acme/first";
        String secondUrl = "https://github.com/acme/second";
        String missingUrl = "https://github.com/acme/missing";

        chatUseCase.registerChat(chatId);
        assertThatThrownBy(() -> chatUseCase.registerChat(chatId))
                .isInstanceOf(AlreadyExistsException.class)
                .hasMessage("Chat already registered: " + chatId);

        LinkView first =
                linkUseCase.addLink(chatId, new AddLinkCommand(firstUrl, List.of("z", "a"), List.of("f2", "f1")));
        LinkView second =
                linkUseCase.addLink(chatId, new AddLinkCommand(secondUrl, List.of("d", "c"), List.of("f4", "f3")));

        assertThat(linkUseCase.listLinks(chatId))
                .containsExactly(
                        new LinkView(first.id(), first.url(), List.of("a", "z"), List.of("f2", "f1")),
                        new LinkView(second.id(), second.url(), List.of("c", "d"), List.of("f4", "f3")));
        assertThat(linkUseCase.listLinks(chatId, new RepositoryPageRequest(1, 1)))
                .containsExactly(new LinkView(second.id(), second.url(), List.of("c", "d"), List.of("f4", "f3")));

        assertThatThrownBy(() -> linkUseCase.addLink(chatId, new AddLinkCommand(firstUrl, List.of("dup"), List.of())))
                .isInstanceOf(AlreadyExistsException.class)
                .hasMessage("Link already tracked for chat: " + firstUrl);

        LinkView removed = linkUseCase.removeLink(chatId, new RemoveLinkCommand(firstUrl));
        assertThat(removed).isEqualTo(new LinkView(first.id(), first.url(), List.of("a", "z"), List.of("f2", "f1")));

        assertThatThrownBy(() -> linkUseCase.removeLink(chatId, new RemoveLinkCommand(firstUrl)))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Link not found for chat: " + firstUrl);
        assertThatThrownBy(() -> linkUseCase.removeLink(chatId, new RemoveLinkCommand(missingUrl)))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Link not found for chat: " + missingUrl);

        assertMissingChatOperationsThrow(999L, "https://github.com/acme/absent");
    }

    @Test
    void dataPersistsAcrossTwoSpringContextsWithSameAccessType() {
        long chatId = 202L;
        String url = "https://github.com/acme/persisted";
        LinkView persistedLink;

        chatUseCase.registerChat(chatId);
        persistedLink = linkUseCase.addLink(chatId, new AddLinkCommand(url, List.of("db"), List.of("state")));

        try (ConfigurableApplicationContext secondContext = runSecondContext()) {
            ScrapperChatUseCase secondChatUseCase = secondContext.getBean(ScrapperChatUseCase.class);
            ScrapperLinkUseCase secondLinkUseCase = secondContext.getBean(ScrapperLinkUseCase.class);

            assertThatThrownBy(() -> secondChatUseCase.registerChat(chatId))
                    .isInstanceOf(AlreadyExistsException.class)
                    .hasMessage("Chat already registered: " + chatId);
            assertThat(secondLinkUseCase.listLinks(chatId))
                    .containsExactly(
                            new LinkView(persistedLink.id(), persistedLink.url(), List.of("db"), List.of("state")));
        }
    }

    @Test
    void schedulerReadsDatabaseSnapshotsCreatesCheckpointsAndNotifiesOnChanges() {
        long firstChatId = 301L;
        long secondChatId = 302L;
        long thirdChatId = 303L;
        String sharedUrl = "https://github.com/acme/shared";
        String uniqueUrl = "https://github.com/acme/unique";
        Instant firstTimestamp = Instant.parse("2026-01-10T00:00:00Z");
        Instant changedTimestamp = Instant.parse("2026-02-10T00:00:00Z");

        chatUseCase.registerChat(firstChatId);
        chatUseCase.registerChat(secondChatId);
        chatUseCase.registerChat(thirdChatId);

        LinkView sharedFirst = linkUseCase.addLink(firstChatId, new AddLinkCommand(sharedUrl, List.of(), List.of()));
        LinkView sharedSecond = linkUseCase.addLink(secondChatId, new AddLinkCommand(sharedUrl, List.of(), List.of()));
        LinkView unique = linkUseCase.addLink(thirdChatId, new AddLinkCommand(uniqueUrl, List.of(), List.of()));
        assertThat(sharedFirst.id()).isEqualTo(sharedSecond.id());

        AtomicReference<Instant> externalTimestamp = new AtomicReference<>(firstTimestamp);
        when(githubExternalSourceReader.supports(any())).thenReturn(true);
        when(githubExternalSourceReader.fetchLatestUpdate(any()))
                .thenAnswer(ignored -> Optional.of(new ExternalUpdate(
                        ExternalUpdateType.GITHUB_ISSUE, externalTimestamp.get(), "title", "author", "preview")));
        when(botNotificationSender.send(any())).thenReturn(true);

        schedulerUseCase.checkUpdates();

        assertThat(checkpointRepository.findByUrl(sharedUrl)).contains(firstTimestamp);
        assertThat(checkpointRepository.findByUrl(uniqueUrl)).contains(firstTimestamp);
        verify(botNotificationSender, never()).send(any());

        schedulerUseCase.checkUpdates();
        verify(botNotificationSender, never()).send(any());

        externalTimestamp.set(changedTimestamp);
        schedulerUseCase.checkUpdates();

        String expectedDescription = """
                Type: GitHub Issue
                Title: title
                Author: author
                Created at: %s
                Preview: preview
                """.formatted(changedTimestamp).trim();

        ArgumentCaptor<LinkUpdateNotification> notificationCaptor =
                ArgumentCaptor.forClass(LinkUpdateNotification.class);
        verify(botNotificationSender, times(2)).send(notificationCaptor.capture());
        assertThat(notificationCaptor.getAllValues())
                .containsExactlyInAnyOrder(
                        new LinkUpdateNotification(
                                sharedFirst.id(),
                                sharedUrl,
                                expectedDescription,
                                "author",
                                List.of(firstChatId, secondChatId)),
                        new LinkUpdateNotification(
                                unique.id(), uniqueUrl, expectedDescription, "author", List.of(thirdChatId)));

        assertThat(checkpointRepository.findByUrl(sharedUrl)).contains(changedTimestamp);
        assertThat(checkpointRepository.findByUrl(uniqueUrl)).contains(changedTimestamp);
    }

    protected abstract String accessType();

    private ConfigurableApplicationContext runSecondContext() {
        return new SpringApplicationBuilder(ScrapperApplication.class)
                .web(WebApplicationType.NONE)
                .run(
                        "--server.port=0",
                        "--app.grpc.server.port=0",
                        "--app.scheduler.enabled=false",
                        "--app.scheduler.link-page-size=50",
                        "--app.cache.list-links.enabled=false",
                        "--app.database.access-type=" + accessType(),
                        "--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                        "--spring.datasource.username=" + POSTGRES.getUsername(),
                        "--spring.datasource.password=" + POSTGRES.getPassword(),
                        "--app.github.token=test-github-token",
                        "--app.stackoverflow.key=test-stackoverflow-key",
                        "--app.stackoverflow.access-token=test-stackoverflow-access-token");
    }

    private void assertMissingChatOperationsThrow(long chatId, String url) {
        assertThatThrownBy(() -> linkUseCase.listLinks(chatId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Chat not found: " + chatId);
        assertThatThrownBy(() -> linkUseCase.addLink(chatId, new AddLinkCommand(url, List.of(), List.of())))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Chat not found: " + chatId);
        assertThatThrownBy(() -> linkUseCase.removeLink(chatId, new RemoveLinkCommand(url)))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Chat not found: " + chatId);
    }
}
