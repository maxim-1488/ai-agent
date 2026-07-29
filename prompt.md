## Prompt 0
Изучи файл с тестовым задание и составь задачу на реалзицаию проекта Агента ИИ Java 21 + Vert.x 

## Prompt 1
Изучи файл task.md в корне проекта.
Составь план реализации проекта строго на его основе.
Важно:
•
не начинай реализацию;
•
не создавай и не изменяй исходный код;
•
проверь требования task.md на противоречия;
•
разбей реализацию на небольшие последовательные этапы;
•
каждый этап должен оставлять проект в собираемом или проверяемом состоянии;
•
явно укажи зависимости между этапами;
•
отдельно выдели backend, PostgreSQL/Liquibase, REST, выполнение AI-задачи,
WebSocket, error handling, frontend, тестирование, Docker и документацию и readme по локальному запуску проекта;
•
учитывай гексагональную архитектуру;
•
Spring Boot и Spring не использовать;
•
backend — Java 21 + Vert.x;
•
frontend — React + TypeScript;
•
все публичные backend-классы, интерфейсы и нетривиальные публичные методы
должны иметь Javadoc на русском языке.
•
каждый оригинальный промт нужно добавить последовательно в файл prompt.md
После составления плана остановись и ничего не реализуй.

## Prompt 2
Реализуй проект полностью в соответствии с составленным планом PLAN.md.
Не отклоняйся от плана и реализуй все пункты плана по порядку
Не упрощай требования PLAN.md и не откладывай обязательные части на будущие этапы.

После реализации:

1. собери backend;
2. запусти backend tests;
3. собери frontend;
4. запусти frontend tests;
5. проверь docker compose;
6. исправь ошибки, которые мешают сборке или запуску;
7. проверь соответствие реализации PLAN.md;
8. перечисли выполненные проверки.

Не проводи дополнительный архитектурный рефакторинг и code review после получения работающей версии — это будет отдельный следующий этап.

Не выполняй git commit самостоятельно.

## Prompt 3
Изучи текущую реализацию backend проекта и требования `task.md` и PLAN.md.

Необходимо провести аудит существующего логирования и при необходимости добавить полноценное, но умеренное логирование backend.

Используй SLF4J API и Logback как реализацию логирования. Не добавляй логирование механически во все методы. Логирование должно помогать понимать жизненный цикл приложения, выполнение AI-задач, ошибки, проблемные запросы, WebSocket-соединения, PostgreSQL и graceful shutdown, но не создавать excessive log noise.

Сначала проведи аудит:

1. используется ли сейчас SLF4J;
2. присутствует ли `logback.xml`;
3. какие классы уже используют логирование;
4. где сейчас ошибки теряются или остаются без логирования;
5. где логирование действительно необходимо;
6. где добавление логирования будет избыточным;
7. реализован ли correlation ID;
8. попадает ли correlation ID в логи;
9. не логируются ли чувствительные или слишком большие данные.

Используй стандартный SLF4J `LoggerFactory.getLogger(CurrentClass.class)`. Не добавляй Lombok и отдельный logging framework.

Уровни: INFO только для значимых событий lifecycle и бизнес-процесса; WARN для неф JSON / unknown WebSocket action / optimistic conflicts / повторной отмены / таймаутов / backpressure; ERROR для неожиданных ошибок миграций, startup, PostgreSQL, AI client, graceful shutdown и async chain; DEBUG для технических деталей use cases, repository operations, progress и WebSocket subscribe/unsubscribe/cleanup.

Startup должен логировать загрузку конфигурации, Liquibase, migrations, PostgreSQL pool, HTTP server, фактический порт и готовность. Shutdown должен логировать начало graceful shutdown, остановку HTTP/WebSocket/AI timers, закрытие PgPool и завершение.

AI task lifecycle должен позволять восстановить последовательность событий: created, started, completed, cancelled, failed. Включать `taskId`, `clientId`, `status`, `correlationId`, если это безопасно и оправданно. Не логировать полный prompt и полный AI result; допускается prompt length.

Progress не логировать на INFO. REST не логировать каждый request на INFO. Correlation ID должен приниматься из текущего механизма или генерироваться, попадать в error response и соответствующие логи. Перед использованием MDC оценить Vert.x: ThreadLocal-контекст нельзя считать автоматически безопасным для всей async chain; не добавлять наивную MDC-реализацию, которая может смешивать IDs.

PostgreSQL: не логировать каждый SQL на INFO и не логировать SQL parameters с пользовательскими данными. DEBUG — repository operation, taskId, affected rows; WARN — optimistic update 0 rows / version conflict / invalid transition; ERROR — unexpected SQL/database error, если не залогировано выше.

WebSocket: DEBUG для established/subscribe/unsubscribe/cleanup/normal close; WARN для malformed message, unknown action, попытки подписки на чужую задачу, slow client, write queue overflow, unexpected close. Не логировать каждое outgoing `TASK_PROGRESS` на INFO.

Mock AI: INFO для начала и terminal state completed/failed/cancelled/timeout; DEBUG для внутренних шагов при необходимости. Не логировать каждое timer срабатывание на INFO.

Error handling: избежать дублирования одного exception на Repository/Service/UseCase/Controller/GlobalErrorHandler. Ожидаемые бизнес-ошибки не логировать как ERROR.

Настрой `logback.xml`, если текущая конфигурация недостаточна. Console appender, формат с timestamp, level, thread/event-loop, logger, correlationId, message. Не добавлять JSON/ELK/extra appenders.

Запрещено логировать `DATABASE_PASSWORD`, credentials, secrets, полный prompt, полный AI result, stack trace в HTTP response, SQL parameters с пользовательским текстом, environment variables целиком.

Не создавать `LoggingService`. Domain не должен зависеть от SLF4J. Основные точки логирования — application, infrastructure, web, bootstrap/configuration.

Тестирование: не добавлять unit tests на текст log message. Проверить correlation ID в error response, request lifecycle, async behavior, centralized error handler, startup/shutdown.

Регрессия: не менять REST contracts, WebSocket protocol, PostgreSQL schema, бизнес-логику, state machine, frontend, Docker API и существующее поведение приложения. Основная задача — observability.

Перед внесением изменений добавь этот оригинальный промт следующей записью в `prompt.md`. Не изменяй предыдущие промты.

После изменений выполни backend build, unit tests, integration tests, startup приложения, successful lifecycle, FAILED, CANCELLED, timeout, correlation ID, graceful shutdown и ручной просмотр логов. Не выполняй Git commit самостоятельно.

В конце покажи: какое логирование было до изменений; найденные проблемы; изменённые классы; где добавлено INFO/WARN/ERROR/DEBUG; как реализован correlation ID; изменялся ли `logback.xml`; как предотвращено избыточное логирование; результаты тестов и сборки; примеры типичных логов без чувствительных данных.

## Prompt 4
Проведи точечный рефакторинг `ExecuteTaskUseCase`, прежде всего метода `executeAsync(Task task)`, без несвязанного рефакторинга проекта.

Сохрани гексагональную архитектуру, публичный контракт класса без необходимости не меняй, поведение не ломай и не противоречь `task.md` и `PLAN.md`. `ExecuteTaskUseCase` остаётся application/use case слоем и работает через Vert.x `Future`.

Основные цели:

1. Разделить перегруженный `executeAsync()` на небольшие приватные методы с явными ответственностями: регистрация выполнения, перевод в `IN_PROGRESS`, публикация старта, запуск `AiClient`, progress callback, обновление progress, terminal update, обработка ошибок, cancellation и cleanup.
2. Сохранить optimistic locking через `task.version()` при `markInProgress()` и не запускать `AiClient`, если старт задачи не подтверждён.
3. Убрать строковые литералы событий из бизнес-потока. Предпочтительно ввести `TaskEventType` или централизованное место для event names.
4. Исправить terminal events: `COMPLETED -> TASK_COMPLETED`, `FAILED -> TASK_FAILED`, `TIMED_OUT -> TASK_TIMED_OUT`. `TASK_CANCELLED` добавлять только если текущая модель реально поддерживает отдельный terminal status `CANCELLED`.
5. Не определять timeout через `error.getClass().getSimpleName().contains("Timeout")`. Если подходящего исключения нет, добавить минимальное application/domain-level исключение, например `AiTimeoutException`, чтобы application слой не зависел от конкретной реализации AI-клиента.
6. Проверить cancellation: задача не должна ошибочно оставаться `IN_PROGRESS`, cancellation не должна превращаться в `FAILED`, `registry.unregister()` должен выполняться всегда, ложный `TASK_FAILED` публиковаться не должен. Если cancellation уже завершается другим use case, не дублировать эту ответственность и явно сохранить текущую модель.
7. Логирование оставить умеренным: INFO для старта и успешного завершения, WARN для timeout/cancellation/optimistic conflict, ERROR для failure и неожиданного сбоя async chain, DEBUG для progress. Не логировать одну ошибку многократно.
8. Не использовать `.recover()` так, чтобы ошибки внутри error-handling скрывали исходную причину. Ошибка сохранения terminal status или публикации terminal event должна быть заметна в логах.
9. Для новых и изменённых публичных классов/методов добавить JavaDoc на русском языке.

Добавь или обнови unit-тесты минимум для сценариев: success `IN_PROGRESS -> COMPLETED`, progress update + event, AI error -> `FAILED`, timeout -> `TIMED_OUT`, timeout публикует `TASK_TIMED_OUT`, cancellation не становится `FAILED`, повторный `registry.register()` не запускает задачу повторно, `registry.unregister()` вызывается после success и failure, ошибка публикации события корректно обрабатывается, optimistic lock/version conflict не запускает `AiClient`.

После изменений выполни релевантные backend-тесты. В конце покажи изменённые файлы, итоговую версию `ExecuteTaskUseCase`, созданные enum/классы, добавленные или изменённые тесты, краткие архитектурные причины изменений и результаты проверок. Не выполняй git commit самостоятельно.
