# TASK-025-1: Backend — снимок ответов справочника при генерации + API staleness

| Поле | Значение |
|---|---|
| **Дата** | 2026-04-18 |
| **Тип** | `feature` |
| **Статус** | `TODO` |
| **Агенты** | `/backend` |
| **Коммит** | — |
| **Зависит от** | — |
| **GitHub Issue** | [#42](https://github.com/NKamaev/DocForge/issues/42) |

---

## Постановка задачи (от пользователя)

> Часть TASK-025: backend-половина иммутабельности справочников.

---

## Интерпретация

При генерации документа (`POST /api/documents/{id}/generate`) для каждого параметра типа `DIRECTORY` сохранять снимок текущих значений полей в `answers._directory_snapshots`. Новый сервис `DirectoryService.checkAnswersForStaleness()` сравнивает снимок с актуальными данными в MongoDB. Новый эндпоинт `GET /api/documents/{id}/staleness` возвращает список устаревших полей.

---

## Файлы для изучения перед исполнением

```
backend/src/main/java/com/docforge/document/DocumentServiceImpl.java
backend/src/main/java/com/docforge/document/DocumentGenerator.java
backend/src/main/java/com/docforge/directory/DirectoryService.java
backend/src/main/java/com/docforge/directory/DirectoryServiceImpl.java
backend/src/main/java/com/docforge/document/DocumentController.java
docs/architecture.md
```

---

## Файлы для изменения

```
backend/src/main/java/com/docforge/document/DocumentServiceImpl.java
  — в generate(): для DIRECTORY-параметров дописать answers._directory_snapshots

backend/src/main/java/com/docforge/directory/DirectoryService.java
  — добавить метод checkAnswersForStaleness(documentId) → Map<String, StalenessInfo>

backend/src/main/java/com/docforge/directory/DirectoryServiceImpl.java
  — реализовать checkAnswersForStaleness: читать _directory_snapshots, сравнивать с MongoDB

backend/src/main/java/com/docforge/directory/StalenessInfo.java (новый)
  — record StalenessInfo(boolean isStale, Map<String,Object> currentValues, Map<String,Object> snapshotValues)

backend/src/main/java/com/docforge/document/DocumentController.java
  — добавить GET /api/documents/{id}/staleness → Map<String, StalenessInfo>
```

---

## Поэтапное исполнение

1. **Создать `StalenessInfo`** — Java record с полями `isStale`, `currentValues`, `snapshotValues`.
2. **Расширить `DocumentServiceImpl.generate()`** — после сохранения `answers` для каждого DIRECTORY-параметра:
   - загрузить запись справочника по UUID из `answers[paramCode]`
   - скопировать значения `directoryFields` в `answers[paramCode__fieldKey]`
   - записать снимок в `answers._directory_snapshots[paramCode]` с `entry_id`, `values`, `snapshot_at`
3. **Реализовать `DirectoryService.checkAnswersForStaleness(documentId)`**:
   - загрузить документ из MongoDB
   - для каждого `_directory_snapshots[paramCode]` — загрузить актуальную запись
   - сравнить поля; вернуть `Map<String, StalenessInfo>`
4. **Добавить эндпоинт** `GET /api/documents/{id}/staleness` в `DocumentController`.
5. **Написать тесты** (см. раздел ниже).

---

## Структура снимка в `answers`

```jsonc
{
  "bank": "entry-uuid-123",
  "bank__name": "Сбербанк",
  "bank__bik":  "044525225",
  "_directory_snapshots": {
    "bank": {
      "entry_id":    "entry-uuid-123",
      "values":      { "name": "Сбербанк", "bik": "044525225" },
      "snapshot_at": "2026-04-18T10:00:00Z"
    }
  }
}
```

---

## Автоматические тесты

### Что покрыть

| Сценарий | Тип теста | Файл |
|---|---|---|
| generate() сохраняет _directory_snapshots | unit | `DocumentServiceTest.java` |
| checkAnswersForStaleness: нет изменений → isStale=false | unit | `DirectoryServiceTest.java` |
| checkAnswersForStaleness: значение изменилось → isStale=true, currentValues актуальны | unit | `DirectoryServiceTest.java` |
| GET /staleness возвращает корректный JSON | integration | `DocumentControllerIT.java` |

### Команды запуска

```bash
cd backend && ./gradlew test --tests "com.docforge.document.DocumentServiceTest"
cd backend && ./gradlew test --tests "com.docforge.directory.DirectoryServiceTest"
cd backend && ./gradlew test --tests "com.docforge.document.DocumentControllerIT"
```

---

## Чеклист ручного тестирования

- [ ] Создать документ с DIRECTORY-параметром → сгенерировать → в MongoDB проверить `answers._directory_snapshots`
- [ ] Изменить запись справочника → вызвать `GET /api/documents/{id}/staleness` → убедиться что `isStale: true`
- [ ] Без изменений справочника → staleness возвращает `isStale: false`
- [ ] Регрессия: генерация документа без DIRECTORY-параметров работает как прежде

---

## История

| Дата | Событие |
|---|---|
| 2026-04-18 | Задача создана как подзадача TASK-025 |

---

## Завершение задачи

> **Обязательно выполнить перед отчётом о выполнении!**

- [ ] Автотесты запущены и проходят (`./gradlew test`)
- [ ] Ручной чеклист выполнен полностью
- [ ] Код прошёл ревью (`/review`)
- [ ] Git-коммит создан с сообщением: `feat(document): directory snapshots on generate + staleness API`
- [ ] Статус обновлён в этом файле: `DONE`
- [ ] Реестр обновлён: [docs/tasks/README.md](README.md)
