# TASK-024: Логические поля справочников в конструкторе условий

| Поле | Значение |
|---|---|
| **Дата** | 2026-04-18 |
| **Тип** | `feature` |
| **Статус** | `DONE` |
| **Агенты** | `/backend`, `/frontend` |
| **Коммит** | — |
| **Зависит от** | TASK-023 (ConditionNode на backend), TASK-023-2 (ConditionBuilder на frontend) |
| **GitHub Issue** | [#38](https://github.com/NKamaev/DocForge/issues/38) |

---

## Постановка задачи (от пользователя)

> Verbatim — из backlog.md Задача 9:

```
В конструкторе условий (ConditionBuilder.tsx) сейчас доступны только параметры шаблона.
Нужно добавить поля справочников (которые могут быть boolean, select и пр.)
в список доступных для условий.

Параметр типа DIRECTORY с directoryFields: ["is_active", "type"] — каждое поле
справочника доступно как bank__is_active, bank__type (уже работает для подстановки
в текст). Нужно то же самое раскрыть в ConditionBuilder.

ConditionEvaluator (backend) — уже умеет работать с bank__fieldKey из answers.
Проверить и покрыть тестом.
```

---

## Интерпретация

Параметры типа `DIRECTORY` ссылаются на объекты справочника. Каждый такой объект имеет поля (`schemaFields`), часть из которых может быть `boolean`, `select`, `text`, `number`. Эти поля уже доступны как `{paramCode}__{fieldKey}` в подстановках текста, но в конструкторе условий (`ConditionBuilder`) они не отображаются.

Нужно:
1. **Backend**: убедиться, что `ConditionEvaluator` корректно вычисляет условия вида `bank__is_active = true` — поле приходит из `answers["bank__is_active"]`, которое уже заполняется при генерации документа. Покрыть тестом.
2. **Frontend**: при загрузке параметров типа `DIRECTORY` в `ConditionBuilder` — подгружать схему справочника и добавлять производные поля `{code}__{fieldKey}` с корректными типами для выбора оператора и значения сравнения.

---

## Декомпозиция

| Подзадача | Файл | Статус |
|---|---|---|
| Backend: проверка и тест ConditionEvaluator для полей справочников | [TASK-024-1](TASK-024-1-directory-condition-evaluator.md) | `TODO` |
| Frontend: поля справочников в ConditionBuilder | [TASK-024-2](TASK-024-2-directory-fields-condition-builder.md) | `TODO` |

---

## Файлы для изучения перед исполнением

```
backend/src/main/java/com/docforge/template/condition/ConditionEvaluator.java
backend/src/main/java/com/docforge/template/condition/ConditionEvaluatorImpl.java
backend/src/main/java/com/docforge/directory/DirectoryService.java
frontend/src/components/template-designer/ConditionBuilder.tsx
frontend/src/types/template.ts          — ParameterType, DirectoryFieldType
frontend/src/api/directoryApi.ts        — метод для получения схемы справочника
docs/architecture.md                    — секции Directory, ConditionEvaluator
```

---

## История

| Дата | Событие |
|---|---|
| 2026-04-18 | Задача создана |
| 2026-04-18 | Выполнена: TASK-024-1 (37713fc) + TASK-024-2 (5588f0c), ревью пройдено |

---

## Завершение задачи

> **Обязательно выполнить перед отчётом о выполнении!**

- [ ] Автотесты запущены и проходят (`./gradlew test` / `npm test`)
- [ ] Ручной чеклист выполнен полностью
- [ ] Код прошёл ревью (`/review`)
- [ ] Git-коммит создан с сообщением: `feat(conditions): directory boolean/select fields available in condition builder`
- [ ] Статус обновлён в этом файле: `DONE`
- [ ] Реестр обновлён: [docs/tasks/README.md](README.md)
