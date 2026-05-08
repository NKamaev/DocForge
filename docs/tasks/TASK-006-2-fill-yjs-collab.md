# TASK-006-2: Yjs-коллаборация в DocumentWorkspace (/fill)

| Поле | Значение |
|---|---|
| **Дата** | 2026-04-16 |
| **Тип** | `feature` |
| **Статус** | `DONE` |
| **Агенты** | `/frontend` |
| **Коммит** | a4c273c |
| **Зависит от** | TASK-006-1 |
| **GitHub Issue** | [#56](https://github.com/NKamaev/DocForge/issues/56) |

---

## Постановка задачи

Подзадача TASK-006 (P2). Добавить real-time совместное редактирование на странице `/fill` — несколько пользователей должны видеть правки друг друга в правой панели (DocumentWorkspace).

---

## Интерпретация

Сейчас `DocumentWorkspace` использует TipTap без `Collaboration` extension — нет Yjs, нет WebSocket. `DocumentEditor` (/edit) уже имеет Yjs через `useCollaboration(id)` + `Collaboration.configure({ document: ydoc })`.

Задача — перенести Yjs-интеграцию в `DocumentWorkspace`, заменив стандартный `StarterKit` на `StarterKit.configure({ undoRedo: false })` и добавив `Collaboration` + `useCollaboration`.

**Сложность**: `DocumentWorkspace` получает `initialBody` через prop и сеет его в TipTap; при Yjs нужно следовать паттерну из `DocumentEditor` — сеять только если Yjs-фрагмент пуст. Нужно согласовать Yjs-инициализацию с `renderPreview`-инициализацией.

---

## Файлы для изучения перед исполнением

```
frontend/src/components/document-editor/DocumentEditor.tsx    — useCollaboration, seed логика (строки 98–122)
frontend/src/components/questionnaire/DocumentWorkspace.tsx   — инициализация TipTap, setContent логика
frontend/src/hooks/useCollaboration.ts                        — хук, возвращает { ydoc, isConnected, connectedUsers }
frontend/src/components/document-editor/ParticipantsPanel.tsx — панель участников
```

---

## Файлы для изменения

```
frontend/src/components/questionnaire/DocumentWorkspace.tsx
  — добавить useCollaboration(documentId)
  — StarterKit.configure({ undoRedo: false })
  — Collaboration.configure({ document: ydoc })
  — seed логика: если ydoc.getXmlFragment('default').length === 0 → setContent через 400ms
  — добавить ParticipantsPanel в заголовок (передаётся через prop или берётся из хука)

frontend/src/components/questionnaire/QuestionnairePage.tsx
  — передать documentId в DocumentWorkspace
```

---

## Поэтапное исполнение

1. Добавить `documentId` prop в `DocumentWorkspace`.
2. Вызвать `useCollaboration(documentId)` внутри `DocumentWorkspace`.
3. Заменить `StarterKit` на `StarterKit.configure({ undoRedo: false })`.
4. Добавить `Collaboration.configure({ document: ydoc })` в extensions.
5. Seed логика: дождаться 400ms после монтирования; если `ydoc.getXmlFragment('default').length === 0` — `setContent(initialBody ?? renderPreview(...))`.
6. Добавить `ParticipantsPanel` в заголовок QuestionnairePage (правая панель).
7. Удалить `onBodyChange`-сохранение через REST (тело теперь хранится в Yjs); оставить `updateBody` только перед экспортом.

---

## Чеклист ручного тестирования

- [x] Открыть один документ в двух вкладках → правки одного пользователя видны второму в реальном времени
- [x] ParticipantsPanel показывает онлайн-участников
- [x] Перезагрузка страницы → контент восстанавливается из Yjs-состояния
- [x] Регрессия: ответы ОЛ по-прежнему синхронизируются с параметрами в документе

---

## История

| Дата | Событие |
|---|---|
| 2026-04-16 | Задача создана (подзадача TASK-006) |
| 2026-04-17 | Реализована: Yjs + ParticipantsPanel в QuestionnairePage, seed-логика 400ms |
| 2026-04-17 | Исправлен баг: edits disappear after refresh (useMemo provider → useEffect); ConcurrentModificationException в backend (коммит 54441a5) |
| 2026-04-18 | Исправлен баг ParticipantsPanel «офлайн»: App.tsx восстанавливает user через /users/me при старте; добавлен sync-event fallback для isConnected (коммит 6687cfb) |
| 2026-04-18 | Код ревью выполнен: 1 CRITICAL, 5 WARNING, 4 SUGGESTION → созданы BUG-009, TASK-014, TASK-015, TASK-016 |

---

## Завершение задачи

- [x] Автотесты запущены и проходят (`npm test`)
- [x] Ручной чеклист выполнен полностью
- [x] Код прошёл ревью (`/review`)
- [x] Git-коммит создан с сообщением: `feat(fill): Yjs real-time collaboration in DocumentWorkspace`
- [x] Статус обновлён в этом файле: `DONE`
- [x] Реестр обновлён: [docs/tasks/README.md](README.md)
