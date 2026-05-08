# TASK-039-4: Template Designer: toolbar, resizable side panel, scrollable tabs

| Поле | Значение |
|---|---|
| **Дата** | 2026-04-25 |
| **Тип** | `feature` |
| **Статус** | `TODO` |
| **Агенты** | `/frontend` |
| **Коммит** | — |
| **Зависит от** | [TASK-039-2](TASK-039-2-shell-layout.md) |
| **Родитель** | [TASK-039](TASK-039-new-design-handoff.md) |

---

## Постановка задачи

Переработать страницу конструктора шаблонов по дизайну из `designer.jsx`.

---

## Файлы для изменения

```
frontend/src/pages/TemplateDesignerPage.tsx
frontend/src/components/template-designer/   — существующие компоненты
```

## Файлы для изучения

```
/tmp/docforge-handoff/docforge/project/designer.jsx
```

---

## Поэтапное исполнение

### 1. Designer Bar (`.designer-bar`)
- `<button class="icon-btn">` ← назад + `<h2>` название + `<span class="tag">` версия
- `.unsaved` badge (icon alert + «Не сохранено») — только если `hasUnsaved && !published`
- `.tag.green` «Опубликован» — если `published`
- `.avatar-stack` соавторов (2 аватара)
- Разделители + кнопки: search / settings / download / Сохранить (primary, disabled) / Опубликовать (success icon) / Снять (danger icon)

### 2. Banner (`.banner`)
- Синяя полоска при `published`: иконка info + текст + кнопка «Снять с публикации»

### 3. Toolbar (`.toolbar`)
Кнопки `.tb-btn` и `.tb-btn.lbl` (с текстом):
- Селект шрифта (145px) + размера (60px)
- B/I/U/S (форматирование) + цвет/подсветка
- Выравнивание L/C/R
- Списки ul/ol + отступы
- Таблица / Изображение / Ссылка / Разрыв страницы
- **«Параметр»** (icon param) + **«Условие»** (icon branch) + **«Пункт»** (icon hash) — с текстовой меткой
- Поиск

### 4. Search Panel (`.search-panel`)
- Инлайн под тулбаром: Найти (220px) + «N совпадений» + Заменить на (220px) + ‹ › + «Заменить» + «Заменить всё» + × закрыть

### 5. Resizable Side Panel
- `.side-resizer` — разделитель между `.editor-pane` и `.designer-side`
- `onMouseDown` → mousemove меняет `--side-w` (260–560px)
- CSS: `.designer-body { grid-template-columns: 1fr 4px var(--side-w) }`

### 6. Scrollable Tabs (`ScrollableTabs`)
- `.tabs > .tabs-scroller` — горизонтальный скролл
- `.tabs-nav` с кнопками ‹ › — появляется при overflow (ResizeObserver)
- Вкладки: **Параметры** / **Опросный лист** / **Формулировки** / **Скрипты**

### 7. Tabs Content
- **ParamsTab**: счётчик + кнопка «+ Параметр» + поиск + `.param-card` (label / code · type / обяз. badge)
- **QuestionnaireTab**: страницы (`.q-card` с номером) + вопросы текущей страницы
- **PhrasingsTab**: список формулировок с количеством вариантов (тег purple)
- **ScriptsTab**: empty state с иконкой code

### 8. Context Menu (`.ctx-menu`)
- Фиксированное позиционирование по `(x, y)` клика
- Пункты: Параметр / Условный блок / Нумерованный пункт / разделитель / Обернуть / Вставить ссылку / разделитель / Копировать / Вставить

### 9. Slash Menu (`.slash-menu`)
- Появляется при двойном клике в canvas
- Пункты: Параметр / Условный блок / Нумерованный пункт / Таблица / Изображение / Разрыв страницы

---

## Чеклист ручного тестирования

- [ ] Designer bar: все кнопки видны, «Не сохранено» / «Опубликован» переключаются
- [ ] Banner появляется при published=true
- [ ] Toolbar: все кнопки кликабельны, активные подсвечиваются
- [ ] Search panel: открывается/закрывается
- [ ] Side panel: перетаскивание разделителя изменяет ширину
- [ ] Scrollable tabs: стрелки появляются при узкой панели
- [ ] Context menu: правый клик → меню, клик вне → закрывается
- [ ] Slash menu: двойной клик → меню появляется
- [ ] В published-режиме контекстное меню и slash-меню заблокированы

---

## История

| Дата | Событие |
|---|---|
| 2026-04-25 | Задача создана |

---

## Завершение задачи

- [ ] Автотесты запущены и проходят
- [ ] Ручной чеклист выполнен
- [ ] Git-коммит создан: `feat(ui): redesign template designer with resizable panel and toolbar`
- [ ] Статус обновлён: DONE
- [ ] Реестр обновлён: docs/tasks/README.md
