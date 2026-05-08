# TASK-026: Кнопка «Две колонки» — таблица 2×1 без заливки и границ

| Поле | Значение |
|---|---|
| **Дата** | 2026-04-18 |
| **Тип** | `feature` |
| **Статус** | `TODO` |
| **Агенты** | `/frontend` |
| **Коммит** | — |
| **Зависит от** | [BUG-015](BUG-015-table-inserted-with-header-row.md) (опционально — независимо от статуса BUG-015 можно делать параллельно) |
| **GitHub Issue** | [#45](https://github.com/NKamaev/DocForge/issues/45) |

---

## Постановка задачи (от пользователя)

```
нужно добавить в редактор шаблонов на тулбар кнопку для создания таблицы 2 на 1
без заливки с прозрачными границами, левая ячейка таблицы выравнивание по левому
краю, правая по правому. Нужно для создания текста двумя колонками.
```

---

## Интерпретация

Добавить в `EditorToolbar` отдельную кнопку «Две колонки» (рядом с обычной кнопкой таблицы).  
По нажатию вставляется таблица 2 столбца × 1 строка со следующими свойствами:

- `withHeaderRow: false` — без заголовочной строки, без заливки.
- Все границы ячеек — `1px solid transparent` (не видны, но сохраняют структуру таблицы).
- Левая ячейка: текст выровнен по левому краю (`text-align: left`).
- Правая ячейка: текст выровнен по правому краю (`text-align: right`).

Цель — быстрый layout «левый блок / правый блок» без видимой таблицы.

---

## Файлы для изучения перед исполнением

```
frontend/src/components/template-designer/EditorToolbar.tsx          — место добавления кнопки и функции
frontend/src/components/template-designer/extensions/TableCellExtension.ts — атрибуты ячеек (border*)
frontend/src/components/template-designer/TemplateDesigner.tsx        — какие расширения подключены (TextAlign?)
frontend/src/components/template-designer/FloatingInsertMenu.tsx      — опционально дублировать туда
```

---

## Файлы для изменения

```
frontend/src/components/template-designer/EditorToolbar.tsx  — новая функция insertTwoColumnLayout() + кнопка в секции Insert
```

---

## Поэтапное исполнение

1. **Проверить наличие TextAlign** — убедиться, что `TextAlign` extension подключён в `TemplateDesigner.tsx`. Если нет — добавить (`@tiptap/extension-text-align`).

2. **Написать `insertTwoColumnLayout()`** в `EditorToolbar.tsx`:
   ```ts
   const insertTwoColumnLayout = () => {
     const chain = editor.chain().focus()
     // Вставить таблицу 1 строку, 2 колонки, без заголовка
     chain.insertTable({ rows: 1, cols: 2, withHeaderRow: false })
     // Установить прозрачные границы и выравнивание через setCellAttribute + setTextAlign
     // Левая ячейка: курсор уже там после insertTable
     chain
       .setCellAttribute('borderTop', '1px solid transparent')
       .setCellAttribute('borderRight', '1px solid transparent')
       .setCellAttribute('borderBottom', '1px solid transparent')
       .setCellAttribute('borderLeft', '1px solid transparent')
       .setTextAlign('left')
     // Перейти к правой ячейке
     chain.goToNextCell()
     chain
       .setCellAttribute('borderTop', '1px solid transparent')
       .setCellAttribute('borderRight', '1px solid transparent')
       .setCellAttribute('borderBottom', '1px solid transparent')
       .setCellAttribute('borderLeft', '1px solid transparent')
       .setTextAlign('right')
     chain.run()
   }
   ```
   > Если `setCellAttribute` недоступен в chain, применять через `updateAttributes` на каждую ячейку после вставки с помощью `editor.state.doc` + `tr.setNodeMarkup`.

3. **Добавить кнопку** в секцию `{/* ── Insert ─── */}` рядом с `ToolBtn` для обычной таблицы:
   ```tsx
   <ToolBtn title="Две колонки" onClick={insertTwoColumnLayout}>
     <LayoutOutlined />  {/* или другой подходящий иконка Ant Design */}
   </ToolBtn>
   ```

4. **Проверить рендер** — убедиться что `table.border` / CSS дефолт таблицы не перекрывает прозрачные границы. При необходимости добавить `border-collapse: collapse` в стили таблицы.

---

## Автоматические тесты

### Что покрыть

| Сценарий | Тип теста | Файл |
|---|---|---|
| insertTwoColumnLayout создаёт таблицу 1×2 без headerRow | unit | `frontend/src/components/template-designer/EditorToolbar.test.tsx` |

### Команды запуска

```bash
cd frontend && npm test -- --run
```

---

## Чеклист ручного тестирования

- [ ] Нажать кнопку «Две колонки» → таблица вставляется с 2 ячейками в 1 строке
- [ ] Границы таблицы не видны (прозрачные)
- [ ] Левая ячейка: курсор/текст по левому краю
- [ ] Правая ячейка: текст по правому краю
- [ ] Нет фоновой заливки ни в одной ячейке
- [ ] При экспорте в DOCX/PDF вёрстка двух колонок сохраняется
- [ ] Регрессия: обычная кнопка «Вставить таблицу» по-прежнему работает

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
- [ ] Git-коммит создан: `feat(template-designer): add two-column layout button`
- [ ] Статус обновлён в этом файле: `DONE`
- [ ] Реестр обновлён: [docs/tasks/README.md](README.md)
