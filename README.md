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

5. Проверьте настройки кэша Scrapper `GET /links`:

- `APP_VALKEY_CLUSTER_NODES` — ноды Valkey cluster, для Docker Compose: `valkey-node-0:7000,valkey-node-1:7001,valkey-node-2:7002`; для локального запуска из IDE: `localhost:17000,localhost:17001,localhost:17002`;
- `APP_VALKEY_TIMEOUT` — таймаут операций Redis/Lettuce, по умолчанию `2s`;
- `APP_CACHE_LIST_LINKS_ENABLED` — включает кэш unpaged `GET /links`, по умолчанию `true`;
- `APP_CACHE_LIST_LINKS_TTL` — TTL значения в Valkey, по умолчанию `10m`.

Кэшируется только unpaged список ссылок: REST `GET /links` без `limit` или с `limit=0`, и gRPC `ListLinks` с `limit=0`.
Ключом является только chat id из `Tg-Chat-Id`; paginated вызовы кэш обходят. После успешных `POST /links`,
`DELETE /links` и удаления чата ключ этого чата удаляется из кэша.

Ошибки чтения, записи и удаления из Valkey не меняют контракт API: Scrapper логирует сбой и продолжает работать через
репозиторий. Ответы с ошибками не кэшируются.

6. При необходимости настройте планировщик проверки ссылок:

- `APP_SCHEDULER_LINK_PAGE_SIZE` — размер батча ссылок, допустимый диапазон `50..500`, значение по умолчанию `100`.
- `APP_SCHEDULER_WORKER_COUNT` — количество рабочих потоков, минимум `1`, значение по умолчанию приложения `1` (в `docker-compose.yml` задано `4` для демонстрации многопоточной обработки).

7. Настройте транспорт обновлений Scrapper -> Bot:

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

Консьюмер Bot идемпотентен: каждое уведомление помечается стабильным `message-id` (UUID из outbox-строки,
передаётся в Kafka-заголовке) и хранится в таблице `processed_link_updates`, поэтому повторная доставка одного
и того же сообщения не приводит к дублю уведомления пользователю. Для этого Bot тоже подключается к PostgreSQL
(`SPRING_DATASOURCE_URL` / `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD`).

8. При необходимости настройте отказоустойчивость внешних вызовов и REST rate limiting:

- `APP_RESILIENCE_RETRY_MAX_ATTEMPTS` — количество попыток retry, по умолчанию `3`;
- `APP_RESILIENCE_RETRY_BACKOFF` — constant backoff между попытками, по умолчанию `200ms`;
- `APP_RESILIENCE_RETRY_RETRYABLE_HTTP_STATUSES` — retryable HTTP-статусы, по умолчанию `500,502,503,504`;
- `APP_RESILIENCE_CIRCUIT_BREAKER_FAILURE_RATE_THRESHOLD` — порог ошибок Circuit Breaker, по умолчанию `50`;
- `APP_RESILIENCE_CIRCUIT_BREAKER_SLIDING_WINDOW_SIZE` — размер sliding window, по умолчанию `10`;
- `APP_RESILIENCE_CIRCUIT_BREAKER_MINIMUM_NUMBER_OF_CALLS` — минимальное число вызовов для расчёта ошибок, по умолчанию `5`;
- `APP_RESILIENCE_CIRCUIT_BREAKER_PERMITTED_CALLS_IN_HALF_OPEN_STATE` — число пробных вызовов в HALF_OPEN, по умолчанию `2`;
- `APP_RESILIENCE_CIRCUIT_BREAKER_OPEN_STATE_DURATION` — время OPEN-состояния, по умолчанию `5s`;
- `APP_RESILIENCE_RATE_LIMIT_LIMIT_FOR_PERIOD` — число разрешённых запросов на IP за период, по умолчанию `60`;
- `APP_RESILIENCE_RATE_LIMIT_LIMIT_REFRESH_PERIOD` — период обновления лимита, по умолчанию `1m`;
- `APP_RESILIENCE_RATE_LIMIT_TIMEOUT_DURATION` — ожидание свободного разрешения Resilience4J RateLimiter, по умолчанию `0ms`.

Resilience4J-backed rate limiting применяется только к публичным REST endpoint'ам: Scrapper `/links`, `/tg-chat/**` и Bot `/updates`.
IP берётся из первого значения `X-Forwarded-For`, если заголовок есть, иначе из `remoteAddr`.

Корневой `.env.example` рассчитан на запуск через Docker Compose, поэтому межсервисные адреса используют имена контейнеров: `postgres`, `scrapper`, `bot`.

## Запуск через Docker Compose

```bash
docker compose up --build
```

`docker-compose.yml` поднимает PostgreSQL, Valkey cluster из 3 нод, Kafka KRaft-кластер из 3 брокеров, Schema Registry,
инициализацию топиков (`link.raw-updates`, `link.processed-updates` и их DLQ), Kafka UI, а также сервисы
`scrapper`, `bot` и `ai-agent`.

Маршрут сообщений: `Scrapper -> link.raw-updates -> AI Agent -> link.processed-updates -> Bot -> Telegram`.

## Запуск вручную / из IDE

1. Поднимите инфраструктуру:

```bash
docker compose up -d postgres kafka-1 kafka-2 kafka-3 schema-registry topic-init
```

Для Scrapper с включённым кэшем дополнительно поднимите Valkey:

```bash
docker compose up -d valkey-node-0 valkey-node-1 valkey-node-2 valkey-cluster-init
```

2. Для ручного запуска используйте модульные env-файлы с localhost-адресами:

```bash
cp scrapper/.env.example scrapper/.env
cp bot/.env.example bot/.env
cp ai-agent/.env.example ai-agent/.env
```

3. Запустите `scrapper`:

- IDE: `backend.academy.linktracker.scrapper.ScrapperApplication`
- CLI: `./mvnw -pl scrapper spring-boot:run`

4. Запустите `ai-agent`:

- IDE: `backend.academy.linktracker.ai.AiAgentApplication`
- CLI: `./mvnw -pl ai-agent spring-boot:run`

5. Запустите `bot`:

- IDE: `backend.academy.linktracker.bot.BotApplication`
- CLI: `./mvnw -pl bot spring-boot:run`

Порядок запуска обязателен: сначала PostgreSQL и Kafka, затем `ScrapperApplication`, `AiAgentApplication` и
`BotApplication`. AI Agent работает «просто из IDE» с заглушкой-суммаризацией; ключ для AI API не требуется.

Если хотите временно отключить Kafka в ручном запуске и использовать gRPC-транспорт:

- в `scrapper/.env` выставьте `APP_BOT_MODE=grpc`;
- в `bot/.env` выставьте `APP_KAFKA_ENABLED=false`.

Valkey cluster в Compose объявляет ноды как `valkey-node-0..2`, а наружу публикует порты `17000..17002`, чтобы не
конфликтовать с системными сервисами macOS на `7000`. При запуске Scrapper из IDE с `scrapper/.env.example` и
localhost-портами убедитесь, что клиент может резолвить имена `valkey-node-0..2` после cluster redirects, либо
запускайте Scrapper через Docker Compose.

Если ранее уже запускалась другая Valkey cluster topology, удалите старые Valkey volumes перед первым запуском,
иначе ноды могут подняться со старым `nodes.conf`.

```bash
docker compose rm -sf valkey-node-0 valkey-node-1 valkey-node-2 valkey-cluster-init
docker volume rm link-tracker_valkey_node_0_data link-tracker_valkey_node_1_data link-tracker_valkey_node_2_data \
  2>/dev/null || true
```

## Финальные команды проверки (test/lint)

Lint/check:

```bash
./mvnw clean compile -am spotless:check modernizer:modernizer spotbugs:check pmd:check pmd:cpd-check
```

Быстрые тесты `bot` и `scrapper` без Testcontainers-интеграций:

```bash
./mvnw -pl bot,scrapper -am test
```

## Проверка интеграции Scraper -> Kafka -> Bot

Полный путь сообщения (Scrapper -> Transactional Outbox -> Kafka -> Bot -> Telegram) проверяет
end-to-end тест на Testcontainers. Ассистент может запустить его локально (требуется Docker):

```bash
./mvnw -pl bot -am -Dsurefire.skip=true -DskipITs=false \
  -Dit.test='TransportIntegrationE2EIT#kafkaTransportFlowDeliversNotificationFromScrapperToTelegram' verify
```

Тест поднимает Kafka, Schema Registry, контейнеры `scrapper` и `bot`, эмулирует Telegram/GitHub,
отслеживает ссылку через бота и проверяет, что обновление доходит до пользователя по Kafka-транспорту.

Дополнительные интеграционные тесты на Testcontainers Kafka запускаются через Failsafe:

```bash
# Scrapper: outbox -> Avro-событие в Kafka, статус строки меняется только после ack
./mvnw -pl scrapper -am -Dsurefire.skip=true -DskipITs=false -Dit.test=KafkaOutboxIntegrationTest verify

# Bot: валидное сообщение, валидация -> DLQ, ошибка десериализации -> DLQ, повторы -> DLQ
./mvnw -pl bot -am -Dsurefire.skip=true -DskipITs=false -Dit.test=KafkaConsumerIntegrationTest verify
```

## Настройки топиков Kafka

Топики создаёт сервис `topic-init` в `docker-compose.yml`:

- `link.raw-updates` — сырые обновления Scrapper -> AI Agent (поле `author` добавлено);
- `link.raw-updates-dlq` — DLQ AI Agent для сообщений, которые не удалось десериализовать;
- `link.processed-updates` — обработанные (отфильтрованные/суммаризированные) обновления AI Agent -> Bot;
- `link.processed-updates-dlq` — DLQ Bot.

Выбранные настройки и их обоснование:

- `--partitions 3` — позволяет распараллелить обработку по ключу (ключ = URL ссылки), сохраняя
  порядок событий в рамках одной ссылки; 3 партиции соответствуют числу брокеров.
- `--replication-factor 3` — по одной реплике на каждый из трёх брокеров, кластер переживает отказ
  любого узла без потери данных.
- `min.insync.replicas=2` вместе с продьюсером `acks=all` — запись подтверждается только когда её
  приняли минимум две реплики, что исключает потерю подтверждённых сообщений при падении одного брокера
  (баланс между надёжностью и доступностью).

## AI Agent Service (фильтрация и суммаризация)

AI Agent читает `link.raw-updates`, применяет фильтрацию и суммаризацию и публикует результат в
`link.processed-updates`.

- **Фильтрация** (`ai-agent.filtering`): по стоп-словам (`stop-words`), исключённым авторам
  (`excluded-authors`) и минимальной длине (`min-length`). Отфильтрованные обновления не публикуются.
- **Суммаризация** (`ai-agent.summarization`): если длина текста превышает `threshold`, текст сокращается.
  Реализация выбирается свойством `ai-agent.summarization.mode`:
  - `stub` (по умолчанию) — обрезка до `threshold` символов + `...`, ключ не нужен;
  - `ai` — Spring AI `ChatClient` (OpenAI-совместимый эндпоинт, подходит для YandexGPT / HuggingFace /
    локальной модели). Ключ задаётся через `APP_AI_OPENAI_API_KEY` и хранится только в `.env`.
- **Устойчивость** (FR/NFR): некорректные сообщения уходят в `link.raw-updates-dlq` и не роняют сервис.

Интеграционный тест на Testcontainers Kafka (получение валидного сообщения, фильтрация, суммаризация и
обработка некорректного сообщения):

```bash
./mvnw -pl ai-agent -am -Dsurefire.skip=true -DskipITs=false -Dit.test=AiAgentKafkaIntegrationTest verify
```

## Полезно

- Документация по шаблону: [HELP.md](./HELP.md)

