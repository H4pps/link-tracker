package backend.academy.linktracker.scrapper.application.integration;

import backend.academy.linktracker.scrapper.ScrapperApplication;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
        classes = ScrapperApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
            "server.port=0",
            "app.grpc.server.port=0",
            "app.scheduler.enabled=false",
            "app.scheduler.link-page-size=50",
            "app.cache.list-links.enabled=false",
            "app.database.access-type=SQL",
            "app.github.token=test-github-token",
            "app.stackoverflow.key=test-stackoverflow-key",
            "app.stackoverflow.access-token=test-stackoverflow-access-token"
        })
class SqlScrapperApplicationIntegrationTest extends AbstractScrapperApplicationIntegrationTest {

    @Override
    protected String accessType() {
        return "SQL";
    }
}
