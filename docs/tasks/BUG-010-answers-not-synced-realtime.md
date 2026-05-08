# BUG-010: Ответы и опросный лист не синхронизируются в реальном времени

| Поле | Значение |
|---|---|
| **Дата** | 2026-04-18 |
| **Тип** | `bug` |
| **Статус** | `DONE` |
| **Агенты** | `/frontend` |
| **Коммит** | `d251868` |
| **Зависит от** | — |
| **GitHub Issue** | [#21](https://github.com/NKamaev/DocForge/issues/21) |

---

## Постановка задачи (от пользователя)

> Verbatim — точная формулировка пользователя.

```
При одновременной работе с документами изменения в тексте синхронизируются
сразу же (кроме чипов со значением параметра), опросный лист и чипы
с параметрами синхронизируются только после обновления страницы
```

---

## Интерпретация

Два пользователя открывают один документ одновременно на `/fill`. Текст
синхронизируется через Yjs/WebSocket. Однако ответы на вопросы опросного
листа хранятся в локальном React-стейте каждого пользователя — когда
Пользователь A меняет ответ, Пользователь B видит изменение только после
перезагрузки страницы. Это порождает два видимых симптома:

1. **Чипы параметров** (WorkspaceParameterFieldNode) не обновляются в документе
   у B, потому что их значения берутся из `storage.answers` → `answers` prop →
   локальный React state.
2. **Опросный лист** у B не отражает изменения (новые ответы, изменения
   видимости вопросов).

---

## Причина ошибки

**Корень проблемы:**  
`answers` — локальный `useState` в `QuestionnairePage`. Он обновляется только
при действиях текущего пользователя. Нет механизма получения изменений от
других пользователей в реальном времени.

**Цепочка сбоя:**
1. Пользователь A вводит ответ → `handleAnswerChange` → `setAnswers(next)` +
   `scheduleSave` (REST PUT /api/documents/{id}/answers).
2. REST-ответ содержит обновлённый `questionnaire` (с пересчитанной видимостью)
   — обновляется только у A.
3. У B `answers` state не меняется → чипы показывают старые значения,
   опросный лист не обновляется.
4. Единственный способ для B увидеть изменения — `window.location.reload()`.

**Почему текст синхронизируется, а ответы нет:**  
Текст документа живёт в `ydoc` (Yjs XML-фрагмент `"default"`) и
распространяется через WebSocket-провайдер автоматически. Ответы же хранятся
только в REST и локальном state, Yjs о них ничего не знает.

---

## Файлы для изучения перед исполнением

```
frontend/src/hooks/useCollaboration.ts         — ydoc, provider, awareness
frontend/src/components/questionnaire/QuestionnairePage.tsx
                                               — answers state, scheduleSave, handleAnswerChange
frontend/src/components/questionnaire/DocumentWorkspace.tsx
                                               — storage.answers, answers-updated dispatch
frontend/src/components/questionnaire/WorkspaceParameterFieldNode.tsx
                                               — чтение storage.answers для рендера чипа
```

---

## Файлы для изменения

```
frontend/src/hooks/useCollaboration.ts         — добавить yAnswers: Y.Map<unknown>
frontend/src/components/questionnaire/QuestionnairePage.tsx
                                               — писать в yAnswers при изменении ответа;
                                                 слушать yAnswers.observe для входящих изменений;
                                                 рефетчить questionnaire при изменении от других
```

---

## Поэтапное исполнение

### Шаг 1 — Экспонировать `yAnswers` из `useCollaboration`

В `useCollaboration.ts` добавить в возвращаемый объект:

```ts
const yAnswers = useMemo(() => ydoc.getMap<unknown>('answers'), [ydoc])
// ...
return { ydoc, provider, isConnected, connectedUsers, yAnswers }
```

`ydoc.getMap('answers')` — стабильный объект, автоматически синхронизируется
через WebSocket тем же провайдером, что и фрагмент `'default'`.

### Шаг 2 — Писать ответы в `yAnswers` при изменении

В `QuestionnairePage.tsx` получить `yAnswers` из хука. В `handleAnswerChange`
добавить после обновления `answersRef`:

```ts
yAnswers.set(paramCode, value)
```

В `handleMultiAnswerChange` — аналогично для каждого ключа:
```ts
Object.entries(changes).forEach(([k, v]) => yAnswers.set(k, v))
```

REST-сохранение (scheduleSave) остаётся как есть — для персистентности.

### Шаг 3 — Подписаться на изменения от других пользователей

В `QuestionnairePage.tsx` добавить `useEffect`:

```ts
useEffect(() => {
  const handler = (event: Y.YMapEvent<unknown>) => {
    // Только изменения от других клиентов (не от текущего)
    if (event.transaction.local) return
    const remote: Record<string, unknown> = {}
    event.changes.keys.forEach((_, key) => {
      remote[key] = yAnswers.get(key)
    })
    const next = { ...answersRef.current, ...remote }
    answersRef.current = next
    setAnswers(next)
    // Рефетч questionnaire для обновления видимости вопросов
    if (id) {
      documentsApi.getQuestionnaire(id)
        .then(q => setQuestionnaire(q))
        .catch(() => {/* ignore */})
    }
  }
  yAnswers.observe(handler)
  return () => yAnswers.unobserve(handler)
}, [yAnswers, id])
```

### Шаг 4 — Инициализация `yAnswers` из REST при первой загрузке

После загрузки `questionnaire` (в существующем `useEffect([id])`), заполнить
`yAnswers` если он пустой (нет данных от других пользователей):

```ts
// После setAnswers(initial) и answersRef.current = initial:
if (yAnswers.size === 0) {
  Object.entries(initial).forEach(([k, v]) => yAnswers.set(k, v))
}
```

Это обеспечивает, что если пользователь заходит первым, его сохранённые
ответы попадают в Yjs и становятся видны следующему.

---

## Автоматические тесты

### Что покрыть

| Сценарий | Тип теста | Файл |
|---|---|---|
| `yAnswers.observe` обновляет `answers` state при remote-изменении | unit | `QuestionnairePage.test.tsx` |
| `handleAnswerChange` пишет значение в `yAnswers` | unit | `QuestionnairePage.test.tsx` |
| `event.transaction.local === true` → нет обновления стейта | unit | `QuestionnairePage.test.tsx` |

### Команды запуска

```bash
cd frontend && npm test -- --run src/components/questionnaire/QuestionnairePage.test.tsx
```

---

## Чеклист ручного тестирования

- [ ] Открыть один документ в двух вкладках (разные пользователи или один)
- [ ] Ввести ответ в поле опросного листа в вкладке A → чип параметра
  обновляется в вкладке B **без перезагрузки**
- [ ] Изменить ответ типа Select/Boolean, влияющий на видимость вопроса →
  опросный лист в вкладке B обновляется
- [ ] Перезагрузить страницу B — ответы сохранились корректно (REST-сохранение
  работает)
- [ ] Регрессия: одиночный пользователь — всё работает как раньше

---

## История

| Дата | Событие |
|---|---|
| 2026-04-18 | Задача создана |
| 2026-04-20 | Выполнено: коммит `d251868` |
| 2026-04-20 | Ревью: создана [BUG-020](BUG-020-yanswers-init-before-ws-sync.md) (race condition init) и [TASK-034](TASK-034-bug010-review-fixes.md) (quality fixes) |

---

## Завершение задачи

> **Обязательно выполнить перед отчётом о выполнении!**

- [ ] Автотесты запущены и проходят (`npm test`)
- [ ] Ручной чеклист выполнен полностью
- [ ] Код прошёл ревью (`/review`)
- [ ] Git-коммит создан с сообщением: `fix(collab): sync answers via Yjs Y.Map`
- [ ] Статус обновлён в этом файле: `DONE`
- [ ] Реестр обновлён: [docs/tasks/README.md](README.md)
