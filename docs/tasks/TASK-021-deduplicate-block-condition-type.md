# TASK-021: Дедупликация интерфейса BlockCondition

| Поле | Значение |
|---|---|
| **Дата** | 2026-04-18 |
| **Тип** | `refactor` |
| **Статус** | `TODO` |
| **Агенты** | `/frontend` |
| **Коммит** | — |
| **Зависит от** | — |
| **GitHub Issue** | [#33](https://github.com/NKamaev/DocForge/issues/33) |

---

## Постановка задачи (от пользователя)

Найдено в ходе ревью BUG-012.

---

## Интерпретация

Интерфейс `BlockCondition` объявлен локально в трёх файлах:
- `ConditionalBlockNode.tsx`
- `WorkspaceConditionalBlockNode.tsx`
- `WorkspaceConditionalInlineNode.tsx`

При изменении структуры условия (добавление поля, переименование) потребуется обновлять три файла.

---

## Файлы для изучения перед исполнением

```
frontend/src/components/template-designer/extensions/ConditionalBlockNode.tsx
frontend/src/components/questionnaire/WorkspaceConditionalBlockNode.tsx
frontend/src/components/questionnaire/WorkspaceConditionalInlineNode.tsx
frontend/src/utils/previewRenderer.ts   — тоже содержит локальный BlockCondition
```

---

## Файлы для изменения

```
frontend/src/components/template-designer/extensions/ConditionalBlockNode.tsx  — экспортировать BlockCondition
frontend/src/components/questionnaire/WorkspaceConditionalBlockNode.tsx         — импортировать
frontend/src/components/questionnaire/WorkspaceConditionalInlineNode.tsx        — импортировать
frontend/src/utils/previewRenderer.ts                                           — импортировать или оставить локальный (тип внутренний)
```

---

## Поэтапное исполнение

1. В `ConditionalBlockNode.tsx` сделать `export interface BlockCondition`.
2. В `WorkspaceConditionalBlockNode.tsx` и `WorkspaceConditionalInlineNode.tsx` убрать локальные объявления, добавить `import type { BlockCondition } from '../template-designer/extensions/ConditionalBlockNode'`.
3. Проверить `previewRenderer.ts` — там `BlockCondition` используется как внутренний тип (`type BlockCondition`), можно оставить локальным или также импортировать.
4. `npx tsc --noEmit` — ошибок нет.

---

## Автоматические тесты

Изменение чисто структурное — существующих тестов достаточно.

---

## История

| Дата | Событие |
|---|---|
| 2026-04-18 | Задача создана по результатам ревью BUG-012 |

---

## Завершение задачи

> **Обязательно выполнить перед отчётом о выполнении!**

- [ ] Автотесты запущены и проходят (`npm test`)
- [ ] TypeScript без ошибок (`npx tsc --noEmit`)
- [ ] Код прошёл ревью (`/review`)
- [ ] Git-коммит создан: `refactor(fill): export BlockCondition from single source`
- [ ] Статус обновлён: DONE
- [ ] Реестр обновлён: docs/tasks/README.md
