# TASK-005: Заблокировать collapse ConditionalBlock в published-режиме

| Поле | Значение |
|---|---|
| **Дата** | 2026-04-16 |
| **Тип** | `refactor` |
| **Статус** | `DONE` |
| **Агенты** | `/frontend` |
| **Коммит** | `eba8b87` |
| **Зависит от** | BUG-002 |
| **GitHub Issue** | [#5](https://github.com/NKamaev/DocForge/issues/5) |

---

## Постановка задачи (от пользователя)

> Найдено в ревью BUG-002 (SUGGESTION). В `ConditionalBlockNode` кнопка свернуть/развернуть (collapse) вызывает `updateAttributes({ collapsed: !collapsed })` в published-режиме. Изменение не сохраняется (save задисейблен), но нарушает принцип «в published всё read-only».

---

## Интерпретация

Кнопка collapse в хедере условного блока должна быть неактивна (или скрыта) при `published === true`. Визуально блок при этом всегда показывается развёрнутым (или в том состоянии, в котором был сохранён — но без возможности переключить).

---

## Файлы для изучения

```
frontend/src/components/template-designer/extensions/ConditionalBlockNode.tsx
  — кнопка collapse ~строка 74–81
```

---

## Файлы для изменения

```
frontend/src/components/template-designer/extensions/ConditionalBlockNode.tsx
  — задизейблить или скрыть кнопку collapse при published
```

---

## Поэтапное исполнение

1. В `ConditionalBlockView` получить `published` из `useDesignerContext()` (уже добавлен в BUG-002).
2. Найти кнопку свернуть/развернуть:
   ```tsx
   <Button ... onClick={() => updateAttributes({ collapsed: !collapsed })} />
   ```
3. Добавить `disabled={published}` или скрыть при `published`.
4. `npx tsc --noEmit` и `npm test -- --run`.

---

## Автоматические тесты

Достаточно ручной проверки.

---

## Чеклист ручного тестирования

- [ ] Открыть published-шаблон с условным блоком → кнопка collapse задисейблена / отсутствует
- [ ] Черновик → кнопка collapse работает в штатном режиме (регрессия)

---

## История

| Дата | Событие |
|---|---|
| 2026-04-16 | Обнаружено в ревью BUG-002 (SUGGESTION) |

---

## Завершение задачи

> **Обязательно выполнить перед отчётом о выполнении!**

- [ ] Автотесты запущены и проходят (`npm test -- --run`)
- [ ] Ручной чеклист выполнен полностью
- [ ] Код прошёл ревью (`/review`)
- [ ] Git-коммит создан: `fix(template): disable collapse button in published mode`
- [ ] Статус обновлён: DONE
- [ ] Реестр обновлён: docs/tasks/README.md
