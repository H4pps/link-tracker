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

- `APP_VALKEY_CLUSTER_NODES` — ноды Valkey cluster, для Docker Compose: `valkey-node-0:7000,valkey-node-1:7001,valkey-node-2:7002,valkey-node-3:7003,valkey-node-4:7004,valkey-node-5:7005`; для локального запуска из IDE: `localhost:17000,localhost:17001,localhost:17002,localhost:17003,localhost:17004,localhost:17005`;
- `APP_VALKEY_TIMEOUT` — таймаут операций Redis/Lettuce, по умолчанию `2s`;
- `APP_CACHE_LIST_LINKS_ENABLED` — включает кэш unpaged `GET /links`, по умолчанию `true`;
- `APP_CACHE_LIST_LINKS_TTL` — TTL значения в Valkey, по умолчанию `10m`;
- `APP_CACHE_CLIENT_SIDE_ENABLED` — флаг бонусной настройки client-side caching.

Кэшируется только unpaged список ссылок: REST `GET /links` без `limit` или с `limit=0`, и gRPC `ListLinks` с `limit=0`.
Ключом является только chat id из `Tg-Chat-Id`; paginated вызовы кэш обходят. После успешных `POST /links`,
`DELETE /links` и удаления чата ключ этого чата удаляется из кэша.

Ошибки чтения, записи и удаления из Valkey не меняют контракт API: Scrapper логирует сбой и продолжает работать через
репозиторий. Ответы с ошибками не кэшируются.

Client-side caching в Lettuce 6.8 доступен как низкоуровневый API `ClientSideCaching`, но Spring Data Redis 4.0.2 не
подключает этот локальный кэш к `StringRedisTemplate`/`RedisTemplate` без отдельного обходного подключения. В этом
проекте бонусный режим не включён в коде, чтобы не смешивать два пути доступа к Valkey; флаг оставлен в конфигурации и
зафиксирован как ограничение.

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

Корневой `.env.example` рассчитан на запуск через Docker Compose, поэтому межсервисные адреса используют имена контейнеров: `postgres`, `scrapper`, `bot`.

## Запуск через Docker Compose

```bash
docker compose up --build
```

`docker-compose.yml` поднимает PostgreSQL, Valkey cluster из 3 master-нод и 3 replica-нод, Kafka KRaft-кластер из 3 брокеров, Schema Registry,
инициализацию топиков `link-updates` / `link-updates-dlq` и Kafka UI.

## Запуск вручную / из IDE

1. Поднимите инфраструктуру:

```bash
docker compose up -d postgres kafka-1 kafka-2 kafka-3 schema-registry topic-init
```

Для Scrapper с включённым кэшем дополнительно поднимите Valkey:

```bash
docker compose up -d valkey-node-0 valkey-node-1 valkey-node-2 valkey-node-3 valkey-node-4 valkey-node-5 valkey-cluster-init
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

Valkey cluster в Compose объявляет ноды как `valkey-node-0..5`, а наружу публикует порты `17000..17005`, чтобы не
конфликтовать с системными сервисами macOS на `7000`. При запуске Scrapper из IDE с `scrapper/.env.example` и
localhost-портами убедитесь, что клиент может резолвить имена `valkey-node-0..5` после cluster redirects, либо
запускайте Scrapper через Docker Compose.

Если ранее уже запускался старый 3-node Valkey cluster, удалите старые Valkey volumes перед первым запуском новой
HA-топологии, иначе ноды могут подняться со старым `nodes.conf`.

```bash
docker compose rm -sf valkey-node-0 valkey-node-1 valkey-node-2 valkey-node-3 valkey-node-4 valkey-node-5 valkey-cluster-init
docker volume rm link-tracker_valkey_node_0_data link-tracker_valkey_node_1_data link-tracker_valkey_node_2_data \
  link-tracker_valkey_node_3_data link-tracker_valkey_node_4_data link-tracker_valkey_node_5_data 2>/dev/null || true
```

## Нагрузочный тест кэша

Артефакты для ДЗ 6 лежат в `load-tests/caching`, отчет по нагрузочному тесту — в `LOAD_TEST_REPORT.md`.

1. Поднимите инфраструктуру и Scrapper.
2. Засейте около 100k ссылок:

```bash
SCRAPPER_BASE_URL=http://localhost:8081 CHAT_COUNT=1000 LINKS_PER_CHAT=100 \
  bash load-tests/caching/seed-links.sh
```

3. Запустите Apache JMeter CLI с соотношением чтений к мутациям 100:1:

```bash
jmeter -n \
  -t load-tests/caching/list-links-cache.jmx \
  -JbaseUrl=http://localhost:8081 \
  -JchatStart=1 \
  -JchatCount=1000 \
  -JlinksPerChat=100 \
  -Jthreads=8 \
  -JrampUp=60 \
  -Jduration=300 \
  -JmutationRatio=101 \
  -l load-tests/caching/results/list-links-cache.jtl \
  -e -o load-tests/caching/results/html
```

Сравните режимы `APP_CACHE_LIST_LINKS_ENABLED=false`, `true`, и client-side-cache режим, если он будет реализован
отдельным клиентским путём.

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

