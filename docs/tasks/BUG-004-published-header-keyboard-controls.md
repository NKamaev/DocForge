# BUG-004: Хедер и Ctrl+F не адаптированы к published-режиму

| Поле | Значение |
|---|---|
| **Дата** | 2026-04-16 |
| **Тип** | `bug` |
| **Статус** | `DONE` |
| **Агенты** | `/frontend` |
| **Коммит** | `5e12ac4` |
| **Зависит от** | BUG-002 |
| **GitHub Issue** | [#2](https://github.com/NKamaev/DocForge/issues/2) |

---

## Постановка задачи (от пользователя)

> Найдено в ревью BUG-002. Два WARNING:
> 1. Ctrl+F перехватывается в published-режиме — `e.preventDefault()` блокирует браузерный поиск, при этом `SearchReplacePanel` не отображается.
> 2. Кнопки «Поиск» (SearchOutlined) и «Параметры страницы» (SettingOutlined) остаются видимы и кликабельны в published-режиме, хотя их функции недоступны.

---

## Причина ошибки

**Корень проблемы:**  
При реализации BUG-002 тулбары под редактором были скрыты, но хедер (`Header`) не был адаптирован к `published`-состоянию.

**Цепочка сбоя:**
1. `SearchReplacePanel` скрыт `{editor && !published && ...}` — корректно.
2. Keyboard handler (`Ctrl+F`) по-прежнему вызывает `e.preventDefault()` и `setSearchOpen` независимо от `published` — браузерный поиск заблокирован, кастомная панель не появляется.
3. Кнопки «Поиск» и «Параметры страницы» в хедере рендерятся без условия `!published`.

---

## Файлы для изучения

```
frontend/src/components/template-designer/TemplateDesigner.tsx
  — keyboard handler (useEffect ~строки 377–390)
  — Header section (кнопки SearchOutlined, SettingOutlined ~строки 471–483)
```

---

## Файлы для изменения

```
frontend/src/components/template-designer/TemplateDesigner.tsx
  — в keyboard handler: обернуть Ctrl+F в if (!published)
  — в хедере: скрыть/задизейблить кнопки SearchOutlined и SettingOutlined при published
```

---

## Поэтапное исполнение

1. В keyboard handler найти блок `Ctrl+F`:
   ```ts
   if ((e.ctrlKey || e.metaKey) && e.key === 'f') {
     e.preventDefault()
     setSearchOpen((v) => !v)
   }
   ```
   Обернуть в `if (!published)` (добавить `published` в closure через зависимости `useEffect`).

2. В хедере найти кнопки `SearchOutlined` и `SettingOutlined`, скрыть при `published`:
   ```tsx
   {!published && <Tooltip title="Найти и заменить (Ctrl+F)">...</Tooltip>}
   {!published && <Tooltip title="Параметры страницы">...</Tooltip>}
   ```

3. Убедиться, что `published` попал в deps-массив `useEffect` keyboard handler.

4. `npx tsc --noEmit` — без ошибок.
5. `npm test -- --run` — тесты зелёные.

---

## Автоматические тесты

Достаточно ручной проверки — keyboard handler тяжело тестировать изолированно.

---

## Чеклист ручного тестирования

- [ ] Открыть опубликованный шаблон → Ctrl+F: браузерный поиск работает (не перехватывается)
- [ ] Кнопки «Поиск» и «Параметры страницы» отсутствуют в хедере при published
- [ ] Открыть черновик → Ctrl+F открывает `SearchReplacePanel` (регрессия)
- [ ] Кнопки «Поиск» и «Параметры страницы» работают в черновике (регрессия)

---

## История

| Дата | Событие |
|---|---|
| 2026-04-16 | Обнаружено в ревью BUG-002 (WARNING × 2) |

---

## Завершение задачи

> **Обязательно выполнить перед отчётом о выполнении!**

- [ ] Автотесты запущены и проходят (`npm test -- --run`)
- [ ] Ручной чеклист выполнен полностью
- [ ] Код прошёл ревью (`/review`)
- [ ] Git-коммит создан: `fix(template): fix Ctrl+F and header buttons in published mode`
- [ ] Статус обновлён: DONE
- [ ] Реестр обновлён: docs/tasks/README.md
