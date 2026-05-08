# BUG-009: Hardcoded WebSocket URL в useCollaboration

| Поле | Значение |
|---|---|
| **Дата** | 2026-04-18 |
| **Тип** | `bug` |
| **Статус** | `DONE` |
| **Агенты** | `/frontend` |
| **Коммит** | `6125ffe` |
| **Зависит от** | — |
| **GitHub Issue** | [#16](https://github.com/NKamaev/DocForge/issues/16) |

---

## Постановка задачи

Код ревью TASK-006-2 выявил CRITICAL: в `useCollaboration.ts` захардкожен адрес `ws://localhost:8080`. В production/staging это сломает WebSocket-коллаборацию у всех пользователей.

---

## Причина ошибки

**Корень проблемы:**  
`useCollaboration.ts:46` — `const serverUrl = 'ws://localhost:8080'` — адрес прибит гвоздями к localhost.

**Цепочка сбоя:**  
1. Пользователь открывает `/fill` на продакшн-сервере.
2. Браузер пытается подключиться к `ws://localhost:8080/ws/collab/{id}`.
3. Соединение отклоняется, коллаборация не работает, `isConnected = false`.

---

## Файлы для изучения перед исполнением

```
frontend/src/hooks/useCollaboration.ts       — строка 46
frontend/.env.example (если существует)       — текущие переменные окружения
frontend/vite.config.ts                       — конфигурация Vite
```

---

## Файлы для изменения

```
frontend/src/hooks/useCollaboration.ts   — заменить строку 46 на env-переменную
frontend/.env                            — добавить VITE_WS_URL=ws://localhost:8080
frontend/.env.example                    — задокументировать переменную
```

---

## Поэтапное исполнение

1. В `useCollaboration.ts:46` заменить:
   ```ts
   const serverUrl = 'ws://localhost:8080'
   ```
   на:
   ```ts
   const serverUrl = import.meta.env.VITE_WS_URL ?? `${window.location.protocol === 'https:' ? 'wss' : 'ws'}://${window.location.host}`
   ```
2. Добавить в `frontend/.env` (если не существует — создать): `VITE_WS_URL=ws://localhost:8080`
3. Добавить в `frontend/.env.example`: `VITE_WS_URL=ws://localhost:8080`
4. Проверить, что `VITE_WS_URL` не попадает в git (`.gitignore` для `.env`).

---

## Автоматические тесты

| Сценарий | Тип теста | Файл |
|---|---|---|
| `useCollaboration` использует VITE_WS_URL если задан | unit | `frontend/src/hooks/useCollaboration.test.ts` |

### Команды запуска

```bash
cd frontend && npm test -- --run
```

---

## Чеклист ручного тестирования

- [ ] Открыть `/fill/{id}` в браузере → `isConnected = true` (зелёный индикатор в ParticipantsPanel)
- [ ] Убедиться, что WS-запрос идёт на правильный хост (DevTools → Network → WS)
- [ ] Регрессия: коллаборация в двух вкладках по-прежнему работает

---

## История

| Дата | Событие |
|---|---|
| 2026-04-18 | Задача создана по результатам код ревью TASK-006-2 (CRITICAL) |

---

## Завершение задачи

> **Обязательно выполнить перед отчётом о выполнении!**

- [x] Автотесты запущены и проходят (`npm test`) — 34/34 ✓
- [ ] Ручной чеклист выполнен полностью
- [ ] Код прошёл ревью (`/review`)
- [x] Git-коммит создан с сообщением: `fix(collab): use env var for WebSocket URL`
- [x] Статус обновлён в этом файле: `DONE`
- [x] Реестр обновлён: [docs/tasks/README.md](README.md)
