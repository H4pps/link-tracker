package backend.academy.linktracker.scrapper.infrastructure.memory.sql;

import static org.assertj.core.api.Assertions.assertThat;

import backend.academy.linktracker.scrapper.ScrapperApplication;
import backend.academy.linktracker.scrapper.application.chat.ScrapperChatRepository;
import backend.academy.linktracker.scrapper.application.link.ScrapperLinkRepository;
import backend.academy.linktracker.scrapper.application.tag.TagRepository;
import backend.academy.linktracker.scrapper.application.update.LinkUpdateCheckpointRepository;
import backend.academy.linktracker.scrapper.infrastructure.memory.inmemory.InMemoryLinkUpdateCheckpointRepository;
import backend.academy.linktracker.scrapper.infrastructure.memory.inmemory.InMemoryScrapperChatRepository;
import backend.academy.linktracker.scrapper.infrastructure.memory.inmemory.InMemoryScrapperLinkRepository;
import backend.academy.linktracker.scrapper.infrastructure.memory.inmemory.InMemoryScrapperStorage;
import backend.academy.linktracker.scrapper.infrastructure.memory.inmemory.InMemoryTagRepository;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(
        classes = ScrapperApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
            "server.port=0",
            "app.grpc.server.port=0",
            "app.scheduler.enabled=false",
            "app.github.token=test-github-token",
            "app.stackoverflow.key=test-stackoverflow-key",
            "app.stackoverflow.access-token=test-stackoverflow-access-token"
        })
class SqlDefaultAccessTypeIntegrationTest {

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
    private ApplicationContext applicationContext;

    @Test
    void sqlRepositoriesAreDefaultWhenAccessTypeIsMissing() {
        assertThat(applicationContext.getEnvironment().getProperty("app.database.access-type"))
                .isEqualTo("SQL");
        assertSingleRepositoryImplementation(ScrapperChatRepository.class, "SqlScrapperChatRepository");
        assertSingleRepositoryImplementation(ScrapperLinkRepository.class, "SqlScrapperLinkRepository");
        assertSingleRepositoryImplementation(TagRepository.class, "SqlTagRepository");
        assertSingleRepositoryImplementation(LinkUpdateCheckpointRepository.class, "SqlLinkUpdateCheckpointRepository");

        assertThat(applicationContext.getBeansOfType(InMemoryScrapperStorage.class))
                .isEmpty();
        assertThat(applicationContext.getBeansOfType(InMemoryScrapperChatRepository.class))
                .isEmpty();
        assertThat(applicationContext.getBeansOfType(InMemoryScrapperLinkRepository.class))
                .isEmpty();
        assertThat(applicationContext.getBeansOfType(InMemoryLinkUpdateCheckpointRepository.class))
                .isEmpty();
        assertThat(applicationContext.getBeansOfType(InMemoryTagRepository.class))
                .isEmpty();
    }

    private <T> void assertSingleRepositoryImplementation(Class<T> repositoryType, String expectedSimpleClassName) {
        List<String> implementationSimpleNames = applicationContext.getBeansOfType(repositoryType).values().stream()
                .map(bean -> AopUtils.getTargetClass(bean).getSimpleName())
                .sorted(Comparator.naturalOrder())
                .toList();

        assertThat(implementationSimpleNames).containsExactly(expectedSimpleClassName);
    }
}
