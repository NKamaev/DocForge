# Коллаборация — Yjs / WebSocket

> Тег: `architecture:collaboration` · Обновлено: `2026-10-10`

## [2026-10-10 10:00] Серверная схема

- **Факт:** `WebSocketConfig` — `@EnableWebSocket` (:19), эндпоинт `/ws/collab/*`, `JwtHandshakeInterceptor` (токен в `?token=`, авторизация, отклонение при невалидном).
- **Протокол:** y-websocket v3 — внешние типы `MSG_SYNC=0`, `MSG_AWARENESS=1`; внутренние `SYNC_STEP1=0`, `SYNC_STEP2=1`, `SYNC_UPDATE=2` (`Varint.java:28-34`). `Varint.java` реализует lib0 binary protocol.
- **`YjsWebSocketHandler`** (extends `BinaryWebSocketHandler`): broadcast апдейтов другим сессиям; debounce 5s персист + мгновенный flush при отключении последнего клиента.
- **`YjsDocumentStoreImpl`:** `DEBOUNCE_SECONDS=5` (:15); ConcurrentHashMap кэш апдейтов + таймеры; в Mongo каждый апдейт пакуется с 4-byte big-endian length prefix в один `byte[]`.
- **`CollaborationBroadcaster`** — интерфейс; сейчас `LocalCollaborationBroadcaster` (только in-JVM), каркас под Redis pub/sub.
- **Источник:** `backend/src/main/java/com/docforge/collaboration/`, explore backend report.

## Frontend-сторона

- **`hooks/useCollaboration.ts`** (128 строк): возвращает `{ ydoc, provider, isConnected, connectedUsers, yAnswers }` (:12-18).
  - JWT через `?token=` в WS-URL (браузер не умеет WS-заголовки) (:20-30).
  - `buildWsUrl()`: `VITE_WS_URL` → dev `ws://localhost:8080` (минуя Vite proxy), prod — same-host `wss`/`ws` (:111-118).
  - **StrictMode-безопасность:** `ydoc` в `useMemo` на documentId (никогда не уничтожается — иначе «мертвая» привязка документа :35-38); `WebsocketProvider` в `useEffect` с полным `p.destroy()` (:47-86) — иначе двойное WS-соединение.
  - Канал ответов: `yAnswers = ydoc.getMap('answers')` (:39) — единственный канал синхронизации ОЛ↔документ.
  - Awareness: `{name,color}`, `loginToColor()` — детерминированный HSL из login (:121-128); `deduplicateByName()` — старший clientId (:101-108). Сигналы: `'status'` + `'sync'` (:56-62).
- **`ParticipantsPanel.tsx`:** аватарки, зелёная точка `#52c41a`, максимум 3 удалённых пользователя, текущий исключён.
- **Источник:** `frontend/src/hooks/useCollaboration.ts`.

<!-- Новые записи добавляй СВЕРХУ. -->