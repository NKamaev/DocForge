# TASK-014: Frontend-исправления по ревью TASK-006-2

| Поле | Значение |
|---|---|
| **Дата** | 2026-04-18 |
| **Тип** | `refactor` |
| **Статус** | `TODO` |
| **Агенты** | `/frontend` |
| **Коммит** | — |
| **Зависит от** | — |
| **GitHub Issue** | [#17](https://github.com/NKamaev/DocForge/issues/17) |

---

## Постановка задачи

Код ревью TASK-006-2 выявил несколько WARNING/SUGGESTION во frontend-файлах коллаборации: дублирование `loginToColor`, лишний cast, inline async в JSX, `id ?? ''` без guard в хуке.

---

## Интерпретация

Устранить замечания ревью, не затрагивая логику коллаборации:
- Дедуплицировать `loginToColor` (сейчас в двух файлах)
- Убрать лишний `as CollaborationUser[]` в QuestionnairePage
- Вынести DOCX/PDF экспорт-обработчики из JSX в `useCallback`
- Добавить guard для `id === undefined` при вызове `useCollaboration`

---

## Файлы для изменения

```
frontend/src/hooks/useCollaboration.ts
  — экспортировать loginToColor (убрать дублирование)

frontend/src/components/questionnaire/QuestionnairePage.tsx
  — импортировать loginToColor из useCollaboration.ts
  — убрать локальную копию loginToColor
  — убрать лишний cast `as CollaborationUser[]`
  — вынести handleExportDocx и handleExportPdf в useCallback
  — добавить guard: если !id, не вызывать useCollaboration (или передавать null с guard в хуке)
```

---

## Поэтапное исполнение

1. В `useCollaboration.ts` сделать `loginToColor` экспортируемой (`export function loginToColor`).
2. В `QuestionnairePage.tsx`:
   - импортировать `loginToColor` из `useCollaboration`
   - удалить локальную функцию `loginToColor` (строки 456-462)
   - убрать `as CollaborationUser[]` (строка 331)
   - вынести inline-обработчики DOCX (строки 346-363) и PDF (строки 370-393) в именованные `useCallback`
3. Добавить guard для `id`:
   ```ts
   // вариант A: в хуке
   export function useCollaboration(documentId: string | null): CollaborationState { ... }
   // вариант B: ранняя проверка в QuestionnairePage
   ```

---

## Автоматические тесты

| Сценарий | Тип теста | Файл |
|---|---|---|
| `loginToColor` возвращает одинаковый цвет для одного логина | unit | `frontend/src/hooks/useCollaboration.test.ts` |

### Команды запуска

```bash
cd frontend && npm test -- --run
```

---

## Чеклист ручного тестирования

- [ ] `/fill/{id}` открывается, коллаборация работает, ParticipantsPanel отображает цвета
- [ ] Экспорт DOCX и PDF работают корректно
- [ ] Регрессия: участники в двух вкладках видят друг друга

---

## История

| Дата | Событие |
|---|---|
| 2026-04-18 | Задача создана по результатам код ревью TASK-006-2 |

---

## Завершение задачи

> **Обязательно выполнить перед отчётом о выполнении!**

- [ ] Автотесты запущены и проходят (`npm test`)
- [ ] Ручной чеклист выполнен полностью
- [ ] Код прошёл ревью (`/review`)
- [ ] Git-коммит создан с сообщением: `refactor(collab): deduplicate loginToColor, extract export callbacks`
- [ ] Статус обновлён в этом файле: `DONE`
- [ ] Реестр обновлён: [docs/tasks/README.md](README.md)
