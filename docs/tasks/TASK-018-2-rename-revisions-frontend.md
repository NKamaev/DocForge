# TASK-018-2: Frontend — переименовать ревизии в редакции, показывать автора

| Поле | Значение |
|---|---|
| **Дата** | 2026-04-18 |
| **Тип** | `refactor` |
| **Статус** | `TODO` |
| **Агенты** | `/frontend` |
| **Коммит** | — |
| **Зависит от** | [TASK-018-1](TASK-018-1-rename-revisions-backend.md) |
| **GitHub Issue** | [#25](https://github.com/NKamaev/DocForge/issues/25) |

---

## Постановка задачи (от пользователя)

```
переименовать ревизии в редакции, указывать в ревизиях/редакциях автора изменений
```

---

## Интерпретация

После TASK-018-1 API возвращает `editionNumber` и `authorName` вместо `revisionNumber` (без `authorName`).

Нужно:
1. Обновить TypeScript-тип `RevisionMeta` под новый контракт.
2. Переименовать UI-строки «ревизия» / «История ревизий» → «редакция» / «История редакций».
3. Показать `authorName` в строке истории редакций (Drawer).

---

## Файлы для изучения перед исполнением

```
frontend/src/types/document.ts                               — интерфейс RevisionMeta
frontend/src/api/documentsApi.ts                             — методы listRevisions, saveRevision, etc.
frontend/src/components/questionnaire/QuestionnairePage.tsx  — Drawer «История ревизий», строки ревизий
frontend/src/components/questionnaire/QuestionnairePage.test.tsx
```

---

## Файлы для изменения

```
frontend/src/types/document.ts
  — RevisionMeta: revisionNumber → editionNumber, добавить authorName?: string

frontend/src/api/documentsApi.ts
  — переименовать методы: listRevisions → listEditions, saveRevision → saveEdition,
    getRevision → getEdition, restoreRevision → restoreEdition (опционально, если хочется
    чистоты; иначе оставить имена методов, просто обновить тип)

frontend/src/components/questionnaire/QuestionnairePage.tsx
  — строки UI: «ревизи» → «редакци» (Tooltip, Drawer title, notification, List.Item title)
  — в List.Item: добавить отображение authorName (если не null)
  — обновить обращения к rev.revisionNumber → rev.editionNumber

frontend/src/components/questionnaire/QuestionnairePage.test.tsx
  — обновить тестовые данные и ожидания под новые поля
```

---

## Поэтапное исполнение

1. **Обновить `RevisionMeta`** в `types/document.ts`: `revisionNumber` → `editionNumber`, добавить `authorName?: string`.
2. **Обновить `QuestionnairePage.tsx`**: все `rev.revisionNumber` → `rev.editionNumber`, все строки «ревизи» → «редакци».
3. **Добавить `authorName` в список**: под заголовком «Редакция N» показать `authorName ?? 'Неизвестный автор'` (серым цветом, `<Typography.Text type="secondary">`).
4. **Обновить тест** `QuestionnairePage.test.tsx`.
5. **Запустить тесты**.

---

## Автоматические тесты

### Что покрыть

| Сценарий | Тип теста | Файл |
|---|---|---|
| Drawer отображает «История редакций» | unit | `QuestionnairePage.test.tsx` |
| `authorName` отображается в строке редакции | unit | `QuestionnairePage.test.tsx` |

### Команды запуска

```bash
cd frontend && npm test -- --run
```

---

## Чеклист ручного тестирования

- [ ] Кнопка «История ревизий» переименована в «История редакций»
- [ ] Drawer заголовок «История редакций»
- [ ] Каждая строка: «Редакция N» + дата + имя автора
- [ ] Если `authorName = null` — показывается «Неизвестный автор» или ничего (не крашится)
- [ ] Кнопка «Восстановить» работает
- [ ] Регрессия: автосохранение работает, нотификации корректны

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
- [ ] Git-коммит создан с сообщением: `refactor(questionnaire): rename revisions to editions, show authorName`
- [ ] Статус обновлён в этом файле: `DONE`
- [ ] Реестр обновлён: [docs/tasks/README.md](README.md)
