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

Корневой `.env.example` рассчитан на запуск через Docker Compose, поэтому межсервисные адреса используют имена контейнеров: `postgres`, `scrapper`, `bot`.

## Запуск через Docker Compose

```bash
docker compose up --build
```

## Запуск вручную / из IDE

1. Поднимите PostgreSQL:

```bash
docker compose up -d postgres
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

## Финальные команды проверки (test/lint)

Lint/check:

```bash
./mvnw clean compile -am spotless:check modernizer:modernizer spotbugs:check pmd:check pmd:cpd-check
```

Тесты `bot` и `scrapper`:

```bash
./mvnw -pl bot,scrapper -am test
```

## Полезно

- Документация по шаблону: [HELP.md](./HELP.md)

