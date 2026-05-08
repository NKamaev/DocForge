# TASK-003: Backend — доработки по ревью TASK-002

| Поле | Значение |
|---|---|
| **Дата** | 2026-04-16 |
| **Тип** | `refactor` |
| **Статус** | `DONE` |
| **Агенты** | `/backend` |
| **Коммит** | 90268c5 |
| **Зависит от** | TASK-002 |
| **GitHub Issue** | [#3](https://github.com/NKamaev/DocForge/issues/3) |

---

## Постановка задачи (от пользователя)

> Из ревью TASK-002 (код-ревьюер, 2026-04-16).

```
WARNING:   delete() не проверяет isPublished() — опубликованный шаблон можно удалить.
SUGGESTION: TemplatePublishedException сообщение на английском, остальные — на русском.
SUGGESTION: TemplateControllerIT использует Map<String,String> вместо UpdateTemplateRequest.
```

---

## Интерпретация

Три небольших доработки в backend-слое, все в рамках одного коммита:

1. **Защита `delete()`** — добавить проверку `if (registry.isPublished()) throw new TemplatePublishedException(id)` перед удалением.
2. **Русский язык в исключении** — сообщение `"Template is published and cannot be modified: {id}"` → `"Шаблон опубликован и не может быть изменён: {id}"`.
3. **Типизированный body в IT-тесте** — заменить `Map<String, String>` на `new UpdateTemplateRequest(...)` в обоих методах `TemplateControllerIT`.

---

## Файлы для изучения перед исполнением

```
backend/src/main/java/com/docforge/common/exception/TemplatePublishedException.java
backend/src/main/java/com/docforge/template/TemplateServiceImpl.java  — метод delete()
backend/src/test/java/com/docforge/template/TemplateControllerIT.java
backend/src/test/java/com/docforge/template/TemplateServiceTest.java  — regression
```

---

## Файлы для изменения

```
backend/src/main/java/com/docforge/common/exception/TemplatePublishedException.java
  — изменить сообщение на русский

backend/src/main/java/com/docforge/template/TemplateServiceImpl.java
  — добавить isPublished()-guard в delete()

backend/src/test/java/com/docforge/template/TemplateControllerIT.java
  — заменить Map<String,String> на UpdateTemplateRequest в теле запроса

backend/src/test/java/com/docforge/template/TemplateServiceTest.java
  — добавить тест: delete() на published → TemplatePublishedException
```

---

## Поэтапное исполнение

1. **Сообщение** — поменять строку в `TemplatePublishedException(UUID)`.
2. **Guard в `delete()`** — добавить `isPublished()`-проверку сразу после `findRegistryOrThrow(id)`.
3. **IT-тест** — заменить `Map<String, String>` на `new UpdateTemplateRequest("...", null, ...)`.
4. **Unit-тест** — добавить в `TemplateServiceTest` тест `delete_publishedTemplate_throwsException`.
5. **Прогон** — `./gradlew test --tests "com.docforge.template.*"`.

---

## Автоматические тесты

### Что покрыть

| Сценарий | Тип теста | Файл |
|---|---|---|
| `delete()` на published → `TemplatePublishedException` | unit | `TemplateServiceTest.java` |
| IT-тест: тело запроса типизировано | integration | `TemplateControllerIT.java` |

### Команды запуска

```bash
cd backend && ./gradlew test --tests "com.docforge.template.TemplateServiceTest"
cd backend && ./gradlew test --tests "com.docforge.template.TemplateControllerIT"
```

---

## Чеклист ручного тестирования

- [ ] Попытка удалить опубликованный шаблон → получить 409
- [ ] Попытка удалить черновик → удаляется (регрессия)
- [ ] Сообщение в ответе на 409 — на русском

---

## История

| Дата | Событие |
|---|---|
| 2026-04-16 | Задача создана по результатам ревью TASK-002 |
| 2026-04-18 | Выполнено: коммит 90268c5 |

---

## Завершение задачи

> **Обязательно выполнить перед отчётом о выполнении!**

- [ ] Автотесты запущены и проходят (`./gradlew test`)
- [ ] Ручной чеклист выполнен полностью
- [ ] Код прошёл ревью (`/review`)
- [ ] Git-коммит создан с сообщением: `refactor(template): block delete published, ru message, typed IT request`
- [ ] Статус обновлён в этом файле: `DONE`
- [ ] Реестр обновлён: [docs/tasks/README.md](README.md)
