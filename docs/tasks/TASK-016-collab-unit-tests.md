# TASK-016: Unit-тесты для коллаборации (YjsDocumentStoreImpl, ParticipantsPanel)

| Поле | Значение |
|---|---|
| **Дата** | 2026-04-18 |
| **Тип** | `refactor` |
| **Статус** | `TODO` |
| **Агенты** | `/backend`, `/frontend` |
| **Коммит** | — |
| **Зависит от** | TASK-015 |
| **GitHub Issue** | [#19](https://github.com/NKamaev/DocForge/issues/19) |

---

## Постановка задачи

Код ревью TASK-006-2 выявил отсутствие тестов для нового кода коллаборации:
- `YjsDocumentStoreImpl.pack` / `unpack` — package-private static методы без покрытия
- `ParticipantsPanel` — нет render-тестов
- Seed-логика `DocumentWorkspace` — покрывается TASK-010, но здесь добавляем тест подключения к Yjs

---

## Интерпретация

Написать минимально достаточный набор тестов, покрывающий критические пути новой функциональности коллаборации. Seed-логика `DocumentWorkspace` с Yjs полностью покрывается в TASK-010 — здесь не дублировать.

---

## Файлы для изменения

```
backend/src/test/java/com/docforge/collaboration/YjsDocumentStoreImplTest.java  — создать
frontend/src/components/document-editor/ParticipantsPanel.test.tsx               — создать
```

---

## Поэтапное исполнение

### Backend: `YjsDocumentStoreImplTest`

1. Тест `pack → unpack` round-trip: несколько updates → pack → unpack → assertEquals
2. Тест `unpack` с пустым массивом → пустой список
3. Тест `unpack` с обрезанными данными (trailing garbage) → корректно обрабатывает
4. Тест `getUpdates` → `applyUpdate` → `getUpdates` возвращает добавленный update
5. Тест `schedulePersist` дважды → `DocumentService.saveYjsState` вызывается один раз (с Mockito)

### Frontend: `ParticipantsPanel.test.tsx`

1. Рендер без пользователей → показывает «офлайн»
2. Рендер с `currentUserName` и `isConnected=true` → показывает зелёный индикатор
3. Рендер с `isConnected=false` → показывает серый индикатор
4. Рендер с двумя `connectedUsers` → два avatar bubble

---

## Автоматические тесты

### Команды запуска

```bash
# Backend
cd backend && ./gradlew test --tests "com.docforge.collaboration.YjsDocumentStoreImplTest"

# Frontend
cd frontend && npm test -- --run ParticipantsPanel
```

---

## Чеклист ручного тестирования

- [ ] Все тесты проходят без ошибок
- [ ] Покрытие `pack`/`unpack` подтверждено

---

## История

| Дата | Событие |
|---|---|
| 2026-04-18 | Задача создана по результатам код ревью TASK-006-2 |

---

## Завершение задачи

> **Обязательно выполнить перед отчётом о выполнении!**

- [ ] Автотесты запущены и проходят
- [ ] Ручной чеклист выполнен полностью
- [ ] Код прошёл ревью (`/review`)
- [ ] Git-коммит создан с сообщением: `test(collab): unit tests for YjsDocumentStoreImpl and ParticipantsPanel`
- [ ] Статус обновлён в этом файле: `DONE`
- [ ] Реестр обновлён: [docs/tasks/README.md](README.md)
