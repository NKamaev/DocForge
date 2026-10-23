# Tag Index

Единственный реестр тегов. Структура: `.memory/tags/{category}/{file}.md`.
Обновлено: `2026-10-13`.

---

## Architecture

| Tag | File | Description |
|-----|------|-------------|
| `architecture:overview` | `architecture/overview.md` | Модульный монолит, карта модулей, слои, известные архитектурные риски |
| `architecture:persistence` | `architecture/persistence.md` | PostgreSQL vs MongoDB, общий UUID, Flyway V001–V016 |
| `architecture:generation-pipeline` | `architecture/generation-pipeline.md` | DocumentGenerator (4 шага), TreeWalker, simple/GraalPython условия |
| `architecture:collaboration` | `architecture/collaboration.md` | Yjs/WS: серверный хендлер, протокол, persisted store, useCollaboration |
| `architecture:export` | `architecture/export.md` | DOCX (POI) / PDF (openhtmltopdf), NodeResolver, ограничения |
| `architecture:formatting` | `architecture/formatting.md` | Дата/число/ФИО/слова, enum-ы, порядок резолва форматов |
| `architecture:security-rbac` | `architecture/security-rbac.md` | JWT, DocumentPermission/Role, folder-inherit, авторизационные грейды |

## Frontend

| Tag | File | Description |
|-----|------|-------------|
| `frontend:workspace` | `frontend/workspace.md` | /fill split-panel: QuestionnairePage, DocumentWorkspace, бидирекция, автосейвы |
| `frontend:tiptap-extensions` | `frontend/tiptap-extensions.md` | Каталог ~16 кастомных TipTap-нод/плагинов (designer + workspace) |
| `frontend:api-layer` | `frontend/api-layer.md` | Паттерн api/*.ts, axiosInstance, 401-логика, toast CustomEvent, authStore |
| `frontend:ui-migration` | `frontend/ui-migration.md` | shadcn 36 модулей, Tailwind 4, остатки AntD (1 импорт) |

## Conventions

| Tag | File | Description |
|-----|------|-------------|
| `conventions:conventions` | `conventions/conventions.md` | Нейминг, слои, DTO-records, изоляция модулей, конвенции тестов |
| `conventions:task-tracker` | `conventions/task-tracker.md` | Матрица переходов статусов, `allow_without_commit`, ошибки-как-значения, поле `author` |

## Pitfalls

| Tag | File | Description |
|-----|------|-------------|
| `pitfalls:yjs-sync` | `pitfalls/pitfalls-yjs-sync.md` | Гонки REST↔Yjs (BUG-020), StrictMode двойной коннект, Yjs-reset (BUG-046) |
| `pitfalls:conditional-rendering` | `pitfalls/pitfalls-conditional-rendering.md` | Условные блоки на /fill и в экспорте (BUG-012/014/019), двойной return (BUG-013) |
| `pitfalls:tiptap-react` | `pitfalls/pitfalls-tiptap-react.md` | setContent emits onUpdate (TASK-009), stable NodeView, дубль fontSize |
| `pitfalls:async-ui` | `pitfalls/pitfalls-async-ui.md` | Promise leak / stale state / silent catch (TASK-030) |
| `pitfalls:build-hygiene` | `pitfalls/pitfalls-build-hygiene.md` | Stray `.js` затеняет `.tsx` в Vite, мёртвые артефакты после смены подхода |

## Best Practices & Pitfalls (якорные)

| Tag | File | Description |
|-----|------|-------------|
| `practices:best-practices` | `practices/best-practices.md` | Источник истины, делегирование, слои, работа с редактором, память. Загружать всегда |
| `practices:lessons` | `practices/lessons.md` | Извлечённые уроки по багам + наблюдения по кодовой базе |

## Tickets

| Tag | File | Description |
|-----|------|-------------|
| `TASK-006` | `tickets/TASK-006.md` | /fill feature parity — реализовано (body, current_body, Yjs, ShareDialog) |
| `TASK-007` | `tickets/TASK-007.md` | Ревизии: исследование нагрузки/очистки — открыт |
| `TASK-019` | `tickets/TASK-019.md` | Diff редакций (backend API + DiffViewer) — закрыт; заменён на TASK-064 |
| `TASK-059` | `tickets/TASK-059.md` | ShareDialog («Доступ») на /fill — закрыто |
| `TASK-062` | `tickets/TASK-062.md` | Diff не учитывает значения параметров (answers) — открыт |
| `TASK-063` | `tickets/TASK-063.md` | Полировка diff-view (baseline, E2E, точность) — открыт (частично закрыт TASK-064) |
| `TASK-064` | `tickets/TASK-064.md` | Structural rich-diff редакций (вариант B) — закрыт |
| `BUG-010` | `tickets/BUG-010.md` | ОЛ/ответы не синхронизируются в реалтайме — закрыт (канал yAnswers) |
| `BUG-012` | `tickets/BUG-012.md` | Все условные блоки видны на /fill — закрыт |
| `BUG-019` | `tickets/BUG-019.md` | Скрытые блоки в DOCX/PDF — закрыт (`9d972fe`, `ConditionalNodeFilter`) |
| `BUG-020` | `tickets/BUG-020.md` | yAnswers инициализируется до WS-sync — закрыт (seeding guard) |
| `BUG-029` | `tickets/BUG-029.md` | Toast не показывался программно — закрыт (CustomEvent app-toast) |
| `BUG-046` | `tickets/BUG-046.md` | Откат к редакции через reload — закрыт (программный Yjs-reset) |
| `BUG-047` | `tickets/BUG-047.md` | ОЛ пуст при вопросах в шаблоне — закрыт (snapshot-first questionnaire, `b0246b4`) |
| `BUG-048` | `tickets/BUG-048.md` | Permission-check на revision/answers/generate эндпоинтах — закрыт (`2cbe399`, `17b6240`, `b758c12`) |
| `BUG-049` | `tickets/BUG-049.md` | DocumentsPage bulk-кнопки «Скачать»/«Удалить» без onClick — закрыт (`7736bf1`) |
| `BUG-042` | `tickets/BUG-042.md` | `useCollaboration.test.ts`: buildWsUrl тесты падали из-за dev-ветки — закрыт (`6e48427`) |
| `BUG-043` | `tickets/BUG-043.md` | Пустой опросный лист: «Страница 1 из 0» и активная «Далее» — закрыт (`5060e27`) |
| `BUG-050` | `tickets/BUG-050.md` | Документные эндпоинты без permission-check: export docx/pdf, participants — закрыт (`805820f`) |
| `BUG-051` | `tickets/BUG-051.md` | Legacy participants API: MANAGER может выдать OWNER — закрыт (`86b2038`) |
| `BUG-052` | `tickets/BUG-052.md` | `POST /api/documents` без проверки доступа к шаблону/папке — закрыт (`07bef1d`) |
| `BUG-058` | — (только tracker) | Инстанцирование черновых версий опубликованного шаблона — TODO |
| `BUG-059` | — (только tracker) | Read-эндпоинты шаблонов без проверки прав — TODO |
| `BUG-060` | — (только tracker) | OWNER-роль неизменяема: нет снятия/передачи владения — TODO |
| `BUG-061` | — (только tracker) | `currentUserId()` глотает исключение внутри транзакции → `UnexpectedRollbackException` — TODO |
| `BUG-062` | — (только tracker) | Черновик шаблона: version=1 при major/minor=1.0 — TODO |
| `BUG-063` | — (только tracker) | `current_body` без валидации: CCE/500 на attrs/body-кастах — TODO |
| `BUG-064` | — (только tracker) | Единая семантика условий: генератор не обрабатывает `conditionalInline` — TODO |
| `BUG-065` | — (только tracker) | `NodeResolver` читает `clauseId` вместо `id`/`refId` — TODO |
| `BUG-066` | — (только tracker) | Экспорт теряет текст видимого `numberedClause` — TODO |
| `BUG-067` | — (только tracker) | `HttpMessageNotReadableException` → 500 вместо 400 — TODO |
| `TASK-066` | — (только tracker) | Вынести пайплайн экспорта из `DocumentController` в сервис — TODO |
| `TASK-065` | — (только tracker) | Удалить legacy participants API — TODO |
| `BUG-039` | `tickets/BUG-039.md` | Белый экран при создании документа — невоспроизводим (устарел после миграции UI) |
| `BUG-044` | `tickets/BUG-044.md` | Счётчик шаблонов за неделю на главной (был hardcoded `+2`) — закрыт (`a3e4f2b`) |
| `BUG-053` | `tickets/BUG-053.md` | Дубли снимков шаблона → 500 при создании документа — закрыт (`d80c55a`) |
| `BUG-054` | `tickets/BUG-054.md` | Конфликт нумерации publish()/saveVersion() в `version_number` — закрыт (`c0f4730`) |
| `BUG-055` | — (только tracker) | Unique-индекс снимков + защита от конкурентной публикации — TODO |
| `BUG-056` | — (только tracker) | Yjs «Unexpected end of array» в консоли на `/fill` — TODO |
| `BUG-057` | `tickets/BUG-057.md` | Мёртвая кнопка «Импорт DOCX» на главной — закрыт (`25b830a`) |

---

## Архив (индекс, без файлов-тегов)

Полный исторический архив задач — `docs/archive/tasks/tasks/` (BUG-001…021, TASK-002…039).
Тикеты выше — только ключевые, остальные при необходимости восстанавливаются из архива по запросу.
Источники Git: ветка `main` (текущий проект DocForge; legacy-версия переименована в DocForgeOld и заархивирована).

<!-- Добавляй новые категории/секции по мере роста памяти. -->
| `BUG-021` | `tickets/BUG-021.md` | NPE в applyAnswers (switch на null type) — закрыт (`9619f81`) |
