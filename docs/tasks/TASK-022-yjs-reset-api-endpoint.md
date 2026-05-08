# TASK-022: Backend API для сброса Yjs-состояния документа

| Поле | Значение |
|---|---|
| **Дата** | 2026-04-18 |
| **Тип** | `feature` |
| **Статус** | `TODO` |
| **Агенты** | `/backend` |
| **Коммит** | — |
| **Зависит от** | — |
| **GitHub Issue** | [#34](https://github.com/NKamaev/DocForge/issues/34) |

---

## Постановка задачи (от пользователя)

Найдено в ходе ревью BUG-012.

---

## Интерпретация

После исправления BUG-012 новые документы сеются корректно (с сохранёнными `conditionalBlock`-нодами). Но существующие документы хранят в MongoDB поле `yjs_state` со старым плоским контентом (все условные блоки развёрнуты). Чтобы они тоже начали корректно отображать условную видимость, нужно сбросить Yjs-состояние и `current_body`.

Сейчас это можно сделать только руками в MongoDB Shell. Нужен API-эндпоинт.

---

## Файлы для изучения перед исполнением

```
backend/src/main/java/com/docforge/document/DocumentService.java
backend/src/main/java/com/docforge/document/DocumentServiceImpl.java
backend/src/main/java/com/docforge/collaboration/YjsDocumentStoreImpl.java
backend/src/main/java/com/docforge/document/model/Document.java
```

---

## Файлы для изменения

```
backend/src/main/java/com/docforge/document/DocumentService.java         — новый метод resetYjsState
backend/src/main/java/com/docforge/document/DocumentServiceImpl.java     — реализация
backend/src/main/java/com/docforge/document/DocumentController.java      — новый эндпоинт
backend/src/main/java/com/docforge/collaboration/YjsDocumentStoreImpl.java — вызов evict
```

---

## Поэтапное исполнение

1. Добавить метод в `DocumentService`:
   ```java
   void resetYjsState(UUID documentId);
   ```
2. Реализовать в `DocumentServiceImpl`: обнулить `yjsState` и `currentBody` в MongoDB, вызвать `YjsDocumentStore.evict(docId)` чтобы сбросить in-memory кеш.
3. Добавить эндпоинт:
   ```
   DELETE /api/documents/{id}/yjs-state
   ```
   Возвращает `204 No Content`. Только для владельца документа (или ADMIN).
4. При следующем открытии `/fill` Yjs-документ будет пуст → сидинг запустится заново с новой логикой.

---

## Автоматические тесты

| Сценарий | Тип | Файл |
|---|---|---|
| Сброс yjs_state и current_body в DB | integration | `DocumentControllerIT.java` |
| Evict из in-memory кеша после сброса | unit | `DocumentServiceTest.java` |

---

## Чеклист ручного тестирования

- [ ] `DELETE /api/documents/{id}/yjs-state` → 204
- [ ] Открыть `/fill` после сброса → документ пересевается из шаблона с условными блоками
- [ ] Условные блоки показываются/скрываются корректно по ответам
- [ ] Повторный `DELETE` — не падает (идемпотентность)

---

## История

| Дата | Событие |
|---|---|
| 2026-04-18 | Задача создана по результатам ревью BUG-012 |

---

## Завершение задачи

> **Обязательно выполнить перед отчётом о выполнении!**

- [ ] Автотесты запущены и проходят
- [ ] Ручной чеклист выполнен полностью
- [ ] Код прошёл ревью (`/review`)
- [ ] Git-коммит создан: `feat(document): DELETE /api/documents/{id}/yjs-state endpoint`
- [ ] Статус обновлён: DONE
- [ ] Реестр обновлён: docs/tasks/README.md
