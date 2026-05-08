# TASK-008: QuestionnairePage — исправления по результатам ревью

| Поле | Значение |
|---|---|
| **Дата** | 2026-04-17 |
| **Тип** | `refactor` |
| **Статус** | `TODO` |
| **Агенты** | `/frontend` |
| **Коммит** | — |
| **Зависит от** | BUG-005 (DONE) |
| **GitHub Issue** | [#8](https://github.com/NKamaev/DocForge/issues/8) |

---

## Постановка задачи (от пользователя)

> Замечания из ревью кода BUG-005 — WARNING и SUGGESTION уровня.

---

## Интерпретация

По итогам ревью BUG-005 выявлены 2 WARNING и 2 SUGGESTION в `QuestionnairePage.tsx` и `documentsApi.ts`, требующие исправления:

1. **WARNING** — Обработчики DOCX/PDF объявлены inline в JSX как анонимные async-функции. Пересоздаются при каждом рендере. Нужно вынести в `useCallback`.
2. **WARNING** — Недостаточное тестовое покрытие: нет тестов на `openHistory()`, `restoreRevision()`, и путь ошибки `saveRevision`.
3. **SUGGESTION** — При ошибке `saveRevision` (fire-and-forget) пользователь не получает UI-сигнала. Нужен `notification.warning` при систематических сбоях.
4. **SUGGESTION** — `documentsApi.getRevision` возвращает нетипизированный `unknown`. Нужно добавить `RevisionMeta` как тип ответа.

---

## Файлы для изучения перед исполнением

```
frontend/src/components/questionnaire/QuestionnairePage.tsx  — обработчики экспорта (строки 326–369), scheduleSave (строка 93)
frontend/src/api/documentsApi.ts                             — getRevision (строка 50)
frontend/src/components/questionnaire/QuestionnairePage.test.tsx — существующие тесты
```

---

## Файлы для изменения

```
frontend/src/components/questionnaire/QuestionnairePage.tsx
  — вынести обработчики exportDocx и exportPdf в useCallback
  — добавить notification.warning при повторной ошибке saveRevision (опционально: счётчик последовательных ошибок)

frontend/src/api/documentsApi.ts
  — getRevision: добавить дженерик <RevisionMeta>

frontend/src/components/questionnaire/QuestionnairePage.test.tsx
  — добавить тест: openHistory загружает список ревизий (состояние loadingRevisions)
  — добавить тест: saveRevision ошибка — console.warn, setSaveError не вызывается
  — добавить тест: restoreRevision вызывает documentsApi.restoreRevision + window.location.reload
```

---

## Поэтапное исполнение

1. **`documentsApi.ts`**: типизировать `getRevision` → `api.get<RevisionMeta>(...)`.
2. **`QuestionnairePage.tsx`**: вынести `exportDocx`-обработчик в `useCallback` по образцу `openHistory`.
3. **`QuestionnairePage.tsx`**: вынести `exportPdf`-обработчик в `useCallback`.
4. **`QuestionnairePage.tsx`**: в `scheduleSave` — при ошибке `saveRevision` показывать `notification.warning` (не более одного раза подряд, чтобы не спамить).
5. **Тесты** — добавить 3 новых теста в `QuestionnairePage.test.tsx`.
6. Запустить `npm test -- --run`, убедиться все 4+ тестов зелёные.

---

## Автоматические тесты

### Что покрыть

| Сценарий | Тип теста | Файл |
|---|---|---|
| `openHistory` загружает список, выставляет loadingRevisions=true/false | unit | `QuestionnairePage.test.tsx` |
| `saveRevision` падает с ошибкой → `console.warn`, `setSaveError` не вызывается | unit | `QuestionnairePage.test.tsx` |
| `restoreRevision` вызывает `documentsApi.restoreRevision` + `window.location.reload` | unit | `QuestionnairePage.test.tsx` |

### Команды запуска

```bash
cd frontend && npm test -- --run
```

---

## Чеклист ручного тестирования

- [ ] DOCX/PDF кнопки работают (нет регрессии после вынесения в useCallback)
- [ ] При ошибке saveRevision (можно эмулировать выключив backend) — появляется notification.warning
- [ ] Регрессия: история ревизий, автосохранение работают корректно

---

## История

| Дата | Событие |
|---|---|
| 2026-04-17 | Задача создана по результатам ревью BUG-005 (WARNING × 2, SUGGESTION × 2) |

---

## Завершение задачи

> **Обязательно выполнить перед отчётом о выполнении!**

- [ ] Автотесты запущены и проходят (`npm test`)
- [ ] Ручной чеклист выполнен полностью
- [ ] Код прошёл ревью (`/review`)
- [ ] Git-коммит создан: `refactor(document): QuestionnairePage review fixes — useCallback, tests, typing`
- [ ] Статус обновлён: DONE
- [ ] Реестр обновлён: docs/tasks/README.md
