# Task-tracker MCP — правила и подводные камни

> Тег: `conventions:task-tracker` · Обновлено: `2026-10-13`

## [2026-10-13 17:58] Матрица переходов и ошибки MCP-инструментов

- **Контекст:** ревизия залежавшейся REVIEW-колонки, потребовались массовые переходы через `task-tracker` MCP.
- **Матрица переходов (status):**
  - `TODO → DONE` — **запрещено**. Путь: `TODO → IN_PROGRESS → REVIEW → DONE`.
  - `REVIEW → DONE` — разрешено.
  - `IN_PROGRESS → DONE` — **запрещено** (только через `REVIEW`).
  - `REVIEW → TODO` — **запрещено**. Путь: `REVIEW → IN_PROGRESS → TODO`.
  - Разрешённые из `TODO`: `[CANCELLED, IN_PROGRESS, BLOCKED]`; из `REVIEW`: `[IN_PROGRESS, BLOCKED, DONE, CANCELLED]`.
  - `DONE → DONE` — ошибка 409 (допустимо только `DONE → REVIEW`).
- **Закрытие без коммита:** `task_transition { allow_without_commit: true }` (+ `comment` с доказательством) — используется для унаследованных задач, чья реализация вне текущего репозитория.
- **Ошибки приходят как ЗНАЧЕНИЕ, не исключение:** `task_transition`/`task_create` возвращают `{ status: 409|500, error/message }` вместо throw. Нельзя считать `resolve` успехом — проверяй `status` (у успешного ответа `status` — строка нового статуса).
- **Поле `author` не поддерживается** на `task_create` и не работает на `task_transition`: запрос падает с `Unrecognized field "author"` (`TaskCreateRequest`). Actor по умолчанию — `mcp:claude-code`.
- **Реестровая модель:** статусы задач — источник истины в task-tracker MCP; файла `docs/tasks/README.md` НЕ существует и не обновляется.
- **Источник:** практика сессии 2026-10-13 (ревизия REVIEW 25 → 0).
- **Статус:** актуально.
