# BUG-020: yAnswers инициализируется из REST до завершения WS-синхронизации

| Поле | Значение |
|---|---|
| **Дата** | 2026-04-20 |
| **Тип** | `bug` |
| **Статус** | `TODO` |
| **Агенты** | `/frontend` |
| **Коммит** | — |
| **Зависит от** | — |
| **GitHub Issue** | [#67](https://github.com/NKamaev/DocForge/issues/67) |

---

## Постановка задачи (от пользователя)

> Из ревью BUG-010 (WARNING #1).

```
Race condition: yAnswers.size === 0 проверяется в момент разрешения loadQ —
до того как WebSocket-провайдер завершил initial sync. Если пользователь B
подключается к сессии, где A уже работает: yAnswers.size === 0 (WS ещё не
синхронизировался) → B записывает свои REST-ответы → WS синхронизируется →
Yjs merges по CRDT, что может привести к непредсказуемому результату.
```

---

## Интерпретация

При открытии документа `QuestionnairePage` загружает ответы через REST и
записывает их в `yAnswers` при условии `yAnswers.size === 0`. Однако Yjs
синхронизирует состояние с сервером асинхронно после установки WebSocket. Если
другой клиент уже работает с документом, `yAnswers.size` в момент проверки всё
равно равен 0 (WS ещё не получил данные), и клиент B перезаписывает актуальное
Yjs-состояние устаревшими REST-данными.

---

## Причина ошибки

**Корень проблемы:**  
`yAnswers.size === 0` проверяется сразу после `loadQ.then(...)`, не дожидаясь
события `sync` от WebSocket-провайдера.

**Цепочка сбоя:**
1. Пользователь A работает с документом, вводит ответы → `yAnswers` содержит
   актуальные данные.
2. Пользователь B открывает документ → `loadQ` резолвится (≈200мс), WS ещё не
   синхронизировался (≈300–500мс).
3. `yAnswers.size === 0` → B записывает свои сохранённые REST-ответы в `yAnswers`.
4. WS синхронизируется → Yjs merge по CRDT: результат непредсказуем, если A
   активно печатал между шагами 2–4.

---

## Файлы для изучения перед исполнением

```
frontend/src/hooks/useCollaboration.ts        — provider, sync event
frontend/src/components/questionnaire/QuestionnairePage.tsx
                                              — loadQ, yAnswers инициализация
```

---

## Файлы для изменения

```
frontend/src/hooks/useCollaboration.ts        — добавить isSynced: boolean в CollaborationState
frontend/src/components/questionnaire/QuestionnairePage.tsx
                                              — инициализировать yAnswers только после sync
```

---

## Поэтапное исполнение

### Шаг 1 — Экспонировать `isSynced` из `useCollaboration`

Добавить `isSynced: boolean` в `CollaborationState` и в хук:

```ts
const [isSynced, setIsSynced] = useState(false)
// в useEffect провайдера:
const onSync = (synced: boolean) => {
  if (synced) { setIsConnected(true); setIsSynced(true) }
}
// в cleanup:
setIsSynced(false)
// в return:
return { ydoc, provider, isConnected, isSynced, connectedUsers, yAnswers }
```

### Шаг 2 — Отложить запись в `yAnswers` до sync

В `QuestionnairePage` заменить прямую инициализацию в `loadQ` на `useEffect`,
зависящий от `isSynced`:

```ts
const initialAnswersRef = useRef<Record<string, unknown> | null>(null)

// в loadQ — сохранить initial, но не писать сразу:
initialAnswersRef.current = initial

// новый useEffect:
useEffect(() => {
  if (!isSynced) return
  if (yAnswers.size === 0 && initialAnswersRef.current) {
    Object.entries(initialAnswersRef.current).forEach(([k, v]) => yAnswers.set(k, v))
  }
}, [isSynced, yAnswers])
```

Это гарантирует: к моменту записи Yjs уже получил актуальное состояние от сервера.

---

## Автоматические тесты

### Что покрыть

| Сценарий | Тип теста | Файл |
|---|---|---|
| `yAnswers` не инициализируется из REST до `isSynced === true` | unit | `QuestionnairePage.test.tsx` |
| После `isSynced = true` и `yAnswers.size === 0` — значения из REST записываются | unit | `QuestionnairePage.test.tsx` |
| После `isSynced = true` и `yAnswers.size > 0` — REST-значения НЕ перезаписывают Yjs | unit | `QuestionnairePage.test.tsx` |

### Команды запуска

```bash
cd frontend && npm test -- --run src/components/questionnaire/QuestionnairePage.test.tsx
```

---

## Чеклист ручного тестирования

- [ ] Открыть документ в двух вкладках — пользователь A вводит ответы
- [ ] Пользователь B открывает документ — ответы A не стираются
- [ ] Пользователь B открывает документ при отключённом WS — ответы из REST загружаются корректно
- [ ] Регрессия: одиночный пользователь — ответы сохраняются и отображаются

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
- [ ] Git-коммит создан с сообщением: `fix(collab): init yAnswers after WS sync`
- [ ] Статус обновлён в этом файле: `DONE`
- [ ] Реестр обновлён: [docs/tasks/README.md](README.md)
- [ ] GitHub Issue закрыт (state: closed) + комментарий с хешем коммита
