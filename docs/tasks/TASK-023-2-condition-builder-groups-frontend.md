# TASK-023-2: Frontend — ConditionBuilder с группами AND/OR

| Поле | Значение |
|---|---|
| **Дата** | 2026-04-18 |
| **Тип** | `feature` |
| **Статус** | `TODO` |
| **Агенты** | `/frontend` |
| **Коммит** | — |
| **Зависит от** | [TASK-023-1](TASK-023-1-condition-node-backend.md) |
| **GitHub Issue** | [#37](https://github.com/NKamaev/DocForge/issues/37) |
| **Эпик** | [TASK-023](TASK-023-condition-groups.md) |

---

## Постановка задачи (от пользователя)

> Подзадача эпика TASK-023. Frontend-часть: визуальный конструктор условий с поддержкой вложенных групп.

---

## Интерпретация

Обновить `ConditionBuilder.tsx` для работы с рекурсивной моделью `ConditionNode`. Пользователь должен видеть вложенные блоки-группы с заголовком AND/OR и уметь добавлять новые группы кнопкой «+ Группа».

---

## Файлы для изучения перед исполнением

```
frontend/src/components/template-designer/ConditionBuilder.tsx
frontend/src/types/   — типы условий
frontend/src/api/     — API-вызовы шаблонов
```

---

## Файлы для изменения

```
frontend/src/types/condition.ts (или аналог)   — добавить ConditionNode, ConditionLeaf, ConditionGroup
frontend/src/components/template-designer/ConditionBuilder.tsx — рекурсивный рендер + кнопка группы
frontend/src/components/template-designer/ConditionGroup.tsx   — новый компонент (вложенная группа)
```

---

## TypeScript-типы

```typescript
export type ConditionLeaf = {
  type: 'leaf';
  field: string;
  op: string;
  value: unknown;
};

export type ConditionGroup = {
  type: 'group';
  operator: 'AND' | 'OR';
  children: ConditionNode[];
};

export type ConditionNode = ConditionLeaf | ConditionGroup;

export interface Condition {
  type: 'simple' | 'python';
  root?: ConditionNode;       // новый формат
  expression?: string;        // legacy
  scriptId?: string;
}
```

---

## Поэтапное исполнение

1. **Добавить** типы в `condition.ts`.
2. **Создать** `ConditionGroup.tsx` — компонент вложенного блока:
   - Заголовок `AND` / `OR` (Select или ToggleButton).
   - Список дочерних `ConditionNode` (рекурсивный рендер).
   - Кнопки «+ Условие» и «+ Группа» внутри блока.
   - Кнопка удаления группы.
   - Визуальный отступ + рамка (Ant Design `Card` или кастомный `div`).
3. **Обновить** `ConditionBuilder.tsx`:
   - Хранить состояние как `ConditionNode` (корневой `group`).
   - Кнопка «+ Группа» на верхнем уровне.
   - Сериализовать в `condition.root` при сохранении.
   - Legacy-режим: если приходит `condition.expression` (строка) — показывать read-only поле.
4. **Написать** тест компонента.

---

## Автоматические тесты

### Что покрыть

| Сценарий | Тип теста | Файл |
|---|---|---|
| Рендер `ConditionGroup` с двумя leaf-детьми | unit | `ConditionBuilder.test.tsx` |
| Переключение AND/OR внутри группы | unit | `ConditionBuilder.test.tsx` |
| Добавление вложенной группы через «+ Группа» | unit | `ConditionBuilder.test.tsx` |
| Удаление условия из вложенной группы | unit | `ConditionBuilder.test.tsx` |
| Сериализация в `condition.root` при onChange | unit | `ConditionBuilder.test.tsx` |

### Команды запуска

```bash
cd frontend && npm test -- --run ConditionBuilder
```

---

## Чеклист ручного тестирования

- [ ] Открыть конструктор шаблона → нода с условием → «Редактировать условие».
- [ ] Нажать «+ Группа» → появляется вложенный блок с AND/OR.
- [ ] Добавить условие внутрь группы → работает независимо от внешнего уровня.
- [ ] Переключить оператор AND ↔ OR → сохраняется корректно.
- [ ] Удалить группу → дети группы исчезают.
- [ ] Сохранить шаблон → перезагрузить → структура групп восстановилась.
- [ ] Создать документ из шаблона с группой `(A AND B) OR C` → условный блок скрывается/показывается правильно.
- [ ] Старый шаблон с `expression` строкой — редактор отображает legacy-режим без ошибок.

---

## История

| Дата | Событие |
|---|---|
| 2026-04-18 | Подзадача создана |

---

## Завершение задачи

> **Обязательно выполнить перед отчётом о выполнении!**

- [ ] Автотесты запущены и проходят (`npm test`)
- [ ] Ручной чеклист выполнен полностью
- [ ] Код прошёл ревью (`/review`)
- [ ] Git-коммит создан: `feat(conditions): ConditionBuilder with nested AND/OR groups`
- [ ] Статус обновлён в этом файле: `DONE`
- [ ] Реестр обновлён: [docs/tasks/README.md](README.md)
