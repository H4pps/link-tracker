package com.linktracker.scrapper.infrastructure.memory.tag;

import com.linktracker.scrapper.ScrapperApplication;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
        classes = ScrapperApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
            "server.port=0",
            "app.grpc.server.port=0",
            "app.scheduler.enabled=false",
            "app.database.access-type=ORM",
            "app.github.token=test-github-token",
            "app.stackoverflow.key=test-stackoverflow-key",
            "app.stackoverflow.access-token=test-stackoverflow-access-token"
        })
class OrmTagRepositoryIntegrationTest extends AbstractTagRepositoryIntegrationTest {}
