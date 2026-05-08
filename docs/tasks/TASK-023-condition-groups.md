# TASK-023: Логические группы условий (скобки)

| Поле | Значение |
|---|---|
| **Дата** | 2026-04-18 |
| **Тип** | `feature` |
| **Статус** | `TODO` |
| **Агенты** | `/backend`, `/frontend` |
| **Коммит** | — |
| **Зависит от** | — |
| **GitHub Issue** | [#35](https://github.com/NKamaev/DocForge/issues/35) |

---

## Постановка задачи (от пользователя)

> Из backlog.md Задача 8. Логические группы условий (скобки)

```
В визуальном конструкторе условий (ConditionBuilder.tsx) добавить возможность
группировать условия в скобки с операторами AND/OR. Пример: (A AND B) OR C.
```

---

## Интерпретация

Текущая структура условий плоская — список пар `field op value` с одним оператором AND/OR между ними. Нужна рекурсивная модель `ConditionNode` с типами `leaf` (простое условие) и `group` (вложенная группа с AND/OR детьми).

**Ожидаемый результат:**
- Backend: новый рекурсивный evaluator, обратно-совместимый со старым форматом `expression`.
- Frontend: в `ConditionBuilder.tsx` кнопка «+ Группа», вложенные блоки с отступом, переключатель AND/OR на уровне группы.
- Сериализация `ConditionNode` хранится в MongoDB в поле `condition.root` шаблонного узла.

---

## Декомпозиция

| Подзадача | Файл | Статус |
|---|---|---|
| 1. Backend: ConditionNode + рекурсивный evaluator | [TASK-023-1-condition-node-backend.md](TASK-023-1-condition-node-backend.md) | `TODO` |
| 2. Frontend: ConditionBuilder с группами | [TASK-023-2-condition-builder-groups-frontend.md](TASK-023-2-condition-builder-groups-frontend.md) | `TODO` |

---

## Файлы для изучения перед исполнением

```
docs/architecture.md
frontend/src/components/template-designer/ConditionBuilder.tsx
backend/src/main/java/com/docforge/template/condition/ConditionEvaluator.java
backend/src/main/java/com/docforge/template/condition/Condition.java (или аналог)
```

---

## История

| Дата | Событие |
|---|---|
| 2026-04-18 | Задача создана, декомпозирована на 2 подзадачи |

---

## Завершение задачи

> **Обязательно выполнить перед отчётом о выполнении!**

- [ ] Автотесты запущены и проходят (`./gradlew test` / `npm test`)
- [ ] Ручной чеклист выполнен полностью (обе подзадачи)
- [ ] Код прошёл ревью (`/review`)
- [ ] Git-коммит создан: `feat(conditions): logical condition groups (parentheses) — nested AND/OR in ConditionBuilder`
- [ ] Статус обновлён в этом файле: `DONE`
- [ ] Реестр обновлён: [docs/tasks/README.md](README.md)
