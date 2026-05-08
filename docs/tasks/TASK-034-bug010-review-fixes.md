# TASK-034: Доработки по ревью BUG-010 — abort, тест-изоляция, transact, delete-handling

| Поле | Значение |
|---|---|
| **Дата** | 2026-04-20 |
| **Тип** | `refactor` |
| **Статус** | `TODO` |
| **Агенты** | `/frontend` |
| **Коммит** | — |
| **Зависит от** | — |
| **GitHub Issue** | [#68](https://github.com/NKamaev/DocForge/issues/68) |

---

## Постановка задачи (от пользователя)

> Из ревью BUG-010 (WARNING #2, #3, #4 и SUGGESTION #5–8).

```
По результатам код-ревью BUG-010 выявлено несколько качественных проблем:
нет отмены in-flight getQuestionnaire при размонтировании, состояние yAnswers
не сбрасывается между тестами, пропущена зависимость в useEffect, нет дебаунса
на рефетч, множественные yAnswers.set без transact, удалённые ключи
устанавливаются в undefined, JSDoc устарел.
```

---

## Интерпретация

Набор небольших качественных улучшений в одном файле и тест-файле после
реализации BUG-010. Не затрагивает архитектуру — только корректность,
надёжность и документацию.

---

## Файлы для изучения перед исполнением

```
frontend/src/components/questionnaire/QuestionnairePage.tsx
frontend/src/components/questionnaire/QuestionnairePage.test.tsx
frontend/src/hooks/useCollaboration.ts
```

---

## Файлы для изменения

```
frontend/src/components/questionnaire/QuestionnairePage.tsx  — WARNING #2, #4, SUGGESTION #5, #6, #7
frontend/src/components/questionnaire/QuestionnairePage.test.tsx — WARNING #3
frontend/src/hooks/useCollaboration.ts                       — SUGGESTION #8 (JSDoc)
```

---

## Поэтапное исполнение

### Шаг 1 — Abort in-flight `getQuestionnaire` при размонтировании (WARNING #2)

В `useEffect` remote-обсервера добавить ignore-флаг:

```ts
useEffect(() => {
  let cancelled = false
  const handler = (event: Y.YMapEvent<unknown>) => {
    if (event.transaction.local) return
    // ... обновление answersRef / setAnswers ...
    if (id) {
      documentsApi.getQuestionnaire(id)
        .then(q => { if (!cancelled) setQuestionnaire(q) })
        .catch(() => {})
    }
  }
  yAnswers.observe(handler)
  return () => { cancelled = true; yAnswers.unobserve(handler) }
}, [yAnswers, id])
```

### Шаг 2 — Добавить `yAnswers` в deps `useEffect([id])` (WARNING #4)

```ts
}, [id, yAnswers])
```

### Шаг 3 — Дебаунс `getQuestionnaire` в remote handler (SUGGESTION #5)

Добавить `remoteQTimer = useRef(null)` и оборачивать вызов `getQuestionnaire`
в `setTimeout/clearTimeout` аналогично `scheduleSave`:

```ts
const remoteQTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
// в handler:
if (remoteQTimerRef.current) clearTimeout(remoteQTimerRef.current)
remoteQTimerRef.current = setTimeout(() => {
  if (!cancelled && id) {
    documentsApi.getQuestionnaire(id)
      .then(q => { if (!cancelled) setQuestionnaire(q) })
      .catch(() => {})
  }
}, DEBOUNCE_MS)
```

### Шаг 4 — Обернуть multiple `yAnswers.set` в `ydoc.transact` (SUGGESTION #6)

```ts
// handleMultiAnswerChange:
ydoc.transact(() => {
  Object.entries(changes).forEach(([k, v]) => yAnswers.set(k, v))
})
```

### Шаг 5 — Обработать удалённые ключи в remote handler (SUGGESTION #7)

```ts
event.changes.keys.forEach((change, key) => {
  if (change.action === 'delete') {
    delete remote[key]  // или явно исключить из next
  } else {
    remote[key] = yAnswers.get(key)
  }
})
// Для delete-ключей удалять из answersRef и state
```

### Шаг 6 — Изолировать состояние yAnswers между тестами (WARNING #3)

В `beforeEach` тест-файла:
```ts
getSharedYAnswers().clear()
```

### Шаг 7 — Обновить JSDoc в `useCollaboration.ts` (SUGGESTION #8)

Добавить `yAnswers` в перечень возвращаемых полей в комментарии.

---

## Автоматические тесты

### Что покрыть

| Сценарий | Тип теста | Файл |
|---|---|---|
| Размонтирование до резолва `getQuestionnaire` не вызывает setState | unit | `QuestionnairePage.test.tsx` |
| `yAnswers.clear()` в `beforeEach` изолирует состояние между тестами | (структурно — убедиться что тесты не завязаны на порядок) | `QuestionnairePage.test.tsx` |
| delete-action не добавляет `undefined` в answers state | unit | `QuestionnairePage.test.tsx` |

### Команды запуска

```bash
cd frontend && npm test -- --run src/components/questionnaire/QuestionnairePage.test.tsx
cd frontend && npx tsc --noEmit
```

---

## Чеклист ручного тестирования

- [ ] Быстрый ввод нескольких ответов подряд — нет избыточных API-вызовов в DevTools Network
- [ ] Переход со страницы во время загрузки — нет предупреждений React в консоли
- [ ] Регрессия: синхронизация ответов между вкладками работает

---

## История

| Дата | Событие |
|---|---|
| 2026-04-20 | Задача создана по результатам ревью BUG-010 |

---

## Завершение задачи

> **Обязательно выполнить перед отчётом о выполнении!**

- [ ] Автотесты запущены и проходят (`npm test`)
- [ ] Ручной чеклист выполнен полностью
- [ ] Код прошёл ревью (`/review`)
- [ ] Git-коммит создан с сообщением: `refactor(collab): BUG-010 review fixes`
- [ ] Статус обновлён в этом файле: `DONE`
- [ ] Реестр обновлён: [docs/tasks/README.md](README.md)
- [ ] GitHub Issue закрыт (state: closed) + комментарий с хешем коммита
