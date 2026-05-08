# BUG-013: WorkspaceConditionalInlineNode — два разных return вызывают перемонтирование

| Поле | Значение |
|---|---|
| **Дата** | 2026-04-18 |
| **Тип** | `bug` |
| **Статус** | `TODO` |
| **Агенты** | `/frontend` |
| **Коммит** | — |
| **Зависит от** | — |
| **GitHub Issue** | [#31](https://github.com/NKamaev/DocForge/issues/31) |

---

## Постановка задачи (от пользователя)

Найдено в ходе ревью BUG-012.

---

## Интерпретация

`WorkspaceConditionalInlineNode.tsx` содержит два отдельных `return` с разными структурами `NodeViewWrapper`. При переключении `visible` React размонтирует и перемонтирует компонент, что может вызвать мерцание или ошибки TipTap/Yjs.

---

## Причина ошибки

**Корень проблемы:**
```tsx
if (!visible) return <NodeViewWrapper as="span" style={{ display: 'none' }}>...
return <NodeViewWrapper as="span" style={{ display: 'inline' }}>...
```
Два return с разными JSX-деревьями → React считает их разными компонентами → unmount/mount при каждом переключении ответа.

**Блочная версия** (`WorkspaceConditionalBlockNode`) этой проблемы не имеет: использует единый return с условным `display`.

---

## Файлы для изменения

```
frontend/src/components/questionnaire/WorkspaceConditionalInlineNode.tsx
```

---

## Поэтапное исполнение

1. Заменить два return на единый:
```tsx
return (
  <NodeViewWrapper as="span" style={{ display: visible ? 'inline' : 'none' }}>
    <NodeViewContent style={{ display: 'inline' }} />
  </NodeViewWrapper>
)
```

---

## Автоматические тесты

Существующие тесты в `WorkspaceConditionalBlock.test.tsx` покрывают `evaluateSimple` и `renderPreview`. Добавить тест на `conditionalInline` с `preserveConditionalBlocks: true`.

---

## Чеклист ручного тестирования

- [ ] Открыть `/fill`, изменить ответ на вопрос с inline-условием → нет мерцания
- [ ] Регрессия: обычные условные блоки (`conditionalBlock`) работают корректно

---

## История

| Дата | Событие |
|---|---|
| 2026-04-18 | Задача создана по результатам ревью BUG-012 |

---

## Завершение задачи

> **Обязательно выполнить перед отчётом о выполнении!**

- [ ] Автотесты запущены и проходят (`npm test`)
- [ ] Ручной чеклист выполнен полностью
- [ ] Код прошёл ревью (`/review`)
- [ ] Git-коммит создан: `fix(fill): single-return in WorkspaceConditionalInlineNode`
- [ ] Статус обновлён: DONE
- [ ] Реестр обновлён: docs/tasks/README.md
