# LinkTracker

LinkTracker — учебный проект Telegram-бота и связанных сервисов.

## Быстрый запуск bot-сервиса

### 1. Требования

- JDK 25
- Maven Wrapper (`./mvnw`, уже в репозитории)

### 2. Настройка токена Telegram

Создайте файл:

- `bot/.env`

Содержимое:

```dotenv
TELEGRAM_TOKEN=<ваш_telegram_bot_token>
```

Пример шаблона:

- `bot/.env.example`

Токен читается из:

- `bot/src/main/resources/application.yaml`
- `spring.config.import=optional:file:.env[.properties],optional:file:bot/.env[.properties]`

### 3. Запуск

Из корня проекта:

```bash
./mvnw -pl bot spring-boot:run
```

Или из IntelliJ:

1. Откройте модуль `bot`
2. Запустите `backend.academy.linktracker.bot.BotApplication`

По умолчанию HTTP-порт приложения: `8080`.

### 4. Проверка бота в Telegram

Отправьте боту команды:

- `/start`
- `/help`
- любую неизвестную команду (например `/unknown`)

Ожидаемое поведение:

- `/start` -> приветствие
- `/help` -> список доступных команд
- неизвестная команда -> сообщение об ошибке

## Тесты

Тесты bot-модуля:

```bash
./mvnw -pl bot -am test
```

Все тесты проекта:

```bash
./mvnw test
```

## Полезно

- Документация по шаблону: [HELP.md](./HELP.md)
