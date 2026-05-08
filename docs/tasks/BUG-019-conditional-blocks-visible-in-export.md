# BUG-019: Скрытые по условию блоки попадают в DOCX и PDF

| Поле | Значение |
|---|---|
| **Дата** | 2026-04-19 |
| **Тип** | `bug` |
| **Статус** | `TODO` |
| **Агенты** | `/backend` |
| **Коммит** | — |
| **Зависит от** | BUG-018 (введён в 9a4b520) |
| **GitHub Issue** | [#65](https://github.com/NKamaev/DocForge/issues/65) |

---

## Постановка задачи (от пользователя)

```
Теперь в docx и pdf выгружаются пункты, которые должны быть скрыты по вариативности
```

---

## Интерпретация

После BUG-018 экспорт начал корректно подставлять параметры, но при этом обнаружилась проблема с условными блоками: `conditionalBlock` и `conditionalInline` в `DocxExportServiceImpl` / `PdfExportServiceImpl` рендерятся **всегда** — без проверки условий. В результате пункты, которые должны быть скрыты (условие ложно), попадают в финальный документ.

Логика должна быть аналогична `DocumentController.applyAnswers` — перед рендерингом экспорта нужно применить ещё и фильтрацию условных блоков через `ConditionEvaluator`.

---

## Причина ошибки

**Корень проблемы:**  
В `DocxExportServiceImpl.renderNode()` и `PdfExportServiceImpl.renderNode()` для узла `conditionalBlock` происходит безусловный рендеринг всех дочерних узлов:

```java
case "conditionalBlock" -> {
    if (children != null) children.forEach(c -> renderNode(doc, c, ctx));
}
```

В `DocumentController.applyAnswers()` (добавлен в BUG-018) применяется только замена `parameterField` — условные блоки не фильтруются.

**Цепочка сбоя:**
1. `currentBody` хранит `conditionalBlock`-узлы с атрибутом `condition` (expression + type).
2. `DocumentController.exportDocx/exportPdf` передаёт тело в сервис без фильтрации условий.
3. Экспортный сервис рендерит все дочерние узлы `conditionalBlock` вне зависимости от `condition`.
4. Пользователь видит скрытые пункты в файле.

---

## Файлы для изучения перед исполнением

```
backend/src/main/java/com/docforge/document/DocumentController.java      — applyAnswers(), export handlers
backend/src/main/java/com/docforge/export/DocxExportServiceImpl.java     — renderNode() conditionalBlock
backend/src/main/java/com/docforge/export/PdfExportServiceImpl.java      — renderNode() conditionalBlock
backend/src/main/java/com/docforge/document/generator/ConditionEvaluator.java  — evaluator для simple-условий
backend/src/main/java/com/docforge/document/generator/TipTapTreeWalker.java    — уже используется в fix BUG-018
backend/src/main/java/com/docforge/document/generator/DocumentGenerator.java   — эталон: как filterConditionalBlocks делается при generate()
```

---

## Файлы для изменения

```
backend/src/main/java/com/docforge/document/DocumentController.java
  — расширить applyAnswers(): добавить фильтрацию conditionalBlock и conditionalInline
  — инжектировать ConditionEvaluator

backend/src/test/java/com/docforge/export/DocxExportServiceImplTest.java  — добавить тест
backend/src/test/java/com/docforge/export/PdfExportServiceImplTest.java   — добавить тест
```

---

## Поэтапное исполнение

1. **Диагностика** — убедиться, что `conditionalBlock` в `currentBody` содержит атрибут `condition` с `expression` и `type`.
2. **Расширить `applyAnswers()`** в `DocumentController`:
   - Добавить `ConditionEvaluator` (и `PythonConditionEvaluator`) как зависимости контроллера.
   - В трансформере TipTapTreeWalker добавить обработку `conditionalBlock`:
     - Если `condition.expression` пустой → рендерить содержимое (блок без условия).
     - Если условие истинно → вернуть дочерние узлы (unwrap, как в `DocumentGenerator`).
     - Если условие ложно → вернуть `[]` (удалить блок).
   - Аналогично обработать `conditionalInline`.
   - `numberedClause` с `condition` — фильтровать по тому же принципу.
3. **Тесты** — добавить сценарии с conditionalBlock (истинное / ложное условие) в DocxExportServiceImplTest и PdfExportServiceImplTest.
4. **Запуск тестов** — `./gradlew test --tests "com.docforge.export.*"`.
5. **Коммит**.

---

## Автоматические тесты

| Сценарий | Тип теста | Файл |
|---|---|---|
| conditionalBlock с ложным условием не попадает в DOCX | unit | `DocxExportServiceImplTest.java` |
| conditionalBlock с истинным условием присутствует в DOCX | unit | `DocxExportServiceImplTest.java` |
| conditionalBlock с ложным условием не попадает в PDF | unit | `PdfExportServiceImplTest.java` |

### Команды запуска

```bash
cd backend && ./gradlew test --tests "com.docforge.export.*"
```

---

## Чеклист ручного тестирования

- [ ] Открыть документ на `/fill`, убедиться что несколько условий в опроснике
- [ ] Ответить так, чтобы часть блоков была скрыта
- [ ] Скачать DOCX → скрытые блоки отсутствуют
- [ ] Скачать PDF → скрытые блоки отсутствуют
- [ ] Регрессия: параметры по-прежнему подставляются корректно (BUG-018)

---

## История

| Дата | Событие |
|---|---|
| 2026-04-19 | Задача создана |

---

## Завершение задачи

> **Обязательно выполнить перед отчётом о выполнении!**

- [ ] Автотесты запущены и проходят (`./gradlew test`)
- [ ] Ручной чеклист выполнен полностью
- [ ] Код прошёл ревью (`/review`)
- [ ] Git-коммит создан с сообщением: `fix(export): filter conditionalBlock by condition before export`
- [ ] Статус обновлён в этом файле: `DONE`
- [ ] Реестр обновлён: [docs/tasks/README.md](README.md)
- [ ] GitHub Issue закрыт (state: closed) + комментарий с хешем коммита
