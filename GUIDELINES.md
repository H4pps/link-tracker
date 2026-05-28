# Инструкция по запуску приложения

## Требования

- Docker + Docker Compose v2 (`docker compose`)
- или JDK 25 для запуска через Maven

## Запуск через Docker (рекомендуется)

Из корня репозитория:

```bash
docker compose up --build -d
```

Логи:

```bash
docker compose logs -f
```

Остановка:

```bash
docker compose down
```

Порты сервисов:

- `bot`: `8080`
- `scrapper`: `8081`

## Запуск через Maven

1. Запустите `scrapper`:

```bash
./mvnw -pl scrapper -am spring-boot:run
```

2. В отдельном терминале запустите `bot` с отключенным polling:

```bash
TELEGRAM_TOKEN=dummy \
APP_TELEGRAM_POLLING_ENABLED=false \
APP_TELEGRAM_URL=http://127.0.0.1:65535/bot \
./mvnw -pl bot -am spring-boot:run
```

## Проверка интеграционных тестов

Интеграционные (container E2E) тесты запускаются отдельным профилем:

```bash
./mvnw -pl bot,scrapper -am -Pe2e verify
```

Примечание: для этого запуска должен быть доступен Docker daemon.
