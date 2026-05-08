# BUG-021: NPE в applyAnswers — switch на null type узла

| Поле | Значение |
|---|---|
| **Дата** | 2026-04-23 |
| **Тип** | `bug` |
| **Статус** | `TODO` |
| **Агенты** | `/backend` |
| **Коммит** | — |
| **Зависит от** | — |
| **GitHub Issue** | [#69](https://github.com/NKamaev/DocForge/issues/69) |
| **Эпик** | — |

---

## Постановка задачи (от пользователя)

```
Код-ревью коммита 16f5208 выявил потенциальный NPE
```

---

## Интерпретация

В `DocumentController.applyAnswers()` введена конструкция `switch (type)`, где `type` — результат `(String) node.get("type")`. Если узел не содержит поля `"type"` (возможно в `currentBody` после Yjs-редактирования), метод бросит `NullPointerException` — экспорт DOCX/PDF упадёт с 500.

Оригинальный код использовал null-safe проверку `if (!"parameterField".equals(node.get("type")))`. Соседние методы в проекте (`DocxExportServiceImpl.renderNode`, `PdfExportServiceImpl.renderNode`) корректно применяют `switch (String.valueOf(type))`.

---

## Причина ошибки

**Корень проблемы:**  
Java switch-expression бросает `NullPointerException` при `switch (null)`. Разработчик применил паттерн `switch (type)` вместо null-safe `switch (String.valueOf(type))`.

**Цепочка сбоя:**  
1. Пользователь редактирует документ через TipTap/Yjs и сохраняет `currentBody`.
2. Узел без поля `"type"` (или с `null`) попадает в дерево.
3. `GET /api/documents/{id}/export/docx` → `applyAnswers()` → `switch (null)` → `NPE`.
4. Сервер возвращает `500 Internal Server Error`, экспорт недоступен.

---

## Файлы для изучения перед исполнением

```
backend/src/main/java/com/docforge/document/DocumentController.java — метод applyAnswers(), строка 264
backend/src/main/java/com/docforge/export/DocxExportServiceImpl.java — пример switch (String.valueOf(type))
```

---

## Файлы для изменения

```
backend/src/main/java/com/docforge/document/DocumentController.java — строка 264: switch (type) → switch (String.valueOf(type))
```

---

## Поэтапное исполнение

1. **Исправить** строку 264: заменить `switch (type)` на `switch (String.valueOf(type))`.
2. **Добавить** тест в существующий или новый тест-класс: узел с `type == null` не бросает исключение, а попадает в `default`.
3. **Запустить** полный тест-сьют.

---

## Автоматические тесты

### Что покрыть

| Сценарий | Тип теста | Файл |
|---|---|---|
| Узел без поля `type` не вызывает NPE при экспорте | unit | `NodeResolverTest.java` или новый `DocumentControllerExportTest.java` |

### Команды запуска

```bash
cd backend && ./gradlew test --tests "com.docforge.document.*"
cd backend && ./gradlew test --tests "com.docforge.export.*"
```

---

## Чеклист ручного тестирования

- [ ] Создать документ, сгенерировать, вручную испортить `currentBody` в MongoDB (добавить узел без `type`) → `GET /export/docx` должен вернуть 200, а не 500.
- [ ] Обычный экспорт не сломан: документ с заполненными ответами → DOCX содержит правильный текст.

---

## История

| Дата | Событие |
|---|---|
| 2026-04-23 | Задача создана по результатам ревью коммита 16f5208 |

---

## Завершение задачи

> **Обязательно выполнить перед отчётом о выполнении!**

- [ ] Автотесты запущены и проходят (`./gradlew test`)
- [ ] Ручной чеклист выполнен полностью
- [ ] Код прошёл ревью (`/review`)
- [ ] Git-коммит создан: `fix(export): guard switch against null node type`
- [ ] Статус обновлён в этом файле: `DONE`
- [ ] Реестр обновлён: [docs/tasks/README.md](README.md)
- [ ] GitHub Issue закрыт (state: closed) + комментарий с хешем коммита
