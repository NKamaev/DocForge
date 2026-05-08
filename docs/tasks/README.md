# DocForge — Реестр задач

> Каждая задача — отдельный файл в этой папке.  
> Формат имени: `TASK-NNN-slug.md` (фичи/рефакторинг) или `BUG-NNN-slug.md` (ошибки).  
> Шаблон нового файла: [_template.md](_template.md)

---

## Статусы

| Статус | Смысл |
|---|---|
| `TODO` | Задача принята, ещё не начата |
| `IN_PROGRESS` | В работе |
| `REVIEW` | Реализовано, ждёт ревью |
| `DONE` | Выполнено, коммит создан |
| `BLOCKED` | Заблокирована зависимостью |
| `CANCELLED` | Отменена |

---

## Активные задачи

| ID | Название | Тип | Статус | Агенты | Дата |
|---|---|---|---|---|---|
| [BUG-006](BUG-006-docx-pdf-export-regression-fill.md) | DOCX/PDF экспорт не работает на /fill (регрессия BUG-005) | bug | `DONE` | `/frontend`, `/backend` | 2026-04-17 |
| [TASK-007](TASK-007-revision-functionality-research.md) | Исследование функционала ревизий — нагрузка, кейсы, очистка | research | `TODO` | `/architect`, `/backend` | 2026-04-17 |
| [TASK-008](TASK-008-questionnaire-review-fixes.md) | QuestionnairePage — useCallback, тесты, типизация (ревью BUG-005) | refactor | `TODO` | `/frontend` | 2026-04-17 |
| [TASK-009](TASK-009-suppress-onupdate-on-init.md) | Подавить onUpdate при инициализации initialBody в DocumentWorkspace | refactor | `TODO` | `/frontend` | 2026-04-17 |
| [TASK-010](TASK-010-document-workspace-initial-body-test.md) | Unit-тест DocumentWorkspace — инициализация из initialBody | refactor | `TODO` | `/frontend` | 2026-04-17 |
| [TASK-011](TASK-011-autotest-script.md) | Скрипт запуска автоматических тестов | feature | `TODO` | `/backend`, `/frontend` | 2026-04-18 |
| [TASK-012](TASK-012-collab-research.md) | Исследование одновременной работы пользователей | research | `TODO` | `/architect`, `/backend`, `/frontend` | 2026-04-18 |
| [TASK-013](TASK-013-mcp-servers-research.md) | Исследование полезных MCP-серверов для проекта | research | `DONE` | `/architect` | 2026-04-18 |
| [BUG-007](BUG-007-dev-sh-not-starting.md) | dev.sh не запускает приложение при ручном вызове | bug | `TODO` | — | 2026-04-18 |
| [BUG-008](BUG-008-participants-panel-duplicates-overflow.md) | ParticipantsPanel — дубли пользователей и бесконечное расширение | bug | `DONE` | `/frontend` | 2026-04-18 |
| [BUG-009](BUG-009-hardcoded-ws-url.md) | Hardcoded WebSocket URL ws://localhost:8080 в useCollaboration | bug | `DONE` | `/frontend` | 2026-04-18 |
| [TASK-014](TASK-014-collab-review-frontend-fixes.md) | Frontend-исправления по ревью TASK-006-2: loginToColor, cast, callbacks | refactor | `TODO` | `/frontend` | 2026-04-18 |
| [TASK-015](TASK-015-yjs-store-backend-fixes.md) | YjsDocumentStoreImpl: атомарный debounce, evict-flush, узкий catch | refactor | `TODO` | `/backend` | 2026-04-18 |
| [TASK-016](TASK-016-collab-unit-tests.md) | Unit-тесты коллаборации: pack/unpack, ParticipantsPanel | refactor | `TODO` | `/backend`, `/frontend` | 2026-04-18 |
| [TASK-017](TASK-017-cleanup-stale-doceditor-refs.md) | Очистка устаревших ссылок на DocumentEditor в docs | refactor | `TODO` | `/task` | 2026-04-18 |
| [BUG-011](BUG-011-revision-not-saved-on-body-edit.md) | Редакции не сохраняются при изменении текста документа | bug | `TODO` | `/frontend` | 2026-04-18 |
| [TASK-018](TASK-018-rename-revisions-to-editions.md) | Переименовать ревизии в редакции, добавить автора изменений (2 подзадачи) | refactor | `TODO` | `/backend`, `/frontend` | 2026-04-18 |
| [TASK-018-1](TASK-018-1-rename-revisions-backend.md) | Backend: DTO editionNumber + authorName через UserService | refactor | `TODO` | `/backend` | 2026-04-18 |
| [TASK-018-2](TASK-018-2-rename-revisions-frontend.md) | Frontend: UI «редакции», отображение authorName | refactor | `TODO` | `/frontend` | 2026-04-18 |
| [TASK-019](TASK-019-show-document-body-diff.md) | Отображение изменений в тексте документа (эпик, 2 подзадачи) | feature | `TODO` | `/backend`, `/frontend` | 2026-04-18 |
| [TASK-019-1](TASK-019-1-diff-api-backend.md) | Backend: API diff между редакциями документа | feature | `TODO` | `/backend` | 2026-04-18 |
| [TASK-019-2](TASK-019-2-diff-view-frontend.md) | Frontend: переключатель и визуализация изменений (DiffViewer) | feature | `TODO` | `/frontend` | 2026-04-18 |
| [TASK-020](TASK-020-collab-cursors.md) | Каретки соавторов в редакторе документа | feature | `TODO` | `/frontend` | 2026-04-18 |
| [BUG-012](BUG-012-conditional-blocks-all-visible-on-fill.md) | Все условные блоки видны одновременно на /fill | bug | `DONE` | `/frontend` | 2026-04-18 |
| [BUG-013](BUG-013-inline-node-double-return.md) | WorkspaceConditionalInlineNode: два return вызывают перемонтирование | bug | `TODO` | `/frontend` | 2026-04-18 |
| [BUG-014](BUG-014-empty-line-after-hidden-conditional-inline.md) | Пустая строка после скрытого условного inline-текста на /fill | bug | `TODO` | `/frontend` | 2026-04-18 |
| [TASK-021](TASK-021-deduplicate-block-condition-type.md) | Дедупликация интерфейса BlockCondition | refactor | `TODO` | `/frontend` | 2026-04-18 |
| [TASK-022](TASK-022-yjs-reset-api-endpoint.md) | Backend API для сброса Yjs-состояния документа | feature | `TODO` | `/backend` | 2026-04-18 |
| [TASK-023](TASK-023-condition-groups.md) | Логические группы условий (скобки) — эпик (2 подзадачи) | feature | `TODO` | `/backend`, `/frontend` | 2026-04-18 |
| [TASK-023-1](TASK-023-1-condition-node-backend.md) | Backend: ConditionNode + рекурсивный evaluator | feature | `TODO` | `/backend` | 2026-04-18 |
| [TASK-023-2](TASK-023-2-condition-builder-groups-frontend.md) | Frontend: ConditionBuilder с вложенными группами AND/OR | feature | `TODO` | `/frontend` | 2026-04-18 |
| [TASK-025](TASK-025-directory-immutability-stale-highlight.md) | Иммутабельность справочников в документах + подсветка устаревших (2 подзадачи) | feature | `TODO` | `/backend`, `/frontend` | 2026-04-18 |
| [TASK-025-1](TASK-025-1-directory-snapshot-backend.md) | Backend: снимок ответов справочника при генерации + API staleness | feature | `TODO` | `/backend` | 2026-04-18 |
| [TASK-025-2](TASK-025-2-stale-highlight-frontend.md) | Frontend: подсветка устаревших полей справочника + применение обновлений | feature | `TODO` | `/frontend` | 2026-04-18 |
| [BUG-015](BUG-015-table-inserted-with-header-row.md) | Таблица вставляется с заливкой первой строки (withHeaderRow: true) | bug | `TODO` | `/frontend` | 2026-04-18 |
| [TASK-026](TASK-026-two-column-layout-table.md) | Кнопка «Две колонки» — таблица 2×1 без заливки и границ | feature | `TODO` | `/frontend` | 2026-04-18 |
| [TASK-027](TASK-027-ui-visual-usability-improvements.md) | Улучшение визуального интерфейса и удобства использования (эпик, 2 подзадачи) | feature | `TODO` | `/frontend` | 2026-04-18 |
| [TASK-027-1](TASK-027-1-ui-audit.md) | UX-аудит: инвентаризация проблем интерфейса | research | `DONE` | `/frontend` | 2026-04-18 |
| [TASK-027-2](TASK-027-2-ui-improvements.md) | Внедрение визуальных улучшений и UX-доработок | feature | `DONE` | `/frontend` | 2026-04-18 |
| [TASK-028](TASK-028-migrate-e2e-to-playwright.md) | Миграция E2E-тестов с Selenium (Java) на Playwright (TypeScript) | refactor | `TODO` | `/frontend`, `/selenium` | 2026-04-18 |
| [TASK-029](TASK-029-parameters-and-clause-refs.md) | Параметры и ссылки на пункты в формулировках (эпик, 2 подзадачи) | feature | `TODO` | `/backend`, `/frontend` | 2026-04-18 |
| [TASK-029-1](TASK-029-1-parameters-backend.md) | Backend: хранение и резолвинг параметров и ссылок при экспорте | feature | `DONE` | `/backend` | 2026-04-23 |
| [TASK-029-2](TASK-029-2-parameters-frontend.md) | Frontend: TipTap-расширения TemplateParam и ClauseRef | feature | `DONE` | `/frontend` | 2026-04-18 |
| [TASK-030](TASK-030-condition-builder-promise-leak-fixes.md) | ConditionBuilder: утечка промиса, stale state, silent catch (ревью 024-2) | refactor | `DONE` | `/frontend` | 2026-04-18 |
| [TASK-031](TASK-031-bug003-review-fixes.md) | Доработки по ревью BUG-003: description в Popconfirm и тесты TemplateList | refactor | `TODO` | `/frontend` | 2026-04-18 |
| [BUG-016](BUG-016-dirty-flag-after-publish.md) | «Есть несохранённые изменения» появляется после публикации шаблона | bug | `DONE` | `/frontend` | 2026-04-18 |
| [TASK-032](TASK-032-questionnaire-test-quality.md) | Улучшение качества тестов QuestionnairePage (ревью BUG-006) | refactor | `TODO` | `/frontend` | 2026-04-19 |
| [TASK-033](TASK-033-participants-panel-test-avatar-count.md) | ParticipantsPanel — добавить проверку количества аватаров в тест | refactor | `TODO` | `/frontend` | 2026-04-19 |
| [BUG-017](BUG-017-pdf-empty-numbered-clause.md) | PdfExportServiceImpl — пустой numberedClause рендерит пустой `<p>` | bug | `TODO` | `/backend` | 2026-04-19 |
| [BUG-018](BUG-018-export-missing-params-pdf-font.md) | Экспорт без параметров в DOCX; символы # в PDF из-за отсутствия шрифта | bug | `DONE` | `/backend` | 2026-04-19 |
| [BUG-019](BUG-019-conditional-blocks-visible-in-export.md) | Скрытые по условию блоки попадают в DOCX и PDF при экспорте | bug | `TODO` | `/backend` | 2026-04-19 |
| [BUG-020](BUG-020-yanswers-init-before-ws-sync.md) | yAnswers инициализируется из REST до завершения WS-синхронизации | bug | `TODO` | `/frontend` | 2026-04-20 |
| [TASK-034](TASK-034-bug010-review-fixes.md) | Доработки по ревью BUG-010 — abort, тест-изоляция, transact, delete-handling | refactor | `TODO` | `/frontend` | 2026-04-20 |
| [BUG-021](BUG-021-apply-answers-npe-null-type.md) | NPE в applyAnswers — switch на null type узла (ревью 16f5208) | bug | `TODO` | `/backend` | 2026-04-23 |
| [TASK-035](TASK-035-template-param-formatting.md) | Форматирование значений templateParam через ParameterSubstitutor | feature | `TODO` | `/backend` | 2026-04-23 |
| [TASK-039](TASK-039-new-design-handoff.md) | Имплементация нового дизайна DocForge (handoff, 6 подзадач) | feature | `TODO` | `/frontend` | 2026-04-25 |
| [TASK-039-1](TASK-039-1-design-system-css.md) | Дизайн-система: styles.css, CSS-переменные, шрифты | feature | `TODO` | `/frontend` | 2026-04-25 |
| [TASK-039-2](TASK-039-2-shell-layout.md) | AppLayout: новый Topbar (nav-pills) + collapsible Sidebar | feature | `TODO` | `/frontend` | 2026-04-25 |
| [TASK-039-3](TASK-039-3-simple-screens.md) | Простые экраны: Login, Home, Templates, Directories, DirectoryDetail | feature | `TODO` | `/frontend` | 2026-04-25 |
| [TASK-039-4](TASK-039-4-template-designer.md) | Template Designer: toolbar, resizable side panel, scrollable tabs | feature | `TODO` | `/frontend` | 2026-04-25 |
| [TASK-039-5](TASK-039-5-documents-page.md) | Documents page: дерево папок, список/плитка, bulk-actions | feature | `TODO` | `/frontend` | 2026-04-25 |
| [TASK-039-6](TASK-039-6-workspace-modals.md) | Workspace: новый layout + модальные окна и drawer-панели | feature | `TODO` | `/frontend` | 2026-04-25 |

---

## Выполненные задачи

| ID | Название | Тип | Коммит | Дата |
|---|---|---|---|---|
| [BUG-010](BUG-010-answers-not-synced-realtime.md) | Ответы и опросный лист не синхронизируются в реальном времени | bug | `d251868` | 2026-04-18 |
| [TASK-005](TASK-005-block-collapse-in-published-mode.md) | Заблокировать collapse ConditionalBlock в published-режиме | refactor | `eba8b87` | 2026-04-18 |
| [TASK-004](TASK-004-frontend-review-fixes.md) | Frontend: отключить drag в readOnly, fix useEffect deps | refactor | `369c70e` | 2026-04-16 |
| [TASK-024](TASK-024-directory-fields-in-condition-builder.md) | Поля справочников в конструкторе условий (эпик, 2 подзадачи) | feature | `37713fc, 5588f0c` | 2026-04-18 |
| [TASK-024-1](TASK-024-1-directory-condition-evaluator.md) | Backend: проверка и тест ConditionEvaluator для полей справочников | feature | `37713fc` | 2026-04-18 |
| [TASK-024-2](TASK-024-2-directory-fields-condition-builder.md) | Frontend: поля справочников в ConditionBuilder | feature | `5588f0c` | 2026-04-18 |
| [BUG-004](BUG-004-published-header-keyboard-controls.md) | Хедер и Ctrl+F не адаптированы к published-режиму | bug | `5e12ac4` | 2026-04-16 |
| [BUG-003](BUG-003-ambiguous-publish-messages.md) | Неоднозначные сообщения при публикации/снятии шаблона | bug | `d5a2b46` | 2026-04-18 |
| [TASK-006](TASK-006-fill-page-feature-parity.md) | Полный паритет /fill с /edit — эпик (3 подзадачи) | feature | `—` | 2026-04-18 |
| [TASK-006-1](TASK-006-1-fill-save-body.md) | Загрузка current_body + сохранение тела документа на /fill | feature | `b83e912` | 2026-04-16 |
| [TASK-006-2](TASK-006-2-fill-yjs-collab.md) | Yjs-коллаборация в DocumentWorkspace | feature | `a4c273c` | 2026-04-16 |
| [TASK-006-3](TASK-006-3-deprecate-edit-route.md) | Удаление маршрута /edit | refactor | `adbeb8a` | 2026-04-18 |
| [BUG-005](BUG-005-revision-history-empty.md) | Кнопка «История ревизий» не видна; ревизии не создаются при сохранении | bug | `afe0b78, 0e2a25a` | 2026-04-17 |
| [BUG-001](BUG-001-template-versioning-snapshot.md) | Версионность шаблонов не работала: снимки не создавались, документы использовали живой шаблон | bug | `d87735d` | 2026-04-16 |
| [BUG-002](BUG-002-toolbar-edits-published-template.md) | Тулбары применяют правки к опубликованному шаблону | bug | `8ed5602` | 2026-04-16 |
| [TASK-003](TASK-003-backend-review-fixes.md) | Backend: guard delete(), рус. сообщение, типизированный IT-тест | refactor | `90268c5` | 2026-04-16 |
| [TASK-002](TASK-002-readonly-published-template.md) | Блокировка редактирования опубликованного шаблона | feature | `57539c4, f5f731a` | 2026-04-16 |

---

## Правила работы с задачами

1. **Новая задача** — создай файл по шаблону до начала работы.
2. **Декомпозиция** — если задача сложная (>3 файлов или >2 агентов), разбей на подзадачи (`TASK-001-1.md`, `TASK-001-2.md`).
3. **Перед отчётом** — обязательно: тесты пройдены + ручной чеклист + коммит создан.
4. **После коммита** — обнови статус в этом файле и в файле задачи.
