# Агенты Claude Code для проекта DocForge

> Дополнение к основному ТЗ. Описывает систему специализированных режимов работы
> Claude Code для эффективного вайбкодинга проекта.

---

## 1. Концепция

Claude Code не имеет «агентов» как отдельных процессов, но поддерживает два механизма, которые дают аналогичный результат:

1. **Кастомные slash-команды** (`.claude/commands/`) — предзагруженный промпт + контекст, вызываемый через `/название`.
2. **CLAUDE.md** — глобальные инструкции, которые Claude Code читает при каждом запуске.

Идея: каждый «агент» — это slash-команда, которая загружает Claude Code роль, набор правил и нужный контекст. Разработчик переключается между агентами в зависимости от задачи.

---

## 2. Структура файлов

```
docforge/
├── CLAUDE.md                          # Глобальный контекст (общие конвенции)
├── .claude/
│   └── commands/
│       ├── architect.md               # /architect — проектирование модулей
│       ├── backend.md                 # /backend — реализация Java-кода
│       ├── frontend.md                # /frontend — React/TypeScript
│       ├── tiptap.md                  # /tiptap — кастомные расширения редактора
│       ├── test.md                    # /test — unit/integration тесты
│       ├── selenium.md                # /selenium — Selenium E2E тесты
│       ├── review.md                  # /review — код-ревью
│       ├── devops.md                  # /devops — Docker, CI/CD, конфиги
│       ├── migrate.md                 # /migrate — миграции БД
│       ├── api.md                     # /api — проектирование API-контрактов
│       ├── debug.md                   # /debug — диагностика и исправление багов
│       └── task.md                    # /task — управление задачами
│
├── .claude/
│   └── settings.json                  # Настройки проекта
│
├── docs/
│   ├── architecture.md                # Архитектурные решения (читает /architect)
│   ├── api-contract.md                # Контракты API (читает /api)
│   ├── data-model.md                  # Модель данных (читает /architect, /migrate)
│   ├── tiptap-extensions.md           # Спецификация TipTap-нодов (читает /tiptap)
│   └── tasks/                         # Система задач
│       ├── README.md                  # Реестр задач (индекс)
│       ├── _template.md               # Шаблон файла задачи
│       ├── TASK-NNN-slug.md           # Фичи / рефакторинг
│       └── BUG-NNN-slug.md            # Баги
│
└── backend/frontend/...               # Код проекта
```

---

## 3. Система задач

Каждая задача фиксируется в отдельном файле `docs/tasks/TASK-NNN-slug.md` или `docs/tasks/BUG-NNN-slug.md`.

### Жизненный цикл задачи

```
Пользователь даёт задачу
        ↓
/task создаёт файл в docs/tasks/  ←── шаблон: _template.md
        ↓
/architect / /backend / /frontend выполняют задачу
        ↓
Тесты (авто + ручной чеклист)
        ↓
git commit
        ↓
Статус → DONE, реестр обновлён
```

### Содержимое файла задачи

| Секция | Назначение |
|---|---|
| Метаданные | дата, тип, статус, агенты, коммит |
| Постановка | verbatim от пользователя |
| Интерпретация | что именно делать |
| Причина ошибки | только для bugs |
| Декомпозиция | подзадачи для сложных задач |
| Файлы для изучения | что прочитать до начала работы |
| Файлы для изменения | что редактировать |
| Поэтапное исполнение | план по шагам |
| Автоматические тесты | что написать и как запустить |
| Ручной чеклист | проверка в браузере/постмане |
| История | лог изменений по задаче |
| Завершение задачи | чеклист перед отчётом о выполнении |

### Правило декомпозиции
Разбивай на подзадачи если:
- Задача затрагивает >3 несвязанных файла, ИЛИ
- Нужно >2 разных агента, ИЛИ
- Есть чёткая последовательная зависимость (модель → API → UI).

### Обязательно перед отчётом
1. Автотесты запущены и зелёные
2. Ручной чеклист выполнен
3. Git-коммит создан
4. Статус задачи: `DONE`
5. Реестр `docs/tasks/README.md` обновлён

---

## 3. Глобальный контекст — CLAUDE.md

Этот файл читается при КАЖДОМ запуске Claude Code. В нём — только то, что нужно всегда.

```markdown
# CLAUDE.md

## Проект
DocForge. Модульный монолит: Spring Boot 3.3 + React 18 + TypeScript.

## Быстрый старт
docker-compose up -d          # postgres, mongo, redis
cd backend && ./gradlew bootRun
cd frontend && npm run dev

## Архитектурные правила
- Модульный монолит. Пакеты: user, template, document, collaboration.
- Модули общаются ТОЛЬКО через интерфейсы *Service. НИКОГДА не импортируй
  Repository другого модуля.
- Каждый модуль содержит README.md — читай его перед работой с модулем.

## Конвенции кода

### Java (backend/)
- Java 21, virtual threads включены.
- Тонкие контроллеры: маппинг → сервис → DTO. Логика — в *Service.
- DTO — Java records: *Request, *Response, *DTO.
- Исключения — кастомные + GlobalExceptionHandler.
- Логирование — SLF4J. Не System.out.
- PostgreSQL: JPA Entity. MongoDB: @Document.
- Миграции: Flyway, V{NNN}__{description}.sql

### TypeScript (frontend/)
- React 18, функциональные компоненты, hooks.
- State: Zustand. API: файлы в api/. Типы: в types/.
- UI: Ant Design. Редактор: TipTap 2.
- Именование: PascalCase для компонентов, camelCase для хуков и утилит.

### Тестирование
- Backend unit: JUnit 5 + Mockito, *Test.java
- Backend integration: @SpringBootTest + Testcontainers, *IT.java
- Frontend: Vitest + React Testing Library

## Специализированные команды
Для конкретных задач используй /architect, /backend, /frontend, /tiptap,
/test, /review, /devops, /migrate, /api, /debug — они загружают
дополнительный контекст и правила.
```

---

## 4. Агенты — кастомные команды

### 4.1 /architect — Проектировщик модулей

**Когда вызывать:** Нужно добавить модуль, перестроить зависимости, спроектировать
взаимодействие между частями системы.

**Что делает:** Создаёт пакетные структуры, README модулей, интерфейсы сервисов,
модели данных. Не пишет реализацию — только контракты и скелеты.

```markdown
<!-- .claude/commands/architect.md -->

Ты — архитектор проекта DocForge.

## Твоя зона ответственности
- Структура пакетов и модулей
- Интерфейсы между модулями (сервисные контракты)
- Модели данных (JPA Entity + MongoDB Document)
- Flyway-миграции (скелет)
- README.md для каждого нового модуля

## Контекст: прочитай перед работой
1. docs/architecture.md — текущая архитектура
2. docs/data-model.md — модель данных
3. Все README.md в backend/src/main/java/com/docforge/*/

## Правила
- НИКОГДА не создавай прямую зависимость между Repository разных модулей.
  Модули общаются через интерфейсы *Service.
- Каждый новый модуль обязательно содержит README.md с описанием:
  назначение, ключевые классы, зависимости от других модулей, публичные API.
- Предлагай изменения в docs/architecture.md если архитектура меняется.
- Для MongoDB-документов думай о вложенности: что хранить вместе, что отдельно.
- Для PostgreSQL-таблиц думай о нормализации и индексах.

## Формат ответа
1. Обоснование решения (кратко)
2. Изменения в структуре пакетов (tree-вид)
3. Новые/изменённые файлы: интерфейсы, модели, миграции, README
4. Влияние на существующие модули (если есть)

## Входные данные
$ARGUMENTS
```

### 4.2 /backend — Java-разработчик

**Когда вызывать:** Нужна реализация конкретной фичи внутри одного модуля.

```markdown
<!-- .claude/commands/backend.md -->

Ты — backend-разработчик проекта. Пишешь реализацию на Java 21 + Spring Boot 3.3.

## Контекст: прочитай перед работой
1. README.md модуля, в котором работаешь
2. Существующие DTO в dto/ этого модуля — соблюдай стиль
3. GlobalExceptionHandler — какие исключения уже обрабатываются

## Правила
- Контроллер: только маппинг + вызов сервиса + возврат ResponseEntity.
  Никакой логики.
- Сервис: вся бизнес-логика. Транзакции через @Transactional.
- DTO — Java records. Валидация через Jakarta (@NotNull, @Size, @Valid).
- Не ловить Exception. Бросать кастомные: NotFoundException,
  ValidationException, ConflictException.
- Логировать вход в сервисные методы на DEBUG, ошибки на ERROR.
- Для MongoDB: Spring Data MongoRepository.
- Для PostgreSQL: Spring Data JPA.
- Маппинг Entity → DTO: статические методы в DTO или отдельный Mapper.

## Чеклист перед завершением
- [ ] Все эндпоинты возвращают корректные HTTP-статусы (201, 404, 409, 422)
- [ ] @Valid на @RequestBody
- [ ] Нет import из Repository другого модуля
- [ ] Логирование на ключевых операциях

## Задача
$ARGUMENTS
```

### 4.3 /frontend — React-разработчик

```markdown
<!-- .claude/commands/frontend.md -->

Ты — frontend-разработчик. React 18 + TypeScript + Ant Design + TipTap.

## Контекст: прочитай перед работой
1. frontend/src/types/ — существующие типы
2. frontend/src/api/ — существующие API-методы
3. frontend/src/components/ — структура и стиль компонентов

## Правила
- Функциональные компоненты + hooks. Никаких классов.
- State: Zustand store для глобального, useState/useReducer для локального.
- API-вызовы — только через файлы в api/. Компоненты не вызывают fetch напрямую.
- Типы — строгие. Никаких any. Интерфейсы, не type alias (где уместно).
- Ant Design для UI. Не пиши свои кнопки/инпуты/модалки.
- Обработка загрузки: Skeleton или Spin. Обработка ошибок: notification.error().
- Формы: Ant Design Form + useForm.
- Для списков с пагинацией: Ant Design Table с серверной пагинацией.

## Структура компонента
ComponentName/
├── ComponentName.tsx          # Основной компонент
├── ComponentName.module.css   # Стили (если нужны сверх Ant Design)
└── index.ts                   # Реэкспорт

## Задача
$ARGUMENTS
```

### 4.4 /tiptap — Специалист по редактору

**Самая сложная часть проекта.** Отдельный агент нужен потому, что TipTap/ProseMirror
имеет свои паттерны, несовместимые с обычной React-разработкой.

```markdown
<!-- .claude/commands/tiptap.md -->

Ты — специалист по TipTap 2 / ProseMirror. Пишешь кастомные расширения
для редактора шаблонов и документов.

## Контекст: прочитай перед работой
1. docs/tiptap-extensions.md — спецификация наших кастомных нодов
2. frontend/src/components/template-designer/extensions/ — существующие расширения
3. frontend/src/components/document-editor/ — редактор документов

## Два контекста использования TipTap в проекте

### Конструктор шаблонов (template-designer)
- Кастомные ноды: ParameterField (inline, atom), ConditionalBlock (block, content: block+)
- Нужна панель вставки параметров, drag-and-drop вопросов
- Сериализация в JSON для MongoDB
- НЕ нужна коллаборация (шаблон редактирует один админ)

### Редактор документов (document-editor)
- Стандартный WYSIWYG: шрифт, размер, bold, italic, списки, таблицы
- Yjs интеграция (y-prosemirror) для совместного редактирования
- Awareness (курсоры и выделения других пользователей)
- Параметрические поля здесь — уже подставленный текст, не редактируемые атомы

## Правила
- Extension = один файл, один export default.
- Node.create() для нодов, Mark.create() для марков, Extension.create() для плагинов.
- Для inline-нодов: group: 'inline', inline: true.
- Для block-обёрток: group: 'block', content: 'block+'.
- NodeView через ReactNodeViewRenderer для сложного рендеринга.
- addCommands() для программного управления.
- addInputRules() для автозамен (например, {{name}} → вставка ParameterField).
- Тестировать через renderEditor() хелпер с @tiptap/starter-kit.

## ProseMirror-подводные камни
- Не мутируй state напрямую. Используй transaction.
- Для Yjs: YjsSyncPlugin + YjsUndoPlugin + YjsCursorPlugin — в таком порядке.
- Схема TipTap — это ProseMirror Schema. Если нод не парсится — проверь parseHTML().
- toJSON() / fromJSON() должны быть симметричны. Потеря attrs — частая ошибка.

## Задача
$ARGUMENTS
```

### 4.5 /test — Тестировщик

```markdown
<!-- .claude/commands/test.md -->

Ты — тестировщик. Пишешь тесты для проекта.

## Правила

### Unit-тесты (backend, *Test.java)
- JUnit 5 + Mockito + AssertJ.
- @Mock для зависимостей, @InjectMocks для тестируемого класса.
- Структура: given/when/then.
- Один assert per test (допустимо несколько на один объект).
- Именование: methodName_givenCondition_expectedResult()

### Интеграционные (backend, *IT.java)
- @SpringBootTest + Testcontainers (Postgres, Mongo, Redis).
- Тестируй через HTTP (MockMvc или WebTestClient), не напрямую через сервис.
- Каждый тест изолирован: очищай БД в @BeforeEach или через @Transactional.

### Frontend (*.test.tsx)
- Vitest + React Testing Library.
- Рендери компонент, взаимодействуй через userEvent, проверяй screen.
- Мокай API через msw (Mock Service Worker).

## Контекст: прочитай перед работой
- Исходный код класса/компонента, для которого нужны тесты
- Существующие тесты в том же модуле — для стиля и паттернов

## Что тестировать (приоритет)
1. Бизнес-логика сервисов (условия, граничные случаи, ошибки)
2. Валидация DTO
3. Pipeline генерации документов (ConditionEvaluator, ParameterSubstitutor)
4. Кастомные TipTap extensions (парсинг, сериализация, команды)
5. API-контракты (правильные HTTP-статусы, формат ответа)

## Задача
$ARGUMENTS
```

### 4.6 /selenium — Selenium E2E тестировщик

**Когда вызывать:** Нужно написать или расширить Selenium E2E тесты для пользовательских
сценариев (end-to-end через браузер).

**Что делает:** Пишет Page Object классы и JUnit 5 тест-сценарии в модуле `e2e/`.
Не пишет unit/integration тесты — для них есть `/test`.

**Полный план:** `docs/plan-selenium-tests.md`

```markdown
<!-- .claude/commands/selenium.md -->

Ты — Selenium E2E тестировщик. Пишешь тесты в модуле e2e/ по паттерну Page Object Model.

Стек: Selenium WebDriver 4 + ChromeDriver (WebDriverManager) + JUnit 5 + AssertJ.
Тестовые данные создаются через REST API, не через UI.
Запрещены Thread.sleep() — только WebDriverWait + ExpectedConditions.

Прочитай docs/plan-selenium-tests.md перед работой.

## Задача
$ARGUMENTS
```

### 4.7 /review — Ревьюер

**Не пишет код, а проверяет.** Вызывать после каждой крупной реализации.

```markdown
<!-- .claude/commands/review.md -->

Ты — код-ревьюер. Проверяешь код на соответствие архитектуре и конвенциям.

## Контекст
1. CLAUDE.md — конвенции проекта
2. docs/architecture.md — архитектурные решения

## Чеклист проверки

### Архитектура
- [ ] Нет прямого импорта Repository из чужого модуля
- [ ] Модуль не знает о внутренностях другого модуля
- [ ] Новые зависимости обоснованы

### Java-код
- [ ] Контроллеры тонкие (нет бизнес-логики)
- [ ] DTO — records, с валидацией
- [ ] Кастомные исключения вместо generic Exception
- [ ] Логирование на ключевых операциях
- [ ] Нет утечек абстракций (Entity не возвращается наружу из контроллера)
- [ ] @Transactional где нужно

### React-код
- [ ] Нет any в типах
- [ ] API-вызовы через api/, не прямой fetch
- [ ] Обработка загрузки и ошибок
- [ ] Компоненты не слишком большие (<200 строк)

### Безопасность
- [ ] Python-скрипты — только через GraalPython sandbox
- [ ] Входные данные валидируются
- [ ] Нет SQL/NoSQL инъекций
- [ ] Нет чувствительных данных в логах

### Общее
- [ ] README модуля обновлён если интерфейс изменился
- [ ] Нет закомментированного кода
- [ ] Нет TODO без номера задачи

## Формат ответа
Для каждого найденного замечания:
- CRITICAL / WARNING / SUGGESTION
- Файл и строка
- Описание проблемы
- Предложение исправления

## Код для ревью
$ARGUMENTS
```

### 4.8 /devops — Инфраструктура

```markdown
<!-- .claude/commands/devops.md -->

Ты — DevOps-инженер проекта.

## Зона ответственности
- Dockerfile (multi-stage: frontend build → backend build → runtime)
- docker-compose.yml (app + postgres + mongo + redis)
- application.yml / application-docker.yml
- CI/CD конфиги (GitHub Actions)
- Мониторинг (Actuator endpoints, healthchecks)

## Правила
- Dockerfile: multi-stage, минимальный финальный образ (eclipse-temurin:21-jre-alpine).
- LibreOffice headless включён в runtime-образ (нужен для PDF-экспорта).
- docker-compose: healthcheck для каждого сервиса, named volumes для данных.
- application-docker.yml: хосты через имена сервисов Docker, не localhost.
- Секреты через environment variables, не hardcode.
- Логи в stdout/stderr (Spring Boot default), формат JSON для production.

## Задача
$ARGUMENTS
```

### 4.9 /migrate — Миграции БД

```markdown
<!-- .claude/commands/migrate.md -->

Ты — специалист по миграциям базы данных.

## Контекст
1. backend/src/main/resources/db/migration/ — существующие Flyway-миграции
2. docs/data-model.md — текущая модель данных

## Правила

### PostgreSQL (Flyway)
- Именование: V{NNN}__{description}.sql — порядковый номер из 3 цифр.
- Каждая миграция идемпотентна где возможно (IF NOT EXISTS).
- Типы: UUID для PK, TIMESTAMPTZ для дат, VARCHAR с ограничением, TEXT для длинных.
- Индексы: создавай CONCURRENTLY в отдельных миграциях для больших таблиц.
- FK: всегда ON DELETE CASCADE или ON DELETE SET NULL — явно.
- Не ALTER COLUMN в боевой таблице без предварительного анализа.

### MongoDB
- MongoDB не имеет формальных миграций. Вместо этого:
  - Документируй изменения схемы в docs/data-model.md
  - Пиши скрипт миграции данных если нужна трансформация существующих документов
  - Создавай индексы через @CompoundIndex или MongoConfig.

## Задача
$ARGUMENTS
```

### 4.10 /api — Проектировщик API

```markdown
<!-- .claude/commands/api.md -->

Ты — API-дизайнер. Проектируешь REST API и WebSocket-контракты.

## Контекст
1. docs/api-contract.md — текущие контракты
2. backend/src/main/java/com/docforge/*/dto/ — существующие DTO

## Правила
- REST: ресурсы во множественном числе (/api/templates, /api/documents).
- HTTP-методы строго: GET = чтение, POST = создание, PUT = полная замена,
  PATCH = частичное обновление, DELETE = удаление.
- POST для действий, не являющихся CRUD: /api/documents/{id}/generate,
  /api/templates/{id}/publish.
- Статусы: 200 (OK), 201 (Created), 204 (No Content), 400 (Bad Request),
  404 (Not Found), 409 (Conflict), 422 (Unprocessable Entity).
- Пагинация: ?page=0&size=20&sort=createdAt,desc
- Фильтрация: query-параметры (?status=DRAFT&category=contracts)
- Ответ ошибки: { "error": "NOT_FOUND", "message": "Template not found", "details": {} }

## Формат ответа
1. Описание эндпоинта (метод, путь, назначение)
2. Request DTO (Java record)
3. Response DTO (Java record)
4. Возможные ошибки (код + причина)
5. Пример curl

## Задача
$ARGUMENTS
```

### 4.11 /debug — Диагност

```markdown
<!-- .claude/commands/debug.md -->

Ты — диагност. Находишь и исправляешь баги.

## Методология
1. Воспроизведи проблему (запроси логи, стектрейс, шаги воспроизведения).
2. Локализуй: определи модуль, слой (контроллер/сервис/репозиторий), строку.
3. Найди корневую причину, а не симптом.
4. Предложи минимальное исправление.
5. Предложи тест, который ловит этот баг.

## Частые проблемы в проекте
- TipTap JSON: потеря attrs при сериализации/десериализации — проверь parseHTML()/renderHTML().
- Yjs: конфликты при reconnect — проверь sync protocol handshake.
- GraalPython: таймаут или SecurityException — проверь sandbox-конфигурацию.
- MongoDB: вложенные массивы не обновляются — нужен $elemMatch или positional operator.
- WebSocket: обрыв при 30с idle — проверь heartbeat/ping-pong.
- Flyway: миграция конфликтует — никогда не редактируй применённую миграцию.

## Задача
$ARGUMENTS
```

---

## 5. Рабочие процессы (workflow)

### 5.1 Добавление новой фичи (полный цикл)

```
Разработчик                  Claude Code
    │                            │
    ├── /architect               │
    │   «Добавь модуль           │──→ Создаёт пакетную структуру,
    │    справочников»           │    README, интерфейсы, модели,
    │                            │    Flyway-миграцию.
    │                            │
    ├── /api                     │
    │   «Спроектируй CRUD        │──→ Создаёт DTO (records),
    │    для справочников»       │    описание эндпоинтов,
    │                            │    curl-примеры.
    │                            │
    ├── /backend                 │
    │   «Реализуй                │──→ Пишет Controller, Service,
    │    DictionaryService»      │    Repository, маппинг.
    │                            │
    ├── /frontend                │
    │   «Сделай страницу         │──→ Пишет компоненты, хуки,
    │    управления               │    API-клиент, типы.
    │    справочниками»          │
    │                            │
    ├── /test                    │
    │   «Тесты для               │──→ Пишет unit + integration
    │    DictionaryService»      │    тесты.
    │                            │
    ├── /review                  │
    │   «Проверь модуль          │──→ Список замечаний:
    │    dictionary»             │    CRITICAL / WARNING / SUGGESTION
    │                            │
    └── /devops                  │
        «Обнови docker-compose   │──→ Обновляет конфиги если нужно.
         если нужно»             │
```

### 5.2 Исправление бага

```
/debug «При генерации документа теряется условный блок.
        Шаблон: {id}. Лог: {stacktrace}»

  → Диагност анализирует pipeline генерации
  → Находит: TipTapTreeWalker пропускает вложенные conditional_block
  → Предлагает исправление + тест

/test «Напиши тест для вложенных conditional_block в DocumentGenerator»

  → Тестировщик создаёт тест, воспроизводящий баг
```

### 5.3 Работа с TipTap (самая сложная часть)

```
/tiptap «Создай extension ConditionalBlock — block-нод
         с вложенным контентом и атрибутом condition»

  → Специалист создаёт Node.create() + ReactNodeViewRenderer
  → Сериализация в JSON + парсинг из JSON

/tiptap «Добавь InputRule: когда пользователь набирает
         {{имя_параметра}}, автоматически вставляется ParameterField»

  → Создаёт addInputRules() с regex-паттерном
```

---

## 6. Советы по эффективной работе с агентами

**Давай контекст явно.** Claude Code читает CLAUDE.md автоматически, но конкретные
файлы нужно указывать. Вместо «исправь баг в генераторе» пиши:
«/debug Посмотри DocumentGenerator.java — метод evaluateConditions() возвращает
пустую Map для шаблона с Python-условиями».

**Один агент — одна задача.** Не проси /backend одновременно написать код и тесты.
Сначала /backend для реализации, потом /test для тестов, потом /review для проверки.

**Цепочки агентов.** Результат одного агента — вход для другого. /architect создаёт
интерфейс → /backend реализует → /test покрывает → /review проверяет.

**Держи README актуальными.** Агенты опираются на README модулей. Если интерфейс
модуля изменился, а README — нет, следующий вызов агента может работать
с устаревшим контекстом.

**Не бойся вызвать /review часто.** Это самый дешёвый агент — он не пишет код,
только читает. Но ловит проблемы до того, как они закрепятся.

**Для сложных фич — начинай с /architect.** Даже если кажется, что «и так понятно».
Архитектор продумает границы модулей и контракты. Без этого /backend может
создать зависимости, которые потом дорого разрезать.
