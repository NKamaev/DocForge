# TASK-024-1: Backend — ConditionEvaluator для полей справочников

| Поле | Значение |
|---|---|
| **Дата** | 2026-04-18 |
| **Тип** | `feature` |
| **Статус** | `DONE` |
| **Агенты** | `/backend` |
| **Коммит** | 37713fc |
| **Зависит от** | TASK-023-1 (ConditionNode + рекурсивный evaluator) |
| **GitHub Issue** | [#39](https://github.com/NKamaev/DocForge/issues/39) |

---

## Постановка задачи

Убедиться, что `ConditionEvaluator` корректно вычисляет условия вида `bank__is_active = true`, где значение `bank__is_active` берётся из `answers` документа. Если логика не реализована — реализовать. Покрыть тестом.

---

## Интерпретация

При генерации документа в `answers` сохраняются поля вида `bank__fieldKey` — заполненные значения из выбранной записи справочника. В `ConditionEvaluator` при вычислении leaf-условия нужно:
1. Искать `answers[field]` — для полей справочников ключ уже имеет форму `bank__is_active`.
2. Корректно сравнивать типы: `boolean` (`true`/`false`), `number`, `text`, `select` (enum-строка).

Никакой дополнительной раскрутки не требуется — `answers` уже содержат нужные ключи.

---

## Файлы для изучения перед исполнением

```
backend/src/main/java/com/docforge/template/condition/ConditionEvaluator.java
backend/src/main/java/com/docforge/template/condition/ConditionEvaluatorImpl.java
backend/src/main/java/com/docforge/document/DocumentServiceImpl.java   — как заполняется answers
backend/src/main/java/com/docforge/directory/DirectoryService.java
```

---

## Файлы для изменения

```
backend/src/main/java/com/docforge/template/condition/ConditionEvaluatorImpl.java
  — убедиться, что leaf-оценка работает для ключей вида {param}__{field}
  — добавить поддержку типов boolean/select если не реализована
backend/src/test/java/com/docforge/template/condition/ConditionEvaluatorTest.java (создать если нет)
  — тесты для bank__is_active = true, bank__type = "ВТБ", bank__rating > 3
```

---

## Поэтапное исполнение

1. **Изучить** `ConditionEvaluatorImpl` — как именно вычисляется `leaf`-условие, откуда берётся `answers`.
2. **Проверить** работу на примере: условие `bank__is_active == true`, `answers = {"bank__is_active": true}` — должно возвращать `true`.
3. **Исправить** если нужно: приведение типов при сравнении (`"true"` vs `true`, `"3"` vs `3`).
4. **Написать** юнит-тест `ConditionEvaluatorTest`:
   - leaf с boolean-полем справочника
   - leaf с select-полем (enum-строка)
   - leaf с числовым полем
   - group AND с двумя полями справочника

---

## Автоматические тесты

### Что покрыть

| Сценарий | Тип теста | Файл |
|---|---|---|
| `bank__is_active = true` → true | unit | `ConditionEvaluatorTest.java` |
| `bank__type = "ВТБ"` → true / false | unit | `ConditionEvaluatorTest.java` |
| `bank__rating > 3` → true / false | unit | `ConditionEvaluatorTest.java` |
| Группа AND из двух полей справочника | unit | `ConditionEvaluatorTest.java` |

### Команды запуска

```bash
cd backend && ./gradlew test --tests "com.docforge.template.condition.ConditionEvaluatorTest"
```

---

## Чеклист ручного тестирования

- [ ] В шаблоне: параметр `bank` (DIRECTORY), условный блок с `bank__is_active = true` — при заполнении ОЛ блок скрыт/показан корректно
- [ ] Проверить в /fill: выбрать запись справочника с `is_active=false` → блок скрыт; с `is_active=true` → блок виден
- [ ] Регрессия: существующие условия по текстовым параметрам работают без изменений

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
- [ ] Git-коммит создан с сообщением: `feat(conditions): verify and test ConditionEvaluator for directory fields`
- [ ] Статус обновлён в этом файле: `DONE`
- [ ] Реестр обновлён: [docs/tasks/README.md](README.md)
