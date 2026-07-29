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

## Prompt 5
Исправь критическую проблему нарушения изоляции клиентов при отмене AI-задачи.

Проблема обнаружена в:

`CancelTaskUseCase.java`

Текущая логика вызывает локальный сигнал отмены через:

```java
registry.cancel(taskId)
```

до того, как `repository.cancel(taskId, clientId, ...)` подтвердит, что задача действительно принадлежит текущему клиенту и может быть отменена.

Из-за этого клиент B, знающий UUID задачи клиента A, может вызвать cancel для чужой задачи.

Даже если PostgreSQL затем отклонит отмену из-за несовпадения `client_id`, локальный cancellation token уже будет установлен.

В результате выполнение AI-задачи клиента A может остановиться, а запись в БД остаться в `IN_PROGRESS`.

## Требуемое поведение

Отмена должна происходить только после успешной проверки владельца и допустимого состояния задачи.

Правильная последовательность должна быть логически такой:

```text
CancelTaskUseCase
    |
    v
repository.cancel(taskId, clientId, expectedVersion)
    |
    | successful DB state transition
    v
registry.cancel(taskId)
    |
    v
publish TASK_CANCELLED
```

Не устанавливай cancellation token до того, как отмена успешно подтверждена persistence-слоем.

## Изоляция клиентов

Клиент должен иметь возможность отменять только собственную задачу.

Сценарий:

```text
client A -> creates task A
client B -> POST cancel task A
```

не должен влиять:

* на cancellation token задачи A;
* на выполнение MockAiClient;
* на статус задачи A;
* на progress задачи A;
* на возможность задачи A успешно перейти в COMPLETED.

Клиент B должен получить существующую корректную ошибку API согласно текущей политике приложения.

Не раскрывай через ошибку лишнюю информацию о существовании чужой задачи, если текущая реализация использует `404` для client isolation.

## Конкурентность

При исправлении обязательно проверь гонку:

```text
cancel vs complete
```

Недостаточно просто поменять местами:

```java
registry.cancel(...)
repository.cancel(...)
```

Необходимо гарантировать, что после успешного перехода задачи в `CANCELLED` поздний callback AI не сможет перевести её обратно в:

```text
COMPLETED
FAILED
IN_PROGRESS
```

Проверь существующие repository operations:

* `updateProgress`;
* `complete`;
* `fail`;
* `cancel`.

Терминальные и конкурентные обновления должны учитывать текущее состояние задачи и optimistic locking согласно существующей архитектуре.

Например после `CANCELLED` операция `complete()` должна завершиться без изменения состояния, если её условный SQL update затронул `0 rows`.

Не ломай существующую модель optimistic locking.

## Progress race

Проверь также ситуацию:

```text
DB -> CANCELLED
      |
      | уже запущенный progress callback
      v
updateProgress(...)
```

После отмены progress callback не должен возвращать задачу в рабочее состояние или изменять терминальный статус.

Если существующая repository-логика уже защищает это условным update — сохрани её.

Если защиты нет и она непосредственно связана с исправляемой гонкой — добавь минимально необходимую защиту.

## Execution error handling

Проверь текущую обработку cancellation в `ExecuteTaskUseCase`.

Не должно быть сценария:

```text
registry cancellation token = true
DB status = IN_PROGRESS
AI execution stopped
handleExecutionError ignores error because token cancelled
task remains IN_PROGRESS forever
```

После исправления cancellation token должен появляться только для задачи, которая действительно была успешно переведена в `CANCELLED`.

Не используй локальный cancellation token как единственный источник истины о persisted status задачи.

PostgreSQL остаётся источником истины для состояния задачи.

## Границы изменений

Исправь именно эту проблему и непосредственно связанные с ней race conditions.

Не выполняй большой рефакторинг:

* архитектуры;
* REST API;
* WebSocket;
* frontend;
* persistence слоя целиком;
* AI client.

Не меняй публичные API-контракты без необходимости.

Не добавляй новые framework или зависимости.

## Логирование

Используй существующее логирование проекта.

При попытке отмены чужой/недоступной задачи не логируй это как `ERROR`.

Если логирование уместно, используй существующий подход `DEBUG` или `WARN`.

Не логируй prompt или AI result.

## Тесты

Обязательно добавь regression tests.

### 1. Отмена чужой задачи

Сценарий:

```text
client A создаёт task A
task A переходит в IN_PROGRESS

client B отправляет cancel для task A
```

Проверить:

* запрос B получает ожидаемую ошибку;
* `registry.cancel(taskA)` фактически не влияет на execution;
* task A остаётся выполняться;
* task A продолжает получать progress;
* task A в итоге переходит в `COMPLETED`;
* результат сохраняется;
* `TASK_CANCELLED` для задачи A не публикуется.

Это основной regression test для найденной уязвимости.

### 2. Владелец успешно отменяет задачу

```text
client A создаёт task A
client A отменяет task A
```

Проверить:

* БД успешно переводит задачу в `CANCELLED`;
* только после этого срабатывает cancellation token;
* AI выполнение останавливается;
* финальный статус остаётся `CANCELLED`;
* публикуется ровно одно корректное событие `TASK_CANCELLED`.

### 3. Race cancel vs complete

Создай конкурентный тест, в котором выполнение AI завершается примерно одновременно с запросом cancel.

Допустимы только корректные терминальные результаты согласно текущей state machine.

Никогда не должно быть:

```text
CANCELLED -> COMPLETED
CANCELLED -> FAILED
```

после того, как cancel уже успешно зафиксирован в БД.

Проверь affected rows / optimistic locking.

### 4. Progress после отмены

Проверь, что поздний progress callback после успешного `CANCELLED` не изменяет состояние отменённой задачи.

### 5. Повторная отмена

Убедись, что существующая семантика повторного cancel не сломалась.

## Проверка существующих тестов

Не удаляй и не ослабляй существующие тесты.

После исправления выполни:

* unit tests `CancelTaskUseCase`;
* unit tests `ExecuteTaskUseCase`;
* repository tests;
* конкурентные тесты;
* REST cancel tests;
* все backend tests.

## prompt.md

Перед внесением изменений добавь этот оригинальный промт следующей записью в `prompt.md`.

Не изменяй предыдущие записи.

## Git

Не выполняй Git commit самостоятельно.

В конце покажи:
1. точную причину бага;
2. как выглядела неправильная последовательность операций;
3. как выглядит последовательность после исправления;
4. какие файлы изменены;
5. как теперь обеспечивается client isolation;
6. как защищена гонка `cancel vs complete`;
7. как защищён поздний progress update;
8. какие regression tests добавлены;
9. результаты тестов;

## Prompt 6
Исправь race condition и утечку WebSocket-подписок, обнаруженную при code review.

Проблема находится в логике обработки `SUBSCRIBE` около `TaskWebSocketHandler.java`: WebSocket получает `SUBSCRIBE`, асинхронно вызывается `getTask.get(...)`, соединение закрывается до завершения Future, `closeHandler` удаляет connection и subscriptions, а поздний success callback вызывает `registry.subscribe(connection, taskId)` и повторно добавляет уже закрытое соединение в registry.

Основной invariant: после закрытия WebSocket connection система никогда не должна позволять снова зарегистрировать это соединение, подписать его на task, добавить его в `taskId -> connections` или отправлять ему события. Закрытое/удалённое соединение является терминальным состоянием lifecycle, поздний asynchronous callback не должен "воскресить" connection.

Изучи текущую реализацию `TaskWebSocketHandler`, `WebSocketSubscriptionManager`/registry, регистрацию connection, `SUBSCRIBE`, `UNSUBSCRIBE`, `closeHandler`, `exceptionHandler`, cleanup, коллекции, модель конкурентности/event-loop и существующие WebSocket-тесты. Не предполагай устройство registry заранее — установи точную причину race condition.

Исправь сценарий `SUBSCRIBE -> getTask.get(...)`: если connection активен — выполнить subscribe, если уже closed/unregistered — ничего не подписывать. Не ограничивайся только `socket.isClosed()` как единственной защитой; основной источник истины lifecycle должен быть в registry, а проверка и регистрация subscription должны быть одной логически атомарной операцией. После `unregisterConnection(connection)` операция `subscribe(connection, taskId)` должна безопасно ничего не делать или возвращать результат, что connection уже не зарегистрирован. Не создавай connection автоматически внутри `subscribe`, если он был удалён после close.

Success callback после `getTask.get(...)` должен учитывать, что socket мог закрыться или connection мог быть unregister. Если connection больше не активен: не создавать subscription, не отправлять `SUBSCRIBED`, не отправлять current task state, не возвращать connection в registry. Failure callback после закрытия не должен пытаться отправлять `ERROR` в закрытый connection.

Убедись, что `UNSUBSCRIBE` остаётся безопасным/idempotent. После закрытия connection должны быть удалены все связи `connection -> clientId`, `connection -> taskIds`, `taskId -> connection`, а пустые task collections не должны накапливаться. `publish(...)` не должен пытаться отправлять событие connection, который уже был unregister; при неожиданном закрытии socket во время публикации cleanup должен оставаться корректным.

Добавь regression tests: основной race `SUBSCRIBE -> pending Future -> CLOSE -> Future success`; нормальная подписка; закрытие после subscription; несколько task subscriptions на одном connection; несколько connections на одном task с сохранением второй подписки после закрытия первой; client isolation не должен ослабляться.

Используй существующий SLF4J logging подход; DEBUG допустим для register/subscribe/unsubscribe/cleanup/игнорирования позднего callback, без INFO на каждый WebSocket event и без WARN для ожидаемой late callback race.

Границы изменений: исправить именно lifecycle WebSocket subscriptions и связанную race condition; не менять REST API, PostgreSQL, schema, AI execution, state machine задач, frontend, WebSocket protocol, формат событий; не добавлять framework/infrastructure; не реализовывать heartbeat или ограничение размера message. Если меняются публичные backend API, сохранить Javadoc на русском языке согласно `task.md`.

После реализации выполнить unit tests registry, WebSocket handler tests, WebSocket integration tests, существующие backend tests и backend build. Git commit самостоятельно не выполнять. В конце показать точную причину race condition, последовательность утечки, новый lifecycle, invariant registry, изменённые файлы, regression tests, результат основного race-теста, результаты тестов и backend build.

## Prompt 7
Исправь проблему отсутствия ограничения размера входящих WebSocket-сообщений, обнаруженную при code review.

Проблема находится в WebSocket-слое backend, в частности около обработки входящих сообщений в:

`TaskWebSocketHandler.java`

Сейчас входящее WebSocket-сообщение может быть передано в Jackson без явного ограничения размера.

HTTP `BodyHandler` не решает эту проблему, потому что WebSocket-сообщения обрабатываются отдельно.

Клиент может подключиться к WebSocket напрямую, минуя React frontend, и отправить очень большой JSON payload.

Это может привести к избыточному потреблению памяти, дорогому JSON parsing, блокировке Vert.x event loop и деградации работы приложения.

Важно: frontend validation является UX-ограничением и не считается защитой backend. WebSocket используется для управляющих сообщений вроде `SUBSCRIBE`, `UNSUBSCRIBE`, `PING`, поэтому допустимый размер входящего WebSocket message может быть значительно меньше лимита REST prompt.

Сначала изучи текущую реализацию: `TaskWebSocketHandler`, создание HTTP/WebSocket server, `HttpServerOptions`, настройки Vert.x WebSocket, обработку text frames/messages, JSON parsing, WebSocket error handling, конфигурацию приложения и существующие WebSocket-тесты. Определи, можно ли ограничить размер сообщения средствами используемой версии Vert.x до передачи payload в application handler. Используй API именно той версии Vert.x, которая подключена в проекте.

Требуемый лимит: ориентир `8192 bytes`. Предпочтительно сделать лимит конфигурируемым через `WEBSOCKET_MAX_MESSAGE_SIZE_BYTES=8192`, добавить default, `.env` и README. Ограничивать размер нужно как можно раньше средствами Vert.x HTTP/WebSocket server configuration. Даже при server-level limit оцени необходимость дополнительной проверки перед Jackson. Лимит считать в байтах, не в `String.length()`.

При превышении лимита oversized payload не должен передаваться в Jackson, use case не должен вызываться, subscription не должна создаваться, состояние приложения не должно меняться. Предпочтительно закрыть соединение корректным WebSocket close code. Не логировать payload, prompt, result или весь JSON.

Добавь regression tests: сообщение в пределах лимита, сообщение непосредственно ниже лимита, сообщение выше лимита, очень большое сообщение и сценарий, где другой клиент продолжает работать после oversized message от первого клиента.

Не менять REST prompt limit `PROMPT_MAX_LENGTH = 4000`, не ограничивать исходящие события тем же значением, не смешивать с другими review fixes. Если добавляются или изменяются публичные backend-классы, интерфейсы или нетривиальные публичные методы, соблюдай требования `task.md` к Javadoc на русском языке.

После реализации выполни WebSocket unit/integration tests, существующие backend tests и backend build. Git commit самостоятельно не выполнять. В конце покажи: где отсутствовало ограничение; какой лимит выбран и почему; на каком уровне Vert.x он применяется; есть ли дополнительная handler-level защита; что происходит при превышении лимита; какие файлы изменены; какие regression tests добавлены; подтверждение, что Jackson/use case не вызываются для oversized message; результаты тестов; результат backend build.

## Prompt 8
Проанализируй backend-проект и оцени, нужно ли выделить отдельный Vert.x verticle для управления жизненным циклом HTTP-сервера и инфраструктурных ресурсов.

Сначала изучи текущий запуск приложения: `Main`, `ApplicationBootstrap`, `ShutdownManager`, `VertxFactory`, `RouterFactory`, создание `HttpServer`, запуск Liquibase, создание `PgPool`, сборку repository/use cases/REST/WebSocket handlers, heartbeat и graceful shutdown. Определи, есть ли уже централизованное lifecycle-управление и как оно работает.

Если lifecycle уже реализован достаточно явно, не добавляй новый класс и объясни существующую схему запуска и остановки. Если управление размазано между bootstrap и shutdown-кодом или есть риск некорректного startup/shutdown поведения, добавь `MainVerticle extends AbstractVerticle` как основной владелец backend-инфраструктуры.

При добавлении `MainVerticle` соблюдай порядок запуска:

1. залогировать начало startup и безопасные параметры конфигурации;
2. выполнить Liquibase migrations вне event loop через Vert.x blocking API;
3. создать PostgreSQL reactive `Pool`;
4. создать WebSocket subscription registry и heartbeat service;
5. собрать repository, use cases, REST handler и WebSocket handler без изменения бизнес-логики;
6. создать router через существующий `RouterFactory`;
7. создать `HttpServerOptions` с текущим лимитом `websocketMaxMessageSizeBytes`;
8. запустить HTTP server и завершить `startPromise` только после успешного bind;
9. при ошибке startup закрыть уже созданные ресурсы и передать ошибку в deploy Vert.x.

Реализуй остановку в `MainVerticle.stop(...)` в обратном порядке:

1. закрыть HTTP server;
2. остановить heartbeat;
3. закрыть WebSocket connections;
4. закрыть PostgreSQL pool.

Закрытие должно быть idempotent и безопасным, если часть ресурсов не успела создаться из-за ошибки startup.

Обнови `ApplicationBootstrap` так, чтобы он отвечал только за внешний bootstrap:

1. создать Vert.x;
2. deploy `MainVerticle`;
3. дождаться результата deploy с fail-fast поведением;
4. закрыть Vert.x при ошибке;
5. зарегистрировать `ShutdownManager` после успешного deploy.

Не дублируй в `ApplicationBootstrap` сборку repository/use cases/router/server.

Обнови `ShutdownManager` так, чтобы он был адаптером между JVM shutdown hook и Vert.x lifecycle: при shutdown закрывать Vert.x. Ресурсы приложения должны освобождаться через `MainVerticle.stop(...)`, который Vert.x вызовет при закрытии.

Не меняй REST API, WebSocket protocol, PostgreSQL schema, бизнес-логику задач, frontend и существующий `RouterFactory` как точку сборки маршрутов. Если добавляются или меняются публичные backend-классы и нетривиальные публичные методы, сохрани Javadoc на русском языке.

После изменений выполни backend tests и `gradlew check`. Git commit самостоятельно не выполняй.

В конце покажи:

1. было ли lifecycle-управление до изменений;
2. почему добавлен `MainVerticle`;
3. чем отличается `ApplicationBootstrap.start(...)` от `MainVerticle.start(...)`;
4. какие файлы изменены;
5. какой порядок startup/shutdown получился;
6. какие проверки выполнены;
7. есть ли сторонние изменения в рабочем дереве, которые не относятся к задаче.

## Prompt 9
Проанализируй текущую реализацию конфигурации backend-проекта и оцени целесообразность разделения конфигов.

Нужно рассмотреть текущие классы конфигурации и места их использования: `AppConfig`, настройки PostgreSQL, запуск Liquibase, создание Vert.x PostgreSQL pool, запуск HTTP/WebSocket server, AI-настройки и JSON-сериализацию.

Основная цель — понять, нужно ли выделять отдельные конфиги, и если разделение действительно оправдано, выполнить его минимально.

Требования:

1. Не делать лишний рефакторинг и не редактировать код вне зоны задачи.
2. Сохранить текущее поведение приложения, env-переменные, default-значения и fail-fast валидацию.
3. Не менять REST API, WebSocket protocol, PostgreSQL schema, бизнес-логику, frontend и Docker API.
4. Если выделяется `DatabaseConfig`, использовать его только для PostgreSQL-настроек, Liquibase и `PgPoolFactory`.
5. `AppConfig` должен остаться верхнеуровневым конфигом приложения и содержать остальные runtime-настройки.
6. Не выделять отдельный `HttpConfig`, если в текущей реализации HTTP-конфиг представлен только одним значением и такое разделение будет формальным.
7. Отдельно оценить, нужен ли `JsonConfig` для текущей реализации проекта.
8. Если JSON уже настраивается через локальный `JsonMapper`/`ObjectMapper`, а глобальный Vert.x `DatabindCodec` не используется, не добавлять `JsonConfig` без необходимости.
9. Если добавляются или меняются публичные backend-классы и нетривиальные публичные методы, сохранить Javadoc на русском языке.

После изменений выполнить backend tests и `gradlew check`. Git commit самостоятельно не выполнять.

В конце показать:

1. какие конфиги были до изменения;
2. какое разделение выполнено и почему;
3. почему остальные конфиги не выделялись;
4. нужен ли `JsonConfig` в текущей реализации;
5. какие файлы изменены;
6. какие проверки выполнены;
7. есть ли сторонние изменения в рабочем дереве, не относящиеся к задаче.
