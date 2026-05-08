# TASK-039: Имплементация нового дизайна DocForge (handoff)

| Поле | Значение |
|---|---|
| **Дата** | 2026-04-25 |
| **Тип** | `feature` |
| **Статус** | `TODO` |
| **Агенты** | `/frontend` |
| **Коммит** | — |
| **Зависит от** | — |
| **GitHub Issue** | #71 |

---

## Постановка задачи (от пользователя)

```
Read the attached docforge.zip and the README inside.
Implement: DocForge.html @docs/DocForge-handoff.zip
```

---

## Интерпретация

Получен handoff-бандл от Claude Design (`docs/DocForge-handoff.zip`). Пользователь создал полный интерактивный макет приложения в HTML/CSS/JSX-прототипе и хочет реализовать его pixel-perfect в существующем стеке (React 18 + TypeScript).

**Архитектурное решение:** импортировать `styles.css` из бандла как фундамент дизайн-системы, заменив Ant Design-компоненты кастомными там, где дизайн расходится. Функциональную логику (API, state, Zustand, TipTap) оставить без изменений, изменить только визуальный слой.

---

## Состав бандла

| Файл | Назначение |
|---|---|
| `styles.css` (~39К) | Полная CSS-система: переменные, типографика, все компоненты |
| `shell.jsx` | Topbar (nav-pills) + collapsible Sidebar |
| `screens-simple.jsx` | Login, Home, Templates, Directories, DirectoryDetail |
| `designer.jsx` | Template Designer (toolbar, resizable panel, tabs) |
| `workspace.jsx` | Workspace + ParticipantsPanel + QField + RevisionsDrawer |
| `modals.jsx` | CreateTemplate, CreateDocument, Export, PageSettings |
| `data.jsx` | Тестовые данные (для ориентира) |
| `icons.jsx` | Иконки (адаптировать под существующий Icon-компонент) |

---

## Декомпозиция

| Подзадача | Файл | Статус | Зависит от |
|---|---|---|---|
| [TASK-039-1](TASK-039-1-design-system-css.md) | design-system-css | `TODO` | — |
| [TASK-039-2](TASK-039-2-shell-layout.md) | shell-layout | `TODO` | 039-1 |
| [TASK-039-3](TASK-039-3-simple-screens.md) | simple-screens | `TODO` | 039-2 |
| [TASK-039-4](TASK-039-4-template-designer.md) | template-designer | `TODO` | 039-2 |
| [TASK-039-5](TASK-039-5-documents-page.md) | documents-page | `TODO` | 039-2 |
| [TASK-039-6](TASK-039-6-workspace-modals.md) | workspace-modals | `TODO` | 039-2 |

---

## Файлы для изучения перед исполнением

```
/tmp/docforge-handoff/docforge/project/   — весь бандл
frontend/src/pages/AppLayout.tsx
frontend/src/App.tsx
frontend/src/pages/
frontend/src/components/
```

---

## Поэтапное исполнение

1. **TASK-039-1** — подключить `styles.css`, настроить CSS-переменные и data-атрибуты
2. **TASK-039-2** — переписать AppLayout (Topbar + Sidebar)
3. **TASK-039-3/4/5/6** — параллельно реализовать страницы

---

## История

| Дата | Событие |
|---|---|
| 2026-04-25 | Задача создана, handoff-бандл распакован в `/tmp/docforge-handoff/` |

---

## Завершение задачи

> **Обязательно выполнить перед отчётом о выполнении!**

- [ ] Все подзадачи TASK-039-1..6 выполнены и закрыты
- [ ] Визуальная проверка всех экранов в браузере
- [ ] Код прошёл ревью (`/review`)
- [ ] Git-коммит создан: `feat(ui): implement new design handoff`
- [ ] Статус обновлён через task_transition: DONE
- [ ] Реестр обновлён: docs/tasks/README.md
- [ ] GitHub Issue #71 закрыт + комментарий с хешем коммита
