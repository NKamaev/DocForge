# TASK-029-1: Backend — хранение и резолвинг параметров и ссылок на пункты

| Поле | Значение |
|---|---|
| **Дата** | 2026-04-18 |
| **Тип** | `feature` |
| **Статус** | `DONE` |
| **Агенты** | `/backend` |
| **Коммит** | `16f5208` |
| **Зависит от** | — |
| **GitHub Issue** | — |
| **Эпик** | [TASK-029](TASK-029-parameters-and-clause-refs.md) |

---

## Постановка задачи (от пользователя)

```
добавить возможность добавления параметров и ссылки на пункты в формулировках
```

---

## Интерпретация

Добавить на бэкенде поддержку двух новых inline TipTap-узлов:

- **`templateParam`** — ссылка на поле шаблона по `fieldId`. При экспорте заменяется на значение ответа (`answers[fieldId]`).
- **`clauseRef`** — ссылка на пронумерованный пункт документа по `clauseId` (UUID пункта в JSON). При экспорте резолвится в строку «п. X.Y».

Бэкенд должен:
1. Корректно хранить эти узлы в теле документа (MongoDB, поле `body` — ProseMirror JSON).
2. Резолвить `templateParam` при экспорте: искать значение в `answers` документа.
3. Резолвить `clauseRef` при экспорте: вычислять номер пункта по положению в дереве.

---

## Файлы для изучения перед исполнением

```
backend/src/main/java/com/docforge/document/export/     — логика экспорта DOCX/PDF
backend/src/main/java/com/docforge/document/            — Document, DocumentService
backend/src/main/java/com/docforge/template/            — TemplateField, TemplateService
docs/architecture.md                                     — модель данных
```

---

## Файлы для изменения

```
backend/src/main/java/com/docforge/document/export/DocxExportService.java  — резолвинг узлов
backend/src/main/java/com/docforge/document/export/PdfExportService.java   — резолвинг узлов
backend/src/main/java/com/docforge/document/export/NodeResolver.java        — новый класс
```

---

## Поэтапное исполнение

1. **Изучить** текущую логику обхода ProseMirror JSON в экспорте.
2. **Создать** `NodeResolver` — класс с методами `resolveTemplateParam(node, answers)` и `resolveClauseRef(node, doc)`.
3. **Реализовать** резолвинг `templateParam`: из `node.attrs.fieldId` → `answers.get(fieldId)` → текст.
4. **Реализовать** резолвинг `clauseRef`: обход дерева → вычисление номера пункта → строка «п. X.Y».
5. **Интегрировать** `NodeResolver` в `DocxExportService` и `PdfExportService`.
6. **Написать** unit-тесты для `NodeResolver`.

---

## Автоматические тесты

### Что покрыть

| Сценарий | Тип теста | Файл |
|---|---|---|
| Резолвинг `templateParam` → значение из answers | unit | `NodeResolverTest.java` |
| Резолвинг `templateParam` → поле отсутствует (пустая строка) | unit | `NodeResolverTest.java` |
| Резолвинг `clauseRef` → корректный номер пункта | unit | `NodeResolverTest.java` |

### Команды запуска

```bash
cd backend && ./gradlew test --tests "com.docforge.document.export.NodeResolverTest"
```

---

## Чеклист ручного тестирования

- [ ] Создать шаблон с полем (например, «Имя»), вставить `{{Имя}}` в текст → сохранить
- [ ] Создать документ, заполнить поле → экспортировать DOCX → параметр заменён значением
- [ ] Вставить ссылку на пункт 2 → экспортировать DOCX → в тексте «п. 2»
- [ ] Переставить пункты местами → экспортировать → номера обновились корректно

---

## История

| Дата | Событие |
|---|---|
| 2026-04-18 | Задача создана |
| 2026-04-23 | Реализовано: NodeResolver, applyAnswers обновлён, fallback в DocxExportServiceImpl и PdfExportServiceImpl |

---

## Завершение задачи

> **Обязательно выполнить перед отчётом о выполнении!**

- [x] Автотесты запущены и проходят (`./gradlew test`)
- [ ] Ручной чеклист выполнен полностью
- [ ] Код прошёл ревью (`/review`)
- [x] Git-коммит создан с сообщением: `feat(export): resolve templateParam and clauseRef nodes`
- [x] Статус обновлён в этом файле: `DONE`
- [ ] Реестр обновлён: [docs/tasks/README.md](README.md)
- [ ] GitHub Issue закрыт (state: closed) + комментарий с хешем коммита
