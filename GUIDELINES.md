# Application Run Guide

## Requirements

- Docker with Docker Compose v2 (`docker compose`)
- JDK 25 for Maven-based runs

## Run With Docker

From the repository root:

```bash
docker compose up --build -d
```

Show logs:

```bash
docker compose logs -f
```

Stop services:

```bash
docker compose down
```

Service ports:

- `bot`: `8080`
- `scrapper`: `8081`

## Run With Maven

1. Start `scrapper`:

```bash
./mvnw -pl scrapper -am spring-boot:run
```

2. In a separate terminal, start `bot` with polling disabled:

```bash
TELEGRAM_TOKEN=dummy \
APP_TELEGRAM_POLLING_ENABLED=false \
APP_TELEGRAM_URL=http://127.0.0.1:65535/bot \
./mvnw -pl bot -am spring-boot:run
```

## Integration Tests

Container-based end-to-end integration tests run through a separate profile:

```bash
./mvnw -pl bot,scrapper -am -Pe2e verify
```

Docker daemon must be available for this command.
