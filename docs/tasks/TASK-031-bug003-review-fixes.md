# TASK-031: Доработки по ревью BUG-003: description в Popconfirm и тесты TemplateList

| Поле | Значение |
|---|---|
| **Дата** | 2026-04-18 |
| **Тип** | `refactor` |
| **Статус** | `TODO` |
| **Агенты** | `/frontend` |
| **Коммит** | — |
| **Зависит от** | BUG-003 (d5a2b46, 01ca1c8) |
| **GitHub Issue** | [#60](https://github.com/NKamaev/DocForge/issues/60) |

---

## Постановка задачи (от пользователя)

```
По результатам ревью BUG-003 создай соответствующие задачи.
```

---

## Интерпретация

Ревью BUG-003 выявило два WARNING:

1. **Popconfirm без description в TemplateList** — в `TemplateList.tsx` Popconfirm «Снять с публикации?» не имеет поля `description`, тогда как аналогичный Popconfirm в `TemplateDesigner.tsx` содержит пояснение «По шаблону нельзя будет создавать документы.» Несогласованность UX между двумя точками входа.

2. **Отсутствует `TemplateList.test.tsx`** — задача BUG-003 требовала unit-тестов на success-тексты publish/unpublish в TemplateList (см. секцию «Автоматические тесты» в BUG-003), файл так и не был создан.

Дополнительно: строка 335 в `TemplateDesigner.tsx` с тернарным оператором внутри `notification.success` слишком длинная — вынести сообщение в переменную.

---

## Файлы для изучения перед исполнением

```
frontend/src/components/template-list/TemplateList.tsx
frontend/src/components/template-designer/TemplateDesigner.tsx
frontend/src/components/template-designer/PublishedBanner.test.tsx  — пример теста
```

---

## Файлы для изменения

```
frontend/src/components/template-list/TemplateList.tsx       — добавить description в Popconfirm
frontend/src/components/template-designer/TemplateDesigner.tsx — вынести successMsg в переменную
frontend/src/components/template-list/TemplateList.test.tsx  — создать (unit-тесты)
```

---

## Поэтапное исполнение

1. **TemplateList.tsx** — добавить `description` к Popconfirm unpublish:
   ```tsx
   description="По шаблону нельзя будет создавать документы."
   ```
2. **TemplateDesigner.tsx:335** — вынести тернарный оператор в переменную `successMsg` перед вызовом `notification.success`.
3. **TemplateList.test.tsx** — создать файл, покрыть:
   - После успешного publish появляется текст «Шаблон опубликован — теперь можно создавать документы»
   - После успешного unpublish появляется текст «Шаблон снят с публикации — создание документов закрыто»

---

## Автоматические тесты

### Что покрыть

| Сценарий | Тип теста | Файл |
|---|---|---|
| После publish появляется корректный success-текст | unit | `TemplateList.test.tsx` |
| После unpublish появляется корректный success-текст | unit | `TemplateList.test.tsx` |

### Команды запуска

```bash
cd frontend && npm test -- --run
```

---

## Чеклист ручного тестирования

- [ ] В TemplateList открыть Popconfirm «Снять с публикации?» — убедиться что description отображается
- [ ] Регрессия: publish/unpublish из TemplateList и TemplateDesigner работают корректно

---

## История

| Дата | Событие |
|---|---|
| 2026-04-18 | Задача создана по результатам ревью BUG-003 |

---

## Завершение задачи

> **Обязательно выполнить перед отчётом о выполнении!**

- [ ] Автотесты запущены и проходят (`npm test`)
- [ ] Ручной чеклист выполнен полностью
- [ ] Код прошёл ревью (`/review`)
- [ ] Git-коммит создан с сообщением: `refactor(template): add popconfirm description and publish tests`
- [ ] Статус обновлён в этом файле: `DONE`
- [ ] Реестр обновлён: [docs/tasks/README.md](README.md)
- [ ] GitHub Issue закрыт (state: closed) + комментарий с хешем коммита
