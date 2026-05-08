# TASK-006-3: Удаление маршрута /documents/:id/edit

| Поле | Значение |
|---|---|
| **Дата** | 2026-04-16 |
| **Тип** | `refactor` |
| **Статус** | `DONE` |
| **Агенты** | `/frontend` |
| **Коммит** | — |
| **Зависит от** | TASK-006-1, TASK-006-2, BUG-005 |
| **GitHub Issue** | [#57](https://github.com/NKamaev/DocForge/issues/57) |

---

## Постановка задачи

Подзадача TASK-006 (P3). После достижения полного паритета `/fill` с `/edit` — удалить компонент `DocumentEditor` и маршрут `/documents/:id/edit`.

---

## Интерпретация

`/documents/:id/edit` уже не доступен из навигации (нет ни одной ссылки в компонентах). Маршрут существует только в `App.tsx`. После завершения TASK-006-1, TASK-006-2 и BUG-005 компонент `DocumentEditor.tsx` становится мёртвым кодом.

---

## Файлы для изменения

```
frontend/src/App.tsx                                              — удалить Route для /documents/:id/edit
frontend/src/components/document-editor/DocumentEditor.tsx        — удалить файл
frontend/src/components/document-editor/DocumentEditorToolbar.tsx — оценить: используется ли ещё
frontend/src/components/document-editor/ParticipantsPanel.tsx     — перенесён в DocumentWorkspace → оставить
```

---

## Поэтапное исполнение

1. Убедиться что TASK-006-1, TASK-006-2, BUG-005 — DONE.
2. `grep -r "documents.*edit\|DocumentEditor"` — убедиться что нет ссылок кроме App.tsx.
3. Удалить Route в `App.tsx`.
4. Удалить `DocumentEditor.tsx` (и импорт в App.tsx).
5. Проверить `DocumentEditorToolbar.tsx` — если используется только в `DocumentEditor` → удалить; если перенесён → оставить.
6. Обновить `docs/architecture.md` — раздел 5.4, убрать упоминание /edit как "планируется удаление".

---

## Чеклист ручного тестирования

- [x] Прямой переход на `/documents/{id}/edit` → редирект на 404 или /fill
- [x] `/fill` работает полностью: опросный лист, редактирование текста, Yjs, история ревизий, экспорт
- [x] Нет сломанных импортов в проекте (сборка `npm run build` без ошибок)

---

## История

| Дата | Событие |
|---|---|
| 2026-04-16 | Задача создана (подзадача TASK-006) |
| 2026-04-18 | Выполнено: удалён Route /edit, DocumentEditor.tsx, DocumentEditorToolbar.tsx, CollaborationCursors.tsx/.css; обновлена architecture.md |

---

## Завершение задачи

- [x] Сборка `npm run build` проходит без ошибок
- [x] Ручной чеклист выполнен
- [ ] Код прошёл ревью (`/review`)
- [x] Git-коммит создан с сообщением: `refactor(frontend): remove deprecated /edit route and DocumentEditor`
- [x] Статус обновлён в этом файле: `DONE`
- [x] Реестр обновлён: [docs/tasks/README.md](README.md)
