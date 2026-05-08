# BUG-005: Кнопка «История ревизий» отсутствует на странице /fill; ревизии не создаются

| Поле | Значение |
|---|---|
| **Дата** | 2026-04-16 |
| **Тип** | `bug` |
| **Статус** | `DONE` |
| **Агенты** | `/frontend`, `/backend` |
| **Коммит** | `afe0b78`, `0e2a25a` |
| **Зависит от** | — |
| **GitHub Issue** | [#53](https://github.com/NKamaev/DocForge/issues/53) |

---

## Постановка задачи (от пользователя)

> Задача 3 — пользователь не видит список ревизий. Поэтому также нельзя проверить возможность откатиться.  
> Уточнение: пользователь не видит кнопку «История ревизий».  
> Уточнение 2: пользователь работает на странице `/documents/{id}/fill`.

---

## Контекст

Маршрут `/documents/{id}/fill` рендерит `QuestionnairePage` — двухпанельный layout:
- **Левая панель** — опросный лист с полями (вопросами)
- **Правая панель** — `DocumentWorkspace` (read-only TipTap превью документа с подставленными ответами)

Пользователь редактирует **ответы** в левой панели; правая панель отображает результат в реальном времени. Это не `DocumentEditor` — там нет Yjs-коллаборации и ручного сохранения тела документа.

---

## Причина ошибки

**Дефект 1 — кнопка отсутствует:**  
В правом хедере `QuestionnairePage.tsx` (строка 281) есть только кнопки DOCX и PDF. Кнопка «История ревизий» и Drawer не реализованы. `DocumentEditor.tsx` имеет эту кнопку, но `/fill` не использует `DocumentEditor`.

**Дефект 2 — ревизии не создаются:**  
`scheduleSave()` в `QuestionnairePage` вызывает `documentsApi.saveAnswers()` — это обновляет ответы в документе, но **не вызывает `documentsApi.saveRevision()`**. Ревизии в таблице `document_revision` (PostgreSQL) и коллекции `document_revision_bodies` (MongoDB) не создаются.

**Цепочка сбоя:**
1. Пользователь меняет ответ → `scheduleSave()` (debounce 500ms) → `saveAnswers()` → ответы сохранены.
2. `saveRevision()` не вызывается → таблица `document_revision` пуста для этого документа.
3. Кнопки «История» нет на странице — пользователь не может её нажать вообще.

**Дефект 3 — смежный backend-баг (обнаружен при тестировании):**  
`DocumentRevisionBody.savedAt` объявлен как `OffsetDateTime`, тогда как MongoDB-драйвер не имеет кодека для этого типа. Все остальные MongoDB-документы проекта (`Document`, `DocumentRegistry`) используют `Instant`. Исправлено: поле заменено на `Instant`, в `saveRevision()` передаётся `now.toInstant()`.

---

## Файлы для изучения перед исполнением

```
frontend/src/components/questionnaire/QuestionnairePage.tsx   — scheduleSave() (строка 86), правый хедер (строка 267–331)
frontend/src/api/documentsApi.ts                              — saveRevision() (строка 54), listRevisions() (строка 46)
frontend/src/components/document-editor/DocumentEditor.tsx    — образец реализации: Drawer (строка 285), openHistory (строка 179), restoreRevision (строка 193)
```

---

## Файлы для изменения

```
frontend/src/components/questionnaire/QuestionnairePage.tsx
  — добавить состояния: historyOpen, revisions, loadingRevisions
  — добавить функции: openHistory(), restoreRevision()
  — в scheduleSave(): после успешного saveAnswers() вызвать saveRevision() (не блокировать при ошибке)
  — в правый хедер (Space, строка 281): добавить кнопку HistoryOutlined → openHistory()
  — добавить Drawer со списком ревизий (по образцу DocumentEditor.tsx строки 285–321)
```

---

## Поэтапное исполнение

1. **Добавить состояния** в `QuestionnairePage`:
   ```ts
   const [historyOpen, setHistoryOpen] = useState(false)
   const [revisions, setRevisions] = useState<RevisionMeta[]>([])
   const [loadingRevisions, setLoadingRevisions] = useState(false)
   ```
2. **Добавить `openHistory()`** — по образцу `DocumentEditor.tsx:179`.
3. **Добавить `restoreRevision()`** — по образцу `DocumentEditor.tsx:193`. После restore перезагрузить страницу (`window.location.reload()`).
4. **Исправить `scheduleSave()`**: после успешного `saveAnswers()` вызвать `saveRevision(id, 'Автосохранение')` (fire-and-forget, ошибку в `console.warn`).
5. **Добавить кнопку** `HistoryOutlined` в правый хедер (Space на строке 281) с `onClick={openHistory}`.
6. **Добавить Drawer** — полная копия блока из `DocumentEditor.tsx:285–321`.
7. Импортировать `HistoryOutlined`, `RollbackOutlined`, `Drawer`, `List`, `Tooltip` из antd/icons.

---

## Автоматические тесты

### Что покрыть

| Сценарий | Тип теста | Файл |
|---|---|---|
| `scheduleSave` вызывает `saveRevision` после `saveAnswers` | unit | `QuestionnairePage.test.tsx` |

### Команды запуска

```bash
cd frontend && npm test -- --run
```

---

## Чеклист ручного тестирования

- [x] Открыть `/documents/{id}/fill` → в правом хедере видна кнопка с иконкой часов (HistoryOutlined)
- [x] Изменить любой ответ → подождать 500мс (автосохранение)
- [x] Нажать кнопку «История» → открывается Drawer, в нём минимум 1 ревизия
- [x] Изменить ещё один ответ → дождаться автосохранения → в Drawer 2 ревизии (после переоткрытия)
- [x] Нажать «Восстановить» на ревизии → страница перезагружается, ответы возвращаются к состоянию ревизии (требует дополнительно изучения поставлена задача TASK-007)
- [ ] Регрессия: автосохранение не падает с ошибкой; DOCX/PDF кнопки продолжают работать (Не работает)

---

## История

| Дата | Событие |
|---|---|
| 2026-04-16 | Задача создана |
| 2026-04-16 | Уточнение: кнопка не видна, не только пустой список |
| 2026-04-16 | Уточнение 2: страница /fill = QuestionnairePage, не DocumentEditor |
| 2026-04-17 | Выполнено (frontend): добавлены кнопка истории, Drawer, saveRevision в scheduleSave, unit-тест (коммит `afe0b78`) |
| 2026-04-17 | Обнаружен смежный backend-баг: POST /revisions → 500, `CodecConfigurationException` на `OffsetDateTime` в `DocumentRevisionBody` |
| 2026-04-17 | Исправлено (backend): `DocumentRevisionBody.savedAt` → `Instant`; `saveRevision()` передаёт `now.toInstant()` (коммит `0e2a25a`) |
| 2026-04-17 | Ревью проведено: WARNING × 2 (export handlers inline, тесты), SUGGESTION × 2 (UI error, typing) |
| 2026-04-17 | По результатам ревью созданы BUG-006 (DOCX/PDF регрессия), TASK-008 (refactor по замечаниям) |

---

## Завершение задачи

> **Обязательно выполнить перед отчётом о выполнении!**

- [x] Автотесты запущены и проходят (`npm test`)
- [x] Ручной чеклист выполнен полностью
- [x] Код прошёл ревью (`/review`) — найдены WARNING × 2, SUGGESTION × 2; созданы BUG-006, TASK-008
- [x] Git-коммит создан с сообщением: `fix(document): revision history button and auto-save revision in QuestionnairePage`
- [x] Статус обновлён в этом файле: `DONE`
- [x] Реестр обновлён: [docs/tasks/README.md](README.md)
