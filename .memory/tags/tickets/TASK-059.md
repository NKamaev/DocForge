# TASK-059 — ShareDialog («Доступ») на странице /fill

- **Статус:** закрыто (реализовано)
- **Создан:** описано в `docs/architecture.md:5.4`, `:10.4`
- **Источники:** `docs/architecture.md`, explore frontend report

## Суть

Компонент `ShareDialog` (RBAC-гранты) был подключён на `DocumentsPage`, но отсутствовал в тулбаре `/fill`.

## Решение

## [2026-10-10 10:00] Подтверждено в коде

- Кнопка «Доступ» (Share2) в тулбаре `QuestionnairePage` (`src/components/questionnaire/QuestionnairePage.tsx:481-483`), диалог смонтирован (:612-619).
- `types/member.ts` — `roleImplies()`; роли/источники см. тег `architecture:security-rbac`.

<!-- История ниже -->