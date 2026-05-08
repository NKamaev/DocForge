# BUG-015: Таблица вставляется с заливкой первой строки (withHeaderRow: true)

| Поле | Значение |
|---|---|
| **Дата** | 2026-04-18 |
| **Тип** | `bug` |
| **Статус** | `TODO` |
| **Агенты** | `/frontend` |
| **Коммит** | — |
| **Зависит от** | — |
| **GitHub Issue** | [#44](https://github.com/NKamaev/DocForge/issues/44) |

---

## Постановка задачи (от пользователя)

```
Нужно создавать таблицы без заливки, сейчас создается с заливкой первой строки
```

---

## Интерпретация

При вставке таблицы через тулбар, FloatingInsertMenu или slash-команду первая строка рендерится как `<th>` (header row) с фоновой заливкой. Пользователь ожидает обычную таблицу без выделенного заголовка.

---

## Причина ошибки

**Корень проблемы:**  
Во всех трёх точках вызова `insertTable` передаётся `withHeaderRow: true`, что заставляет TipTap создать первую строку из `TableHeader`-ячеек вместо обычных `TableCell`.

**Цепочка сбоя:**  
1. Пользователь нажимает кнопку/команду вставки таблицы.
2. Вызывается `editor.chain().focus().insertTable({ rows: 3, cols: 3, withHeaderRow: true }).run()`.
3. TipTap создаёт первую строку из `<th>`-ячеек, CSS даёт им фоновый цвет.
4. Таблица выглядит с «заливкой» первой строки.

---

## Файлы для изучения перед исполнением

```
frontend/src/components/template-designer/EditorToolbar.tsx
frontend/src/components/template-designer/FloatingInsertMenu.tsx
frontend/src/components/template-designer/extensions/SlashCommandExtension.ts
```

---

## Файлы для изменения

```
frontend/src/components/template-designer/EditorToolbar.tsx:123         — withHeaderRow: true → false
frontend/src/components/template-designer/FloatingInsertMenu.tsx:102    — withHeaderRow: true → false
frontend/src/components/template-designer/extensions/SlashCommandExtension.ts:57 — withHeaderRow: true → false
```

---

## Поэтапное исполнение

1. В `EditorToolbar.tsx` строка 123: заменить `withHeaderRow: true` на `withHeaderRow: false`.
2. В `FloatingInsertMenu.tsx` строка 102: заменить `withHeaderRow: true` на `withHeaderRow: false`.
3. В `SlashCommandExtension.ts` строка 57: заменить `withHeaderRow: true` на `withHeaderRow: false`.

---

## Автоматические тесты

### Что покрыть

| Сценарий | Тип теста | Файл |
|---|---|---|
| После вставки таблицы ни одна ячейка не является `tableHeader` | unit | `frontend/src/components/template-designer/EditorToolbar.test.tsx` |

### Команды запуска

```bash
cd frontend && npm test -- --run
```

---

## Чеклист ручного тестирования

- [ ] Открыть конструктор шаблонов → вставить таблицу кнопкой тулбара → первая строка без заливки
- [ ] Открыть конструктор шаблонов → вставить таблицу через FloatingInsertMenu (+) → первая строка без заливки
- [ ] Открыть конструктор шаблонов → вставить таблицу через slash-команду `/Таблица` → первая строка без заливки
- [ ] Регрессия: убедиться что ячейки таблицы редактируются, добавление строк/колонок работает

---

## История

| Дата | Событие |
|---|---|
| 2026-04-18 | Задача создана |

---

## Завершение задачи

> **Обязательно выполнить перед отчётом о выполнении!**

- [ ] Автотесты запущены и проходят (`npm test`)
- [ ] Ручной чеклист выполнен полностью
- [ ] Код прошёл ревью (`/review`)
- [ ] Git-коммит создан с сообщением: `fix(template-designer): insert table without header row`
- [ ] Статус обновлён в этом файле: `DONE`
- [ ] Реестр обновлён: [docs/tasks/README.md](README.md)
