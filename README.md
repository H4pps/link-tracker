# LinkTracker

LinkTracker — учебный проект из двух сервисов: `scrapper` и `bot`.

## Требования

- JDK 25
- Maven Wrapper (`./mvnw`, уже в репозитории)
- Docker
- Docker Compose (`docker compose`)

## Настройка `.env` в корне проекта

1. Создайте корневой `.env` из шаблона:

```bash
cp .env.example .env
```

2. Заполните обязательные поля:

- `TELEGRAM_TOKEN`
- `GITHUB_TOKEN`
- `STACKOVERFLOW_KEY`
- `STACKOVERFLOW_ACCESS_KEY`

3. Проверьте/при необходимости скорректируйте переменные базы данных:

- `POSTGRES_DB`
- `POSTGRES_USER`
- `POSTGRES_PASSWORD`
- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`

4. Выберите тип доступа к БД:

```dotenv
APP_DATABASE_ACCESS_TYPE=SQL
```

Допустимые значения: `SQL` или `ORM`.

5. При необходимости настройте планировщик проверки ссылок:

- `APP_SCHEDULER_LINK_PAGE_SIZE` — размер батча ссылок, допустимый диапазон `50..500`, значение по умолчанию `100`.
- `APP_SCHEDULER_WORKER_COUNT` — количество рабочих потоков, минимум `1`, значение по умолчанию приложения `1` (в `docker-compose.yml` задано `4` для демонстрации многопоточной обработки).

6. Настройте транспорт обновлений Scrapper -> Bot:

- `APP_BOT_MODE` — по умолчанию `kafka` (допустимые значения: `kafka`, `grpc`, `http`).
- Для Kafka используются:
  - `APP_KAFKA_BOOTSTRAP_SERVERS`
  - `APP_KAFKA_SCHEMA_REGISTRY_URL`
  - `APP_KAFKA_LINK_UPDATES_TOPIC`
  - `APP_KAFKA_LINK_UPDATES_DLQ_TOPIC`
  - `APP_KAFKA_CONSUMER_GROUP`
  - `APP_KAFKA_MAX_ATTEMPTS`
  - `APP_KAFKA_RETRY_BACKOFF`
  - `APP_KAFKA_OUTBOX_BATCH_SIZE`
  - `APP_KAFKA_OUTBOX_PUBLISH_INTERVAL`

Корневой `.env.example` рассчитан на запуск через Docker Compose, поэтому межсервисные адреса используют имена контейнеров: `postgres`, `scrapper`, `bot`.

## Запуск через Docker Compose

```bash
docker compose up --build
```

`docker-compose.yml` поднимает PostgreSQL, Kafka KRaft-кластер из 3 брокеров, Schema Registry, инициализацию топиков `link-updates` / `link-updates-dlq` и Kafka UI.

## Запуск вручную / из IDE

1. Поднимите инфраструктуру:

```bash
docker compose up -d postgres kafka-1 kafka-2 kafka-3 schema-registry topic-init
```

2. Для ручного запуска используйте модульные env-файлы с localhost-адресами:

```bash
cp scrapper/.env.example scrapper/.env
cp bot/.env.example bot/.env
```

3. Запустите `scrapper`:

- IDE: `backend.academy.linktracker.scrapper.ScrapperApplication`
- CLI: `./mvnw -pl scrapper spring-boot:run`

4. Запустите `bot`:

- IDE: `backend.academy.linktracker.bot.BotApplication`
- CLI: `./mvnw -pl bot spring-boot:run`

Порядок запуска обязателен: сначала PostgreSQL, затем `ScrapperApplication`, затем `BotApplication`.

Если хотите временно отключить Kafka в ручном запуске и использовать gRPC-транспорт:

- в `scrapper/.env` выставьте `APP_BOT_MODE=grpc`;
- в `bot/.env` выставьте `APP_KAFKA_ENABLED=false`.

## Финальные команды проверки (test/lint)

Lint/check:

```bash
./mvnw clean compile -am spotless:check modernizer:modernizer spotbugs:check pmd:check pmd:cpd-check
```

Тесты `bot` и `scrapper`:

```bash
./mvnw -pl bot,scrapper -am test
```

## Проверка интеграции Scraper -> Kafka -> Bot

Полный путь сообщения (Scrapper -> Transactional Outbox -> Kafka -> Bot -> Telegram) проверяет
end-to-end тест на Testcontainers. Ассистент может запустить его локально (требуется Docker):

```bash
./mvnw -pl bot -am -Dsurefire.skip=true \
  -Dit.test='TransportIntegrationE2EIT#kafkaTransportFlowDeliversNotificationFromScrapperToTelegram' verify
```

Тест поднимает Kafka, Schema Registry, контейнеры `scrapper` и `bot`, эмулирует Telegram/GitHub,
отслеживает ссылку через бота и проверяет, что обновление доходит до пользователя по Kafka-транспорту.

Дополнительные интеграционные тесты на Testcontainers Kafka (запускаются обычным `test`):

```bash
# Scrapper: outbox -> Avro-событие в Kafka, статус строки меняется только после ack
./mvnw -pl scrapper -am -Dtest=KafkaOutboxIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test

# Bot: валидное сообщение, валидация -> DLQ, ошибка десериализации -> DLQ, повторы -> DLQ
./mvnw -pl bot -am -Dtest=KafkaConsumerIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test
```

## Настройки топиков Kafka

Топики создаёт сервис `topic-init` в `docker-compose.yml`:

- `link-updates` — нотификации Scrapper -> Bot;
- `link-updates-dlq` — Dead Letter Queue для сообщений, которые не удалось обработать.

Выбранные настройки и их обоснование:

- `--partitions 3` — позволяет распараллелить обработку по ключу (ключ = URL ссылки), сохраняя
  порядок событий в рамках одной ссылки; 3 партиции соответствуют числу брокеров.
- `--replication-factor 3` — по одной реплике на каждый из трёх брокеров, кластер переживает отказ
  любого узла без потери данных.
- `min.insync.replicas=2` вместе с продьюсером `acks=all` — запись подтверждается только когда её
  приняли минимум две реплики, что исключает потерю подтверждённых сообщений при падении одного брокера
  (баланс между надёжностью и доступностью).

## Полезно

- Документация по шаблону: [HELP.md](./HELP.md)

