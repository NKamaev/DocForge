# collaboration — совместное редактирование (Yjs)

## Ответственность

Реализует real-time совместное редактирование документов через WebSocket,
используя бинарный протокол Yjs (y-websocket).

## Ключевые классы

| Класс | Роль |
|---|---|
| `WebSocketConfig` | Регистрирует WS-endpoint `/ws/collab/*`, подключает handshake-interceptor |
| `JwtHandshakeInterceptor` | Проверяет JWT из `?token=` при WS-upgrade; отклоняет неаутентифицированных |
| `YjsWebSocketHandler` | Диспетчер бинарных Yjs-сообщений (syncStep1/2, update, awareness) |
| `YjsDocumentStore` | Интерфейс: загрузка/хранение/сохранение Yjs-состояния |
| `YjsDocumentStoreImpl` | Реализация: in-memory кэш + дебаунс-сохранение в MongoDB (5 с) |
| `CollaborationBroadcaster` | Интерфейс: рассылка сообщений участникам документа |
| `LocalCollaborationBroadcaster` | Реализация: in-memory Map docId→Set<Session> (один инстанс) |
| `Varint` | Хелпер: кодирование/декодирование lib0-varint (формат Yjs/y-websocket) |

## Протокол Yjs (бинарный)

```
Тип сообщения (первый varint):
  0 — sync step 1   Client → Server: state vector клиента
                    Server → Client: sync step 2 (что клиент пропустил)
  1 — sync step 2   Содержит update; применяется к серверному состоянию + broadcast
  2 — update        Инкрементальный CRDT-update; применяется + broadcast
  3 — awareness     Курсоры/присутствие; только relay, не сохраняется
```

## Поток сообщений при подключении

```
Клиент A                    Сервер                  Клиент B
  |                            |                        |
  |── WS connect (token) ─────►|                        |
  |◄── syncStep2 (full state) ─|                        |
  |── syncStep1 (my sv) ──────►|                        |
  |◄── syncStep2 (diff) ───────|                        |
  |── update (изменение) ─────►|                        |
  |                            |── update (relay) ─────►|
  |                            |    (persist debounce)  |
```

## Хранение состояния

- Во время активной сессии: `ConcurrentHashMap<String, byte[]>` (накопленные обновления)
- Дебаунс-сохранение: через 5 с после последнего update → `documents.yjs_state` в MongoDB
- При отключении последнего участника: `persistNow()` + `evict()`

## MVP vs. Production

| Аспект | MVP (сейчас) | Production |
|---|---|---|
| Broadcaster | `LocalCollaborationBroadcaster` | Redis pub/sub (заменить реализацию) |
| Diff-sync | Полное состояние при syncStep1 | Настоящий Yjs-diff по state vector |
| Аутентификация | JWT в `?token=` | Может быть cookie с HttpOnly |

## Зависимости модуля

- `document.DocumentService` — для `getYjsState` / `saveYjsState`
- `security.JwtTokenProvider` — для валидации токена при handshake
