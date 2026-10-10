# Архитектура проекта — обзор

> Тег: `architecture:overview` · Обновлено: `2026-10-10`

## [2026-10-10 10:00] Модульный монолит DocForge-Next

- **Контекст:** первый бутстрап памяти, анализ кодовой базы.
- **Факт:** Spring Boot 3.3 + Java 21 (virtual threads) + React 18/TS. Модули в `backend/src/main/java/com/docforge/`: `user`, `template`, `document`, `collaboration`, `directory`, `export`, `audit`, `upload`, `feedback`, `security`, `common`. Модули общаются ТОЛЬКО через `*Service` интерфейсы, импорт чужого Repository запрещён (`docs/specs/layers.md`).
- **Слои backend:** controller (тонкий) → service → repository + model; domain/ для чистой бизнес-логики.
- **Слои frontend:** `api/`, `components/`, `store/` (Zustand), `types/`. Слой `domain/` в AGENTS.md заявлен, но в коде отсутствует.
- **Источник:** `docs/architecture.md:2.1`, `docs/specs/layers.md`, `AGENTS.md`.

## Модули и ключевые классы (краткая карта)

| Модуль | Ключевые классы | Ответственность |
|--------|-----------------|-----------------|
| user | `UserController`, `UserServiceImpl`, `User` (JPA) | auth, JWT login/register, `/api/users/me`, search |
| template | `TemplateController`, `TemplateService(Iml)`, `Template` (Mongo), `TemplateRegistry`+`TemplateVersion` (JPA) | CRUD, publish/unpublish, семантические версии, validate, скрипты |
| document | `DocumentController`, `DocumentServiceImpl` (679 строк), `Document` (Mongo), `DocumentRegistry` (JPA) | questionnaire visibility, answers, generate, body, revisions, folders, members |
| collaboration | `YjsWebSocketHandler`, `YjsDocumentStoreImpl`, `LocalCollaborationBroadcaster`, `Varint` | Yjs WS, слой хранения с debounce, JWT handshake, бинарный протокол lib0 |
| directory | `DirectoryController`, `DirectoryServiceImpl`, `DirectoryRegistry`/`DirectorySchemaField` (JPA) + `DirectoryDocument` (Mongo) | справочники: реестр+схема (PG), записи (Mongo) |
| export | `DocxExportServiceImpl` (POI), `PdfExportServiceImpl` (openhtmltopdf), `NodeResolver`, `DocxConverter` | экспорт DOCX/PDF |
| audit | `AuditServiceImpl` (@Async) | запись audit_log |
| upload | `LocalImageStorageService` | хранение изображений, guard от path traversal |
| feedback | `FeedbackController` | приём отчётов → audit |

**@Lazy-цикл:** `TemplateServiceImpl` ↔ `DocumentServiceImpl` (`TemplateServiceImpl.java:59-62`) — документированный, но хрупкий паттерн.
- **Источник:** explore backend report, `docs/specs/contracts/service-contracts.md`.

## Известные архитектурные риски (на 2026-10-10)

- `DocumentController` — часть эндпоинтов без permission-check: `saveAnswers` (:137-144), `generate` (:149-152), revisions (:225-267), participants (:271-296), document export (:170-221). `TemplateController` — вообще без owner-проверок.
- `ConditionEvaluator`: AND/OR вычисляются слева-направо **без приоритета операторов** (`ConditionEvaluator.java:54-59`).
- `testcontainersVersion` объявлен, но зависимости Testcontainers нет; тесты читают docker-compose контейнеры.
- `/actuator/health` открыт в SecurityConfig, но actuator на classpath отсутствует.
- Легаси-классы `DocumentParticipant`/`DocumentParticipantRepository` остались рядом с `document_members`.
- **Источник:** explore backend report, `backend/src/main/java/com/docforge/document/DocumentController.java`.

<!-- Новые записи добавляй СВЕРХУ. -->