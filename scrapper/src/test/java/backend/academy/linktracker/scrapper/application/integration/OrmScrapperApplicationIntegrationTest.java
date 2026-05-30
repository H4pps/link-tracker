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
            "app.scheduler.link-page-size=1",
            "app.database.access-type=ORM",
            "app.github.token=test-github-token",
            "app.stackoverflow.key=test-stackoverflow-key",
            "app.stackoverflow.access-token=test-stackoverflow-access-token"
        })
class OrmScrapperApplicationIntegrationTest extends AbstractScrapperApplicationIntegrationTest {

    @Override
    protected String accessType() {
        return "ORM";
    }
}
