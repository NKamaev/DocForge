# TASK-019 — Отображение изменений в тексте документа (diff редакций)

- **Статус:** DONE (реализовано)
- **Создан:** 2026-04-18
- **Завершён:** 2026-10-10
- **Источники:** task-tracker MCP (TASK-019 + подзадачи), git log
- **Коммиты:** `78e9909` (backend API), `79c6cdc` (frontend DiffViewer)

## Суть

Пользователь должен видеть, что изменилось в тексте между редакциями документа.
Удалённый текст — красным зачёркиванием, добавленный — зелёным подчёркиванием.

## Декомпозиция

| Подзадача | Что | Статус | Коммит |
|---|---|---|---|
| TASK-019-1 | Backend: API diff между редакциями | DONE | `78e9909` |
| TASK-019-2 | Frontend: DiffViewer + кнопка в истории | DONE | `79c6cdc` |

## Решение

- **Backend** (`java-diff-utils`): `GET /api/documents/{id}/revisions/{revNum}/diff?compareTo=N`
  - `DocumentServiceImpl.computeRevisionDiff` (~566) → `List<DiffChunk>` (`INSERT`/`DELETE`/`EQUAL`), `computeRevisionDiff` нормализует `CHANGE` в DELETE+INSERT; текст извлекается `extractTextRecursive` (~648) по TipTap-дереву.
  - DTO: `document/dto/DiffChunk.java`, `RevisionDiffResponse.java`.
  - Permission: `DocumentPermission.READ` в `DocumentController` (`getRevisionDiff`, ~259).
- **Frontend**: `frontend/src/components/questionnaire/DiffViewer.tsx`
  - `DiffViewer` рендерит chunks: `INSERT`→`<ins>` (зелёный + `underline`), `DELETE`→`<del>` (красный + зачёркивание), `EQUAL` — обычный текст; `data-chunk-type` для тестов.
  - `RevisionDiffDialog` — проп `previousRevisionNumber: number | null`; baseline по умолчанию — предыдущая редакция (зелёный = добавлено в N), тумблер «С текущим состоянием» (compareTo=undefined).
  - Кнопка «Изменения» (иконка `GitCompare`, `aria-label`/`title`) в drawer истории `QuestionnairePage.tsx`; baseline вычисляется из `listRevisions` (~373).
  - API: `documentsApi.getRevisionDiff(id, revNum, compareTo?)` (`api/documentsApi.ts`), типы `DiffChunk`/`RevisionDiff` в `types/document.ts`.

## Тесты

- Backend: `DocumentServiceDiffTest` (10) + `DocumentControllerDiffTest` (4, MockMvc slice: 200/404/403).
- Frontend: `DiffViewer.test.tsx` + `QuestionnairePage.test.tsx` (18).

## Ограничения / follow-up

- **BUG-048** — соседние revision-эндпоинты (`GET /revisions`, `GET /revisions/{n}`, `restore`) и `PUT /answers`, `POST /generate` не имеют permission-чека (diff-эндпоинт получил).
- **TASK-062** — diff не отражает изменения значений параметров (они в `answers`, не в теле документа).
- **TASK-063** — полировка: направление baseline «текущее» (инвертированная семантика цвета), Playwright E2E, guard, точность диффа, дубли `data-testid`.
- Ручной E2E-чеклист эпика вынесен в TASK-063 (Playwright-сценарий не написан).

<!-- История ниже -->
