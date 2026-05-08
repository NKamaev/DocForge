# TASK-019-1: Backend — API diff между редакциями документа

| Поле | Значение |
|---|---|
| **Дата** | 2026-04-18 |
| **Тип** | `feature` |
| **Статус** | `TODO` |
| **Агенты** | `/backend` |
| **Коммит** | — |
| **Зависит от** | BUG-011 |
| **GitHub Issue** | [#27](https://github.com/NKamaev/DocForge/issues/27) |

---

## Постановка задачи (от пользователя)

```
необходимо добавить опцию включения отображения внесенных изменений
в тексте документа
```

---

## Интерпретация

Добавить endpoint, который принимает ID документа и ID редакции, и возвращает diff текстового тела между указанной редакцией и предыдущей (или текущим состоянием). Diff вычисляется на бэкенде и отдаётся клиенту в виде структурированного JSON (список операций: `insert`, `delete`, `equal`).

Фронтенд использует эти данные для визуализации — он не вычисляет diff сам.

---

## Файлы для изучения перед исполнением

```
backend/src/main/java/com/docforge/document/DocumentServiceImpl.java  — saveRevision, getRevision
backend/src/main/java/com/docforge/document/DocumentController.java   — REST-маппинги
backend/src/main/java/com/docforge/document/model/              — DocumentRevision, DocumentRevisionBody
docs/architecture.md                                             — контракты API
```

---

## Файлы для изменения

```
backend/src/main/java/com/docforge/document/DocumentController.java        — новый endpoint GET /api/documents/{id}/revisions/{revisionId}/diff
backend/src/main/java/com/docforge/document/DocumentServiceImpl.java       — метод computeRevisionDiff()
backend/src/main/java/com/docforge/document/dto/RevisionDiffResponse.java  — новый DTO (record)
build.gradle (backend)                                                       — зависимость diff-library (например, java-diff-utils)
```

---

## Поэтапное исполнение

1. **Добавить зависимость** `io.github.java-diff-utils:java-diff-utils` в `backend/build.gradle`.
2. **Создать DTO** `RevisionDiffResponse` — record с полями: `revisionId`, `compareToRevisionId` (или `"current"`), `diff` — список `DiffChunk { type: "INSERT"|"DELETE"|"EQUAL", text: String }`.
3. **Реализовать** `DocumentService.computeRevisionDiff(Long documentId, Long revisionId, @Nullable Long compareToRevisionId)`:
   - Загружает тело указанной редакции из MongoDB.
   - Если `compareToRevisionId` null — сравнивает с `document.currentBody`; иначе с телом второй редакции.
   - Извлекает plain-text из TipTap JSON (рекурсивный обход `doc.content[].text`) для построения диффа.
   - Применяет `DiffUtils.diff()`, возвращает `RevisionDiffResponse`.
4. **Добавить endpoint** в `DocumentController`:
   ```
   GET /api/documents/{id}/revisions/{revisionId}/diff?compareTo={revisionId|"current"}
   ```
5. **Написать unit-тест** для `computeRevisionDiff` с моками MongoDB/PostgreSQL репозиториев.

---

## Автоматические тесты

### Что покрыть

| Сценарий | Тип теста | Файл |
|---|---|---|
| diff между двумя редакциями возвращает корректные chunks | unit | `DocumentServiceTest.java` |
| diff редакции с текущим телом | unit | `DocumentServiceTest.java` |
| endpoint возвращает 404 если revisionId не существует | integration | `DocumentControllerIT.java` |

### Команды запуска

```bash
cd backend && ./gradlew test --tests "com.docforge.document.DocumentServiceTest"
cd backend && ./gradlew test --tests "com.docforge.document.DocumentControllerIT"
```

---

## Чеклист ручного тестирования

- [ ] `GET /api/documents/{id}/revisions/{revId}/diff` возвращает 200 с непустым `diff`
- [ ] `diff` содержит корректные `INSERT`/`DELETE`/`EQUAL` блоки при сравнении двух отличающихся редакций
- [ ] При сравнении идентичных тел возвращается только `EQUAL`
- [ ] Несуществующий revisionId → 404

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
- [ ] Git-коммит создан: `feat(document): add revision diff API endpoint`
- [ ] Статус обновлён: DONE
- [ ] Реестр обновлён: [docs/tasks/README.md](README.md)
