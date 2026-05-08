# TASK-039-2: AppLayout: новый Topbar (nav-pills) + collapsible Sidebar

| Поле | Значение |
|---|---|
| **Дата** | 2026-04-25 |
| **Тип** | `feature` |
| **Статус** | `TODO` |
| **Агенты** | `/frontend` |
| **Коммит** | — |
| **Зависит от** | [TASK-039-1](TASK-039-1-design-system-css.md) |
| **Родитель** | [TASK-039](TASK-039-new-design-handoff.md) |

---

## Постановка задачи

Переписать `AppLayout.tsx` по дизайну из `shell.jsx`.

---

## Интерпретация

Текущий AppLayout использует Ant Design Layout/Sider/Header. Новый дизайн — полностью кастомный: фиксированный топбар с навигацией-пилюлями и схлопывающийся sidebar с разделами.

---

## Файлы для изменения

```
frontend/src/pages/AppLayout.tsx        — полная переработка
frontend/src/components/BrandMark.tsx   — новый SVG-компонент
```

## Файлы для изучения

```
/tmp/docforge-handoff/docforge/project/shell.jsx
/tmp/docforge-handoff/docforge/project/icons.jsx  — список иконок
```

---

## Поэтапное исполнение

### BrandMark.tsx
```tsx
// SVG 32×32: rect rx=7 fill=var(--brand), буква D (path stroke=white), угловая стрелка
const BrandMark = ({ size = 24 }) => ( <svg ...> )
```

### Topbar (`.topbar`)
1. Левая часть `.topbar-left`:
   - Кнопка `.icon-btn` fold/unfold (иконка `fold`)
   - `.brand-mark`: BrandMark + `<div class="name"><b>Doc</b><span>Forge</span></div>`
   - Разделитель 1px
   - `<nav class="ws-switch">` с 4 nav-pills (`.ws-pill`): Главная / Мои документы / Шаблоны / Справочники
   - Активная пилюля: `.ws-pill.active` — подсвечена брендом
2. Правая часть `.topbar-right`:
   - `.topbar-search`: иконка search + input placeholder «Найти…» + `<kbd>⌘K</kbd>`
   - Иконки info, alert
   - Разделитель
   - `.user-pill`: аватар + имя + роль + chevron-down

### Sidebar (`.sidebar` / `.sidebar.collapsed`)
1. Секция «Недавние» + кнопка fold (иконка `chev-left`)
2. Список `.nav-item` — последние 5 документов (из React Router location или props)
3. Секция «Закладки» — 3 статических шаблона
4. Секция «Команды» — 2 команды со счётчиками
5. Footer `.sidebar-foot`: зелёная точка + «Все изменения сохранены»
6. В collapsed-режиме: sidebar width схлопывается через CSS `.app.sidebar-collapsed`

### Класс на `.app`
- При collapsed: `app sidebar-collapsed` → CSS скрывает тексты навигации

---

## Чеклист ручного тестирования

- [ ] Топбар отображает бренд, nav-pills, поиск, user-pill
- [ ] Активная пилюля подсвечивается при навигации
- [ ] Sidebar сворачивается/разворачивается кнопкой fold
- [ ] В collapsed-режиме остаются только иконки
- [ ] Мобильный вид: topbar не переполняется

---

## История

| Дата | Событие |
|---|---|
| 2026-04-25 | Задача создана |

---

## Завершение задачи

- [ ] Автотесты запущены и проходят
- [ ] Ручной чеклист выполнен
- [ ] Git-коммит создан: `feat(ui): new AppLayout with topbar nav-pills and sidebar`
- [ ] Статус обновлён: DONE
- [ ] Реестр обновлён: docs/tasks/README.md
