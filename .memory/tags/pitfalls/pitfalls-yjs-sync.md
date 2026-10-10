# Pitfalls — Yjs / WebSocket синхронизация ответов

> Тег: `pitfalls:yjs-sync` · Обновлено: `2026-10-10`

## Класс проблем: гонки REST ↔ Yjs и жизненный цикл провайдера

### Гонка: инициализация yAnswers из REST до WS-sync (BUG-020)
- **Симптом:** второй пользователь открывает документ, где первый уже работает → B перезаписывает актуальное Yjs-состояние устаревшими REST-данными.
- **Корень:** проверка `yAnswers.size === 0` происходит до завершения initial WS-sync (WebSocket асинхронен).
- **Решение (паттерн):** seeding guard в `DocumentWorkspace` — ждать WS-sync (400ms, `seededRef`), Yjs-контент побеждает REST (`DocumentWorkspace.tsx:163-190`).
- **Источники:** `docs/archive/tasks/tasks/BUG-020-yanswers-init-before-ws-sync.md`, тег `frontend:workspace`.

### Ответы ОЛ не синхронизируются в реальном времени (BUG-010)
- **Симптом:** текст синхронизируется, а ответы ОЛ и чипы параметров — только после reload.
- **Корень:** ответы держали в локальном React-state, а не в Yjs. Лечение: единый канал `yAnswers = ydoc.getMap('answers')` + observer в обе стороны.
- **Источник:** `docs/archive/tasks/tasks/BUG-010-answers-not-synced-realtime.md`.

### StrictMode: двойное WS-соединение / «мертвый» документ
- **Правило:** `ydoc` — в `useMemo` по documentId (никогда не destroy); `WebsocketProvider` — в `useEffect` с полным `p.destroy()` в cleanup (`useCollaboration.ts:35-38,47-86`). Иначе dev-режим создаёт второе соединение или теряет привязку документа.
- **Источник:** `frontend/src/hooks/useCollaboration.ts`.

### Очистка Yjs при откате/рестарте
- **BUG-046:** рестарт после restore-редакции делался через `window.location.reload()`. Правильно — программный reset: очистить Yjs fragment + `yAnswers.clear()` + reload REST + `restoreSeedKey` (`QuestionnairePage.tsx:264-309`). Commit: `6e17e84`, `a2e031e`.
- **Источник:** git log, тег `tickets:BUG-046`.

<!-- Новые записи добавляй СВЕРХУ. -->