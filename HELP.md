# Getting Started

## Project Structure

This is a multi-module Java project built with [Apache Maven](https://maven.apache.org/).

Modules:

- [bot/](./bot): Telegram bot service that interacts with users through the [Telegram Bot API](https://core.telegram.org/bots/api).
- [scrapper/](./scrapper): content monitoring service that tracks link changes and sends updates.
- [ai-agent/](./ai-agent): service for filtering and summarizing update data.
- [messaging/](./messaging): shared Avro message contracts.
- [build-report-aggregate/](./build-report-aggregate): utility module for aggregated build reports.

Each module follows the standard Maven layout:

- `pom.xml`: module build descriptor.
- `src/main`: application source code.
- `src/test`: test source code.

Shared repository files:

- [pom.xml](./pom.xml): parent POM with shared dependencies, plugins, and settings.
- [mvnw](./mvnw) and [mvnw.cmd](./mvnw.cmd): Maven Wrapper scripts for Unix and Windows.
- [pmd.xml](pmd.xml) and [spotbugs-excludes.xml](spotbugs-excludes.xml): static analysis configuration.
- [.mvn/](./.mvn): Maven wrapper and build configuration.
- [lombok.config](lombok.config): Lombok configuration.
- [.editorconfig](.editorconfig): formatting settings.
- [.gitlab-ci.yml](.gitlab-ci.yml): GitLab CI build definition.
- [.gitattributes](.gitattributes) and [.gitignore](.gitignore): Git metadata and ignored files.

## Build

From IntelliJ IDEA, use [Run Anything](https://www.jetbrains.com/help/idea/running-anything.html) with:

```shell
mvn clean verify
```

From a terminal:

```shell
./mvnw clean verify
```

On Windows:

```shell
mvnw.cmd clean verify
```

The build downloads dependencies, compiles modules, and runs the configured tests.

If the build fails with:

```shell
Rule 0: org.apache.maven.enforcer.rules.version.RequireJavaVersion failed with message:
JDK version must be at least 25
```

the active JDK is older than 25.

If the build fails with:

```shell
Rule 1: org.apache.maven.enforcer.rules.version.RequireMavenVersion failed with message:
Maven version should, at least, be 3.9.12
```

the active Maven version is older than 3.9.12. This should not happen when using IntelliJ IDEA or the Maven Wrapper.

Useful Maven commands:

```shell
mvn spotless:apply
```

Formats code.

```shell
mvn compile
```

Compiles main classes.

```shell
mvn test
```

Runs tests.

```shell
mvn clean compile -am spotless:check modernizer:modernizer spotbugs:check pmd:check pmd:cpd-check
```

Runs formatting, modernization, SpotBugs, PMD, and CPD checks.

```shell
mvn dependency:tree
```

Prints the dependency tree. This is useful for debugging transitive dependencies.

```shell
mvn help:describe -Dplugin=compiler
```

Prints help for a Maven plugin. Replace `compiler` with another plugin name when needed.

## Configuration

Tokens and other secrets must not be stored in source code. Keep them in `.env` files in each module root.

Example `bot/.env` value:

```properties
APP_TELEGRAM_TOKEN=123456:ABC-DEF1234ghIkl-zyx57W2v1u123ew11
```

Spring Boot reads environment variables automatically. `APP_TELEGRAM_TOKEN` maps to `app.telegram.token` in `application.yaml`.

Make sure `.env` files are listed in `.gitignore` and are not committed.

## Dependencies

Prefer dependencies already declared in the parent [pom.xml](./pom.xml) and the existing Spring ecosystem dependencies used by the project. Add new third-party dependencies only when they are required by the implementation and fit the existing architecture.

## External APIs

The project uses these external APIs:

- [Telegram Bot API](https://core.telegram.org/bots/api): user interaction through Telegram. The project uses [java-telegram-bot-api](https://github.com/pengrad/java-telegram-bot-api).
- [GitHub REST API](https://docs.github.com/en/rest): repository, commit, issue, and pull request data.
- [StackOverflow API](https://api.stackexchange.com/docs): question and answer data.

The Bot and Scrapper service contract is also implemented through shared gRPC protobuf definitions in `bot/src/main/protobuf` and `scrapper/src/main/protobuf`.

## Spring Reference

Useful reference links:

- [Official Apache Maven documentation](https://maven.apache.org/guides/index.html)
- [Spring Boot Maven Plugin Reference Guide](https://docs.spring.io/spring-boot/4.0.2/maven-plugin)
- [Spring Configuration Processor](https://docs.spring.io/spring-boot/4.0.2/specification/configuration-metadata/annotation-processor.html)
- [Validation](https://docs.spring.io/spring-boot/4.0.2/reference/io/validation.html)
- [Spring Web](https://docs.spring.io/spring-boot/4.0.2/reference/web/servlet.html)
- [HTTP Client](https://docs.spring.io/spring-boot/4.0.2/reference/io/rest-client.html#io.rest-client.restclient)
- [Spring Data JDBC](https://docs.spring.io/spring-boot/4.0.2/reference/data/sql.html#data.sql.jdbc)
- [Spring Data JPA](https://docs.spring.io/spring-boot/4.0.2/reference/data/sql.html#data.sql.jpa-and-spring-data)
- [Flyway Migration](https://docs.spring.io/spring-boot/4.0.2/how-to/data-initialization.html#howto.data-initialization.migration-tool.flyway)
- [Liquibase Migration](https://docs.spring.io/spring-boot/4.0.2/how-to/data-initialization.html#howto.data-initialization.migration-tool.liquibase)
- [Spring Data Redis](https://docs.spring.io/spring-boot/4.0.2/reference/data/nosql.html#data.nosql.redis)
- [Spring Boot Actuator](https://docs.spring.io/spring-boot/4.0.2/reference/actuator/index.html)
- [Task Execution and Scheduling](https://docs.spring.io/spring-boot/4.0.2/reference/features/task-execution-and-scheduling.html)
- [Spring for Apache Kafka](https://docs.spring.io/spring-boot/4.0.2/reference/messaging/kafka.html)
- [Spring for GraphQL](https://docs.spring.io/spring-boot/4.0.2/reference/web/spring-graphql.html)
- [Spring gRPC](https://docs.spring.io/spring-grpc/reference/index.html)
- [Resilience4J](https://docs.spring.io/spring-cloud-circuitbreaker/reference/spring-cloud-circuitbreaker-resilience4j.html)
- [Spring AI Models](https://docs.spring.io/spring-ai/reference/api/index.html)
- [Docker Compose Support](https://docs.spring.io/spring-boot/4.0.2/reference/features/dev-services.html#features.dev-services.docker-compose)
- [Create an OCI image](https://docs.spring.io/spring-boot/4.0.2/maven-plugin/build-image.html)
- [Testcontainers](https://java.testcontainers.org/)
- [Spring Boot Testcontainers support](https://docs.spring.io/spring-boot/4.0.2/reference/testing/testcontainers.html#testing.testcontainers)
- [Testcontainers Postgres Module Reference Guide](https://java.testcontainers.org/modules/databases/postgres/)
- [Testcontainers Kafka Modules Reference Guide](https://java.testcontainers.org/modules/kafka/)
- [Testcontainers Grafana Module Reference Guide](https://java.testcontainers.org/modules/grafana/)
- [OpenTelemetry](https://docs.spring.io/spring-boot/4.0.2/reference/actuator/observability.html#actuator.observability.opentelemetry)

## Guides

Useful Spring guides:

- [Serving Web Content with Spring MVC](https://spring.io/guides/gs/serving-web-content/)
- [Validation](https://spring.io/guides/gs/validating-form-input/)
- [Building a RESTful Web Service](https://spring.io/guides/gs/rest-service/)
- [Building REST services with Spring](https://spring.io/guides/tutorials/rest/)
- [Building a RESTful Web Service with Spring Boot Actuator](https://spring.io/guides/gs/actuator-service/)
- [Using Spring Data JDBC](https://github.com/spring-projects/spring-data-examples/tree/main/jdbc/basics)
- [Accessing Data with JPA](https://spring.io/guides/gs/accessing-data-jpa/)
- [Messaging with Redis](https://spring.io/guides/gs/messaging-redis/)
- [Building a GraphQL service](https://spring.io/guides/gs/graphql-server/)
- [Spring gRPC sample apps](https://github.com/spring-projects/spring-grpc/tree/main/samples)

## Testcontainers

The project uses [Testcontainers during development](https://docs.spring.io/spring-boot/4.0.2/reference/features/dev-services.html#features.dev-services.testcontainers).

Configured Docker images:

- [`grafana/otel-lgtm:latest`](https://hub.docker.com/r/grafana/otel-lgtm)
- [`apache/kafka-native:4.1.1`](https://hub.docker.com/r/apache/kafka-native)
- [`postgres:18-alpine`](https://hub.docker.com/_/postgres)
- [`redis:8.2-alpine`](https://hub.docker.com/_/redis)

Check image tags before production use and keep them aligned with deployment configuration.

## Maven Parent Overrides

Some Maven parent elements are inherited by child POMs. The root POM intentionally contains empty overrides for optional metadata such as `<license>` and `<developers>`.
If you switch to another parent POM and want to inherit those elements, remove the empty overrides.
