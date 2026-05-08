# TASK-024-2: Frontend — поля справочников в ConditionBuilder

| Поле | Значение |
|---|---|
| **Дата** | 2026-04-18 |
| **Тип** | `feature` |
| **Статус** | `DONE` |
| **Агенты** | `/frontend` |
| **Коммит** | 5588f0c |
| **Зависит от** | TASK-023-2 (ConditionBuilder с группами AND/OR), TASK-024-1 |
| **GitHub Issue** | [#40](https://github.com/NKamaev/DocForge/issues/40) |

---

## Постановка задачи

В `ConditionBuilder.tsx` список доступных полей для условий сейчас содержит только параметры шаблона. Нужно раскрыть параметры типа `DIRECTORY` до уровня полей справочника — добавить производные поля `{paramCode}__{fieldKey}` с корректными типами.

---

## Интерпретация

Когда пользователь открывает конструктор условий и видит список доступных полей:
- Параметр `bank` (DIRECTORY) должен раскрываться в: `bank__name (текст)`, `bank__is_active (boolean)`, `bank__type (выбор из списка)` и т.д.
- Для каждого производного поля — правильный набор операторов и компонент ввода значения (чекбокс для boolean, select для enum, input для текста/числа).

Схема полей справочника (`schemaFields`) уже доступна через API справочников.

---

## Файлы для изучения перед исполнением

```
frontend/src/components/template-designer/ConditionBuilder.tsx
frontend/src/types/template.ts          — ParameterType, DirectoryFieldType, Parameter
frontend/src/types/directory.ts         — Directory, SchemaField, FieldType
frontend/src/api/directoryApi.ts        — getDirectory(id), getDirectorySchema(id)
frontend/src/api/templateApi.ts         — getTemplate, getParameters
```

---

## Файлы для изменения

```
frontend/src/components/template-designer/ConditionBuilder.tsx
  — при маппинге параметров: если type=DIRECTORY, раскрывать schemaFields в производные поля
  — для каждого производного поля: label="{paramLabel} → {fieldLabel}", key="{code}__{fieldKey}"
  — тип оператора и компонент значения — по типу поля справочника (boolean/select/text/number)
frontend/src/types/template.ts (при необходимости)
  — добавить вспомогательный тип DerivedConditionField
frontend/src/hooks/useDirectorySchema.ts (создать если нет)
  — хук для загрузки схемы справочника по directoryId параметра
```

---

## Поэтапное исполнение

1. **Изучить** структуру `ConditionBuilder` — как формируется список полей, как рендерятся операторы и значения.
2. **Добавить хук** `useDirectorySchema(directoryId)` или встроить загрузку схемы в `ConditionBuilder` — при наличии параметров типа `DIRECTORY` подгружать их схемы.
3. **Расширить** маппинг полей: для каждого параметра `DIRECTORY` с загруженной схемой — добавить производные поля `{code}__{fieldKey}`.
4. **Маппинг типов** полей справочника → операторы условия:
   - `boolean` → `= (равно)` + чекбокс / select `true/false`
   - `select` → `=`, `!=` + select из допустимых значений
   - `text` → `=`, `!=`, `contains`
   - `number` → `=`, `!=`, `>`, `<`, `>=`, `<=`
5. **Протестировать**: создать условие `bank__is_active = true` в конструкторе, сохранить шаблон, проверить что условие вычисляется корректно.

---

## Автоматические тесты

### Что покрыть

| Сценарий | Тип теста | Файл |
|---|---|---|
| Параметр DIRECTORY раскрывается в производные поля | unit | `ConditionBuilder.test.tsx` |
| boolean-поле: операторы только `=` / `!=` | unit | `ConditionBuilder.test.tsx` |
| select-поле: значения берутся из enum-списка | unit | `ConditionBuilder.test.tsx` |

### Команды запуска

```bash
cd frontend && npm test -- --run src/components/template-designer/ConditionBuilder.test.tsx
```

---

## Чеклист ручного тестирования

- [ ] Открыть конструктор условий в шаблоне с параметром DIRECTORY — поля справочника видны в списке
- [ ] Для boolean-поля: доступны операторы `=`/`!=`, значения `true`/`false`
- [ ] Для select-поля: доступны значения из схемы справочника
- [ ] Сохранить условие `bank__is_active = true`, открыть /fill — блок корректно показывается/скрывается
- [ ] Регрессия: параметры не-DIRECTORY типов отображаются без изменений

---

## История

| Дата | Событие |
|---|---|
| 2026-04-18 | Задача создана |
| 2026-04-18 | Выполнена: коммит 5588f0c, ревью пройдено (4 WARNING → TASK-030) |

---

## Завершение задачи

> **Обязательно выполнить перед отчётом о выполнении!**

- [ ] Автотесты запущены и проходят (`npm test`)
- [ ] Ручной чеклист выполнен полностью
- [ ] Код прошёл ревью (`/review`)
- [ ] Git-коммит создан с сообщением: `feat(conditions): directory fields available in ConditionBuilder`
- [ ] Статус обновлён в этом файле: `DONE`
- [ ] Реестр обновлён: [docs/tasks/README.md](README.md)
