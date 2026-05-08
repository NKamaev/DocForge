# TASK-018-1: Backend — переименовать ревизии в редакции, добавить authorName в DTO

| Поле | Значение |
|---|---|
| **Дата** | 2026-04-18 |
| **Тип** | `refactor` |
| **Статус** | `TODO` |
| **Агенты** | `/backend` |
| **Коммит** | — |
| **Зависит от** | — |
| **GitHub Issue** | [#24](https://github.com/NKamaev/DocForge/issues/24) |

---

## Постановка задачи (от пользователя)

```
переименовать ревизии в редакции, указывать в ревизиях/редакциях автора изменений
```

---

## Интерпретация

Переименовать Java-классы/DTO `...Revision...` → `...Edition...` (или оставить внутренние имена, но обновить Javadoc и DTO-поля с `revision` → `edition`).  
Добавить поле `authorName: String` в `DocumentRevisionDTO`, разрешая его через `UserService.getById(savedBy).displayName()`.

**Переименование в БД (таблицы, колонки) — не нужно**: имена таблиц `document_revision`, колонка `revision_number` остаются. Меняем только:
- Java-классы и интерфейсы (переименование)
- DTO-поле `revisionNumber` → `editionNumber` (API-контракт меняется)
- Добавляем `authorName` в DTO

---

## Файлы для изучения перед исполнением

```
backend/src/main/java/com/docforge/document/model/DocumentRevision.java
backend/src/main/java/com/docforge/document/dto/DocumentRevisionDTO.java
backend/src/main/java/com/docforge/document/DocumentServiceImpl.java     — метод toRevisionDTO()
backend/src/main/java/com/docforge/document/DocumentController.java      — эндпоинты /revisions
backend/src/main/java/com/docforge/document/DocumentService.java
backend/src/main/java/com/docforge/user/UserService.java                 — getById(UUID)
backend/src/main/java/com/docforge/user/dto/UserDTO.java                 — поле displayName
```

---

## Файлы для изменения

```
backend/src/main/java/com/docforge/document/dto/DocumentRevisionDTO.java
  — переименовать поле revisionNumber → editionNumber
  — добавить поле authorName (String, nullable)

backend/src/main/java/com/docforge/document/DocumentServiceImpl.java
  — в маппере: заполнять authorName через userService.getById(savedBy).displayName()
  — savedBy может быть null → authorName = null

backend/src/main/java/com/docforge/document/model/DocumentRevision.java
  — Javadoc: «редакция» вместо «ревизия»

backend/src/main/java/com/docforge/document/DocumentController.java
  — URL-пути /revisions оставить (URL — публичный контракт, менять нежелательно)
  — обновить комментарии
```

---

## Поэтапное исполнение

1. **Добавить `authorName` в `DocumentRevisionDTO`** — добавить поле `String authorName` после `savedBy`.
2. **Обновить маппер в `DocumentServiceImpl`** — при заполнении DTO: если `savedBy != null`, вызвать `userService.getById(savedBy)`, достать `displayName()`. Обернуть в try/catch — если пользователь не найден, `authorName = null`.
3. **Переименовать поле `revisionNumber` → `editionNumber`** в `DocumentRevisionDTO` и всех местах использования в сервисе.
4. **Обновить Javadoc/комментарии** в `DocumentRevision.java` и `DocumentController.java` — «редакция» вместо «ревизия».
5. **Убедиться что `UserService` доступен в `DocumentServiceImpl`** — инжектировать, если ещё не инжектирован.
6. **Запустить тесты**.

---

## Автоматические тесты

### Что покрыть

| Сценарий | Тип теста | Файл |
|---|---|---|
| `authorName` заполнен когда `savedBy` != null | unit | `DocumentServiceImplTest.java` |
| `authorName = null` когда `savedBy = null` | unit | `DocumentServiceImplTest.java` |

### Команды запуска

```bash
cd backend && ./gradlew test --tests "com.docforge.document.*"
```

---

## Чеклист ручного тестирования

- [ ] GET `/api/documents/{id}/revisions` — ответ содержит `editionNumber` и `authorName`
- [ ] `authorName` — отображаемое имя пользователя, а не UUID
- [ ] Если `savedBy = null` — `authorName: null` без ошибки
- [ ] Регрессия: сохранение и восстановление редакций работает

---

## История

| Дата | Событие |
|---|---|
| 2026-04-18 | Задача создана |

---

## Завершение задачи

> **Обязательно выполнить перед отчётом о выполнении!**

- [ ] Автотесты запущены и проходят (`./gradlew test`)
- [ ] Ручной чеклист выполнен полностью
- [ ] Код прошёл ревью (`/review`)
- [ ] Git-коммит создан с сообщением: `refactor(document): rename revision to edition, add authorName to DTO`
- [ ] Статус обновлён в этом файле: `DONE`
- [ ] Реестр обновлён: [docs/tasks/README.md](README.md)
