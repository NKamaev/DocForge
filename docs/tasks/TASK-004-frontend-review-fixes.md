# TASK-004: Frontend — доработки по ревью TASK-002

| Поле | Значение |
|---|---|
| **Дата** | 2026-04-16 |
| **Тип** | `refactor` |
| **Статус** | `DONE` |
| **Агенты** | `/frontend` |
| **Коммит** | `369c70e` |
| **Зависит от** | TASK-002 |
| **GitHub Issue** | [#4](https://github.com/NKamaev/DocForge/issues/4) |

---

## Постановка задачи (от пользователя)

> Из ревью TASK-002 (код-ревьюер, 2026-04-16).

```
SUGGESTION: Drag-handle в ParameterPanel остаётся активным в readOnly-режиме —
  пользователь не может изменить параметры, но перетаскивание доступно визуально.
SUGGESTION: Обработчик Ctrl+S — `published` не входит в deps useEffect,
  что может вызвать stale-closure при будущих рефакторингах.
```

---

## Интерпретация

Две небольшие фронтенд-доработки:

1. **Отключить drag-handle в readOnly** — в `SortableRow` (ParameterPanel) при `readOnly=true` не передавать `listeners` на drag-handle, чтобы перетаскивание было недоступно визуально и функционально.
2. **Добавить `published` в deps** — в `TemplateDesigner` добавить `published` в массив зависимостей `useEffect` обработчика клавиатуры (Ctrl+S).

---

## Файлы для изучения перед исполнением

```
frontend/src/components/template-designer/ParameterPanel.tsx  — SortableRow, drag listeners
frontend/src/components/template-designer/TemplateDesigner.tsx — useEffect Ctrl+S (строка ~376)
```

---

## Файлы для изменения

```
frontend/src/components/template-designer/ParameterPanel.tsx
  — в SortableRow: ...(readOnly ? {} : listeners) на drag-handle-элементе

frontend/src/components/template-designer/TemplateDesigner.tsx
  — добавить published в deps useEffect для Ctrl+S
```

---

## Поэтапное исполнение

1. **ParameterPanel** — в `SortableRow` найти элемент с `{...listeners}` и заменить на `...(readOnly ? {} : listeners)`. Аналогично для `style` drag-handle (cursor: grab → default при readOnly).
2. **TemplateDesigner** — найти `useEffect` с `window.addEventListener('keydown', handler)` и добавить `published` в deps-массив.
3. **Тест** — обновить/добавить тест в `ParameterPanel.test.tsx`: убедиться, что в readOnly-режиме нельзя перетащить строку (нет drag attrs).
4. **Прогон** — `npm test -- --run src/components/template-designer`.

---

## Автоматические тесты

### Что покрыть

| Сценарий | Тип теста | Файл |
|---|---|---|
| SortableRow в readOnly: нет drag-атрибутов на handle | unit (React) | `ParameterPanel.test.tsx` |

### Команды запуска

```bash
cd frontend && npm test -- --run src/components/template-designer
```

---

## Чеклист ручного тестирования

- [ ] Открыть опубликованный шаблон → вкладка «Параметры» → попытка drag-and-drop параметра → не работает
- [ ] Открыть черновик → drag-and-drop параметров работает (регрессия)
- [ ] Ctrl+S в опубликованном шаблоне → сохранение не вызывается

---

## История

| Дата | Событие |
|---|---|
| 2026-04-16 | Задача создана по результатам ревью TASK-002 |

---

## Завершение задачи

> **Обязательно выполнить перед отчётом о выполнении!**

- [ ] Автотесты запущены и проходят (`npm test`)
- [ ] Ручной чеклист выполнен полностью
- [ ] Код прошёл ревью (`/review`)
- [ ] Git-коммит создан с сообщением: `fix(template): disable drag in readOnly, fix useEffect deps`
- [ ] Статус обновлён в этом файле: `DONE`
- [ ] Реестр обновлён: [docs/tasks/README.md](README.md)
