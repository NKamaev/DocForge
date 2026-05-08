# TASK-023-1: Backend — ConditionNode + рекурсивный evaluator

| Поле | Значение |
|---|---|
| **Дата** | 2026-04-18 |
| **Тип** | `feature` |
| **Статус** | `TODO` |
| **Агенты** | `/backend` |
| **Коммит** | — |
| **Зависит от** | — |
| **GitHub Issue** | [#36](https://github.com/NKamaev/DocForge/issues/36) |
| **Эпик** | [TASK-023](TASK-023-condition-groups.md) |

---

## Постановка задачи (от пользователя)

> Подзадача эпика TASK-023. Backend-часть: новая модель ConditionNode и рекурсивный evaluator.

---

## Интерпретация

Добавить рекурсивную структуру условий в backend. Текущий `ConditionEvaluator` разбирает плоский список — нужно расширить его поддержкой вложенных групп без ломки обратной совместимости.

---

## Файлы для изучения перед исполнением

```
backend/src/main/java/com/docforge/template/condition/
backend/src/main/java/com/docforge/document/fill/FillService.java (или аналог)
docs/architecture.md — секция условий/скриптов
```

---

## Файлы для изменения

```
backend/src/main/java/com/docforge/template/condition/ConditionNode.java   — новый sealed interface / record
backend/src/main/java/com/docforge/template/condition/Condition.java        — добавить поле root: ConditionNode
backend/src/main/java/com/docforge/template/condition/ConditionEvaluator.java — рекурсивный evaluate(ConditionNode)
```

---

## Модель данных

```java
// sealed interface (Java 21)
public sealed interface ConditionNode
    permits ConditionNode.Leaf, ConditionNode.Group {

    record Leaf(String field, String op, Object value) implements ConditionNode {}

    record Group(String operator, List<ConditionNode> children) implements ConditionNode {
        // operator: "AND" | "OR"
    }
}
```

```java
// Расширение существующего Condition (или аналога)
// Добавить поле:
ConditionNode root;   // null → использовать legacy expression
```

**Сериализация:** Jackson-полиморфизм по полю `"type": "leaf"` / `"type": "group"`, хранится в MongoDB.

---

## Поэтапное исполнение

1. **Изучить** текущую структуру `condition`-пакета и `ConditionEvaluator`.
2. **Создать** `ConditionNode` (sealed interface с `Leaf` и `Group`).
3. **Настроить** Jackson-аннотации для полиморфной десериализации (`@JsonTypeInfo`, `@JsonSubTypes`).
4. **Расширить** `Condition`: добавить поле `root`, сохранить legacy-поля.
5. **Обновить** `ConditionEvaluator`:
   - Если `condition.root != null` → вызвать `evaluate(ConditionNode, answers)`.
   - Иначе → legacy-путь (строковый expression).
   - `evaluate(Leaf)` — существующая логика сравнения.
   - `evaluate(Group)` — итерировать `children`, применить AND/OR.
6. **Написать** unit-тесты.

---

## Автоматические тесты

### Что покрыть

| Сценарий | Тип теста | Файл |
|---|---|---|
| `leaf` → простое сравнение eq/contains/gt | unit | `ConditionEvaluatorTest.java` |
| `group AND` → все children true | unit | `ConditionEvaluatorTest.java` |
| `group OR` → хотя бы один child true | unit | `ConditionEvaluatorTest.java` |
| Вложенная группа `(A AND B) OR C` | unit | `ConditionEvaluatorTest.java` |
| Legacy `expression` строка — обратная совместимость | unit | `ConditionEvaluatorTest.java` |
| Jackson round-trip: сериализация/десериализация `ConditionNode` | unit | `ConditionNodeSerializationTest.java` |

### Команды запуска

```bash
cd backend && ./gradlew test --tests "com.docforge.template.condition.*"
```

---

## Чеклист ручного тестирования

- [ ] Создать условие со вложенной группой через API (JSON), убедиться что сохраняется в Mongo.
- [ ] Вызвать генерацию документа, где условие — `(A AND B) OR C` → блок показывается корректно.
- [ ] Проверить, что старые шаблоны (legacy expression) продолжают работать.

---

## История

| Дата | Событие |
|---|---|
| 2026-04-18 | Подзадача создана |

---

## Завершение задачи

> **Обязательно выполнить перед отчётом о выполнении!**

- [ ] Автотесты запущены и проходят (`./gradlew test`)
- [ ] Ручной чеклист выполнен полностью
- [ ] Код прошёл ревью (`/review`)
- [ ] Git-коммит создан: `feat(conditions): ConditionNode model + recursive evaluator`
- [ ] Статус обновлён в этом файле: `DONE`
- [ ] Реестр обновлён: [docs/tasks/README.md](README.md)
