# Извлечённые уроки

> Тег: `practices:lessons` · Обновлено: `2026-10-12`

## [2026-10-12] Уроки TASK-064 (structural rich-diff)

- **Схема ProseMirror — не мягкая:** контейнеры (`bulletList`/`table`/`tableRow`) ограничивают тип детей. Проверяй вывод в тестах через `getSchema(ext)` + `PMNode.fromJSON(schema, doc).check()` — TipTap молча рендерит невалидное. (Детали — `pitfalls:tiptap-react`.)
- **Stray `.js` из отменённого агента:** отменённый транспайл-воркер эмитнул 148 `.js` рядом с `.ts/.tsx`; Vite резолвит `.js` **раньше** `.tsx` → затенение исходников. Проблема не видна в `git status`, если директория untracked (показывает строку каталога, не файлов) — проверяй `find frontend/src -name '*.js'`.
- **`buildWorkspaceExtensions` (TASK-064)** — единый источник списка расширений workspace; опции `{ collaboration?, diff? }`. Diff-рендер и рабочий редактор используют один билдер.

## [2026-10-10 10:00] Сводка по багам (из архива задач и git-истории DocForge)

- **REST vs Yjs — всегда гонка**: инициализация ответов из REST должна дожидаться WS-sync (BUG-020), иначе перезапись актуального состояния.
- **Два места рендера условий** (генерация + экспорт) — расхождение порождало BUG-019; держи единый фильтр.
- **Одно state-хранилище на ответы** — локальный React-state вместо Yjs-канала давал BUG-010; канал `yAnswers` единственный.
- **NodeView ≠ сменяемый компонент** — несколько `return` = remount (BUG-013).
- **`setContent` без `emitUpdate:false` = холостые записи** (TASK-009).
- **Скрытие контента должно убирать и пустой контейнер** — `display:none` на span не всегда достаточно (BUG-014).
- **Async-модалки**: guard на unmount/stale + сброс данных при открытии + не глотать ошибки (TASK-030).
- **Рестарт после отката редакции** — программный Yjs-reset, а не reload страницы (BUG-046).
- **Toast реализован через CustomEvent `app-toast`** (BUG-029) — программамый путь покрыт e2e.

## Наблюдения по кодовой базе (2026-10-10)

- Ноль TODO/FIXME/HACK в коде — «умные» знания живут в архиве задач (`docs/archive/tasks/tasks/`) и github issues; благоприятно для памяти-тегов.
- Авторизационные грейды (`DocumentController` saveAnswers/generate/revisions/participants/export; весь `TemplateController`) — кандидаты на hardening.
- Дубликаты на грани риска: testcontainersVersion без зависимости, actuator permitAll без зависимости, легаси `DocumentParticipant*` рядом с `document_members`.

<!-- Новые записи добавляй СВЕРХУ. -->