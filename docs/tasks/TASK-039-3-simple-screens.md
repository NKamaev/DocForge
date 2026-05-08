# TASK-039-3: Простые экраны: Login, Home, Templates, Directories, DirectoryDetail

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

Переписать 5 страниц по дизайну из `screens-simple.jsx`.

---

## Файлы для изменения

```
frontend/src/pages/LoginPage.tsx
frontend/src/pages/HomePage.tsx
frontend/src/pages/TemplatesPage.tsx
frontend/src/pages/DirectoriesPage.tsx
frontend/src/pages/DirectoryDetailPage.tsx
```

## Файлы для изучения

```
/tmp/docforge-handoff/docforge/project/screens-simple.jsx
```

---

## Поэтапное исполнение

### LoginPage (`.login-screen`)
- Двухколоночный layout: `.login-art` (левая) + `.login-form` (правая)
- `.login-art`: BrandMark + h1 «Конструктор документов — без боли и без копипаста» + p + `.demo-doc` + footer
- `.demo-doc`: декоративный A4-документ со span.chip.chip-param и span.chip.chip-cond-inline
- `.login-form`: pill-tabs Вход/Регистрация, поля логин+пароль с иконками в `.input-group`, кнопка входа
- При регистрации — дополнительные поля: Отображаемое имя, Email (необязательно)

### HomePage (`.page`)
- `.page-head`: «Здравствуйте, {firstName}» + дата + текст + кнопки «Импорт DOCX» + «Новый шаблон»
- 4 `StatCard` в сетке 4 колонки: шаблоны / документы / соавторы / экспорты
- `StatCard`: иконка в цветном квадрате + число (26px, weight 600) + подпись + optional trend badge
- «Продолжить работу»: карточка с таблицей последних документов + прогресс-бар
- «Быстрые действия»: 3 кликабельные карточки с иконкой и описанием

### TemplatesPage (`.page`)
- `.page-head` + pill-tabs «Все · N / Опубликованные · N / Черновики · N» + поиск
- `.tbl` таблица: название+версия / категория / статус (зелёный/янтарный badge) / использован / автор / обновлён / row-actions
- `.tbl-foot`: счётчик + пагинация

### DirectoriesPage (`.page`)
- Сетка 3 колонки: карточки `.card.hover.pad` с иконкой book + название + записей/полей/дата

### DirectoryDetailPage (`.page`)
- Breadcrumb «← Справочники» + h1 + описание
- `.tbl` с колонками записи + кнопки Edit/Delete
- Кнопки в шапке: Импорт CSV / Поля / Добавить запись

---

## Чеклист ручного тестирования

- [ ] Login: вход работает, переключение вкладок Вход/Регистрация
- [ ] Home: статкарды отображаются, клик по документу — переход в workspace
- [ ] Templates: фильтрация по статусу работает, клик «Открыть» — переход в designer
- [ ] Directories: карточки кликабельны, переход в DirectoryDetail
- [ ] DirectoryDetail: breadcrumb «← Справочники» работает

---

## История

| Дата | Событие |
|---|---|
| 2026-04-25 | Задача создана |

---

## Завершение задачи

- [ ] Автотесты запущены и проходят
- [ ] Ручной чеклист выполнен
- [ ] Git-коммит создан: `feat(ui): redesign login, home, templates, directories screens`
- [ ] Статус обновлён: DONE
- [ ] Реестр обновлён: docs/tasks/README.md
