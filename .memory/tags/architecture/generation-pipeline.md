# Pipeline генерации документа и условия вариативности

> Тег: `architecture:generation-pipeline` · Обновлено: `2026-10-10`

## [2026-10-10 10:00] DocumentGenerator — 4 шага

- **Факт:** `DocumentGenerator.java` (372 строки), вход `generate(Template, answers)` (:61):
  1. Валидация required + тип + min/max/pattern (`validateAnswers` :165, `validateType` :193, `validateConstraints` :222). DIRECTORY принимает любой non-blank entryId (:217).
  2+3. Один проход `TipTapTreeWalker.transform` (:74-142): `conditionalBlock` — удалён при false, развёрнут при true; `numberedClause` с `condition` — отфильтрован; `phrasingsRef` — развёрнут в первый подходящий вариант (легаси `phrasingsId` :316-324; разделитель "," или `\n`→hardBreak :327-332).
  4. Второй проход: `parameterField` → отформатированный `text` с учётом `attrs.format`-переопределения (`parseFormat` :353).
- **TreeWalker:** рекурсивный; `NodeTransformer` возвращает `[]`(удалить)/`[node]`(оставить/заменить)/`[a,b]`(развернуть); рекурсия в подмены (:55-89).
- **Источник:** `backend/src/main/java/com/docforge/document/generator/DocumentGenerator.java`.

## Оценка условий

- **Simple** (`ConditionEvaluator`): операторы `== != > < >= <= in not_in is_empty is_not_empty` (:116-127); связка `\s+(AND|OR)\s+` case-insensitive (:34-35); **слево-направо, без приоритета** (:54-59); сравнения через Double (:145-157); равенство с приведением Boolean/Number/String (:138-143).
- **Python** (`PythonConditionEvaluator`): GraalPython sandbox, `TIMEOUT_SECONDS=2` (:44); `allowIO(NONE)`, без native/threads, `HostAccess.NONE`, `python.PythonPath=""` (:124-131); параметры → bindings, null пропускаются (:133-142); результат обязан быть bool (:91-99); таймаут — `context.close(true)` на общем daemon-потоке (:50-55,80-86).
- **Различие фолбэков:** Python-сбой в видимости вопроса ОЛ → treat as true (`DocumentServiceImpl.evalCondition` :342); Python-сбой в conditional_block генерации → false (`DocumentGenerator.java:282`).

## Экспорт документа через контроллер

- `DocumentController` (:170-221): берёт `currentBody` иначе `generatedBody`; параметры подставляет через `treeWalker.transform` + `substitutor.format` (`applyAnswers` :309-340); форматирование из snapshot (фолбэк — live template) (:364-379); `sanitizeFilename` (:381).
- **Источник:** backend explore report.

<!-- Новые записи добавляй СВЕРХУ. -->