# AI Agent

Монорепозиторий по `PLAN.md`: backend на Java 21 + Vert.x, PostgreSQL + Liquibase, REST + WebSocket, mock AI execution и frontend на React + TypeScript.

Проект реализует управление асинхронными AI-задачами, а не диалоговый чат-бот. Frontend использует chat-like раскладку только как удобный способ показать prompt пользователя, прогресс задачи и результат; backend не хранит conversation context и не объединяет prompt в диалоговую историю.

## Архитектура

Backend разделён по гексагональной архитектуре:

- `domain` — модель `Task`, статусы, инварианты terminal state и progress `0..100`;
- `application` — use cases и порты `TaskRepository`, `AiClient`, `TaskEventPublisher`;
- `infrastructure` — PostgreSQL adapter на `PgPool`, Liquibase, mock AI;
- `web` — REST, WebSocket, DTO, централизованные ошибки.

Зависимости направлены внутрь: `domain <- application <- adapters`.

## Переменные окружения

- `HTTP_PORT`, default `8080`
- `DATABASE_HOST`, `DATABASE_PORT`, `DATABASE_NAME`
- `DATABASE_USER`, `DATABASE_PASSWORD`
- `DATABASE_JDBC_URL` для Liquibase
- `DATABASE_POOL_SIZE`
- `AI_STEP_DELAY_MS`, `AI_TIMEOUT_MS`
- `WEBSOCKET_MAX_MESSAGE_SIZE_BYTES`, default `8192`. Ограничивает входящие WebSocket-сообщения `client -> WebSocket server` в байтах; это не лимит AI prompt.

Безопасный шаблон локальной конфигурации находится в `.env.example`.

## Локальный backend

Нужен PostgreSQL с БД `ai_agent` и пользователем `ai_agent`.

```bash
./gradlew test
./gradlew shadowJar
./gradlew run
```

Liquibase применяется до создания `PgPool` и старта HTTP server.

## Frontend

Frontend находится в `frontend` и реализован на React + TypeScript + Vite.

Требования:

- Node.js 20.19+;
- npm 10+;
- запущенный backend на `http://localhost:8080` для реальной REST/WebSocket-интеграции.

Установка и проверки:

```bash
cd frontend
npm ci
npm test
npm run build
```

Локальный запуск:

```bash
cd frontend
npm run dev
```

По умолчанию Vite запускается на `http://localhost:5173` и проксирует:

- `/api` -> `http://localhost:8080`;
- `/ws` -> `ws://localhost:8080`.

Переменные окружения frontend задаются через `.env` по примеру `frontend/.env.example`:

```bash
VITE_BACKEND_URL=http://localhost:8080
VITE_WS_URL=ws://localhost:8080/ws/tasks
VITE_API_TIMEOUT_MS=15000
VITE_PROMPT_MAX_LENGTH=4000
```

`VITE_BACKEND_URL` можно оставить пустым при запуске через Vite proxy. `VITE_WS_URL` не должен хардкодить production host.

Запуск frontend вместе с backend локально:

1. запустить PostgreSQL;
2. запустить backend: `./gradlew run`;
3. запустить frontend: `cd frontend && npm run dev`;
4. открыть `http://localhost:5173`.

Frontend генерирует `X-Client-Id` при первом открытии, сохраняет его в `localStorage` и использует один идентификатор для REST и WebSocket.

## Быстрый запуск через Docker Compose

```bash
docker compose up --build
```

После успешного запуска приложение доступно в браузере:

- frontend: `http://localhost:5173`;
- backend healthcheck: `http://localhost:8080/health`;
- backend API: `http://localhost:8080/api/v1`.

Сервисы:

- `postgres` с volume `postgres-data`;
- `backend` non-root, healthcheck `/health`;
- `frontend` на nginx.

## REST примеры

Хелсчек проверки бэкенда
```bash
curl http://localhost:8080/health
```

```bash
curl -X POST http://localhost:8080/api/v1/tasks \
  -H "Content-Type: application/json" \
  -H "X-Client-Id: client-123" \
  -d '{"prompt":"Проанализируй текст"}'
```

```bash
curl -H "X-Client-Id: client-123" http://localhost:8080/api/v1/tasks
```

```bash
curl -X POST -H "X-Client-Id: client-123" http://localhost:8080/api/v1/tasks/{taskId}/cancel
```

Формальный REST-контракт находится в `docs/openapi.yaml` (OpenAPI 3.0.3). Его можно импортировать в Swagger Editor, Postman или генератор API-клиента.

## CI

GitHub Actions workflow `.github/workflows/ci.yml` запускается для pull request, push в `main` и вручную. Backend job выполняет `./gradlew check` на Java 21, frontend job выполняет `npm ci`, `npm test` и `npm run build` на Node.js 20.19.

## WebSocket

Endpoint: `ws://localhost:8080/ws/tasks`, handshake header `X-Client-Id`.

Входящие управляющие сообщения WebSocket ограничены `WEBSOCKET_MAX_MESSAGE_SIZE_BYTES` bytes, default `8192`.
Лимит применяется к сообщениям от клиента к WebSocket server и не меняет REST/frontend prompt limit.

```json
{"action":"SUBSCRIBE","taskId":"uuid"}
```

События: `TASK_CREATED`, `TASK_STARTED`, `TASK_PROGRESS`, `TASK_COMPLETED`, `TASK_FAILED`, `TASK_CANCELLED`, `SUBSCRIBED`, `UNSUBSCRIBED`, `ERROR`, `PONG`.

## Сквозной сценарий

1. Создать задачу через REST.
2. Подключиться к `/ws/tasks` с тем же `X-Client-Id`.
3. Отправить `SUBSCRIBE`.
4. Получать progress и terminal event.
5. Проверить состояние через `GET /api/v1/tasks/{id}`.
6. Проверить, что другой `X-Client-Id` не читает чужую задачу.

## Ограничения

- Mock AI не является реальной AI-интеграцией.
- Prompt со словами `fail`/`error` завершится `FAILED`, `timeout` — `TIMED_OUT`.
- WebSocket subscriptions in-memory и не разделяются между несколькими backend instances.
- PostgreSQL в compose предназначен для локальной проверки.

## AI-assisted development

Оригинальные prompts сохраняются в `prompt.md` последовательно. Git commit выполняется вручную пользователем.
