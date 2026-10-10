# TASK-006 — Параритет страницы /fill (save body, current_body, Yjs, ShareDialog)

- **Статус:** реализовано (в коде)
- **Создан:** 2026-04 (реконструкция 2026-10-10)
- **Источники:** `docs/architecture.md:5.4`, подзадачи `TASK-006-1/2/3`, git log

## Суть

Страница `/fill` (QuestionnairePage) отставала от полного workflow: не сохраняла свободные правки тела, не грузила `current_body`, не имела Yjs-коллаборации. В вариации TASK-059 — отсутствовала кнопка «Доступ» (ShareDialog).

## Решение (по коду)

- Body: автосейв 3s (`updateBody`) + revision при 60s idle + `saveOnExit()` (`keepalive:true`); `DocumentWorkspace` сидит на `currentBody` (`initialBody` guard).
- Коллаборация: `Collaboration.configure({document: ydoc})` активна; seeding guard 400ms WS-sync.
- TASK-059: ShareDialog добавлен в тулбар `/fill` (`QuestionnairePage.tsx:481-483, 612-619`).
- Затронутые файлы: `frontend/src/components/questionnaire/QuestionnairePage.tsx`, `DocumentWorkspace.tsx`, `hooks/useCollaboration.ts`.
- TODO: TASK-007 (retention редакций) — отдельно, открыт.

<!-- История ниже -->