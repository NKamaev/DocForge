# TASK-006-1: Загрузка current_body + сохранение тела документа на /fill

| Поле | Значение |
|---|---|
| **Дата** | 2026-04-16 |
| **Тип** | `feature` |
| **Статус** | `DONE` |
| **Агенты** | `/frontend` |
| **Коммит** | `b83e912` |
| **Зависит от** | — |
| **GitHub Issue** | [#55](https://github.com/NKamaev/DocForge/issues/55) |

---

## Постановка задачи

Подзадача TASK-006 (P0). `DocumentWorkspace` на `/fill` всегда рендерит шаблон через `renderPreview`, игнорируя `current_body`. Правки свободного текста теряются при перезагрузке, а экспорт DOCX/PDF использует устаревший `current_body` от последней генерации.

---

## Причина проблемы

В `QuestionnairePage.tsx` при загрузке:
```ts
const tmpl = await templatesApi.getSnapshot(...)
if (tmpl.body) setTemplateBody(tmpl.body)
```
Загружается только тело шаблона. `doc.currentBody` (из `documentsApi.getById`) не используется для инициализации `DocumentWorkspace`.

В `DocumentWorkspace.tsx` инициализация:
```ts
const content = renderPreview(templateBody, answers, parameters, { showAllConditionals: true })
editor.commands.setContent(content)
```
Всегда рендерит с нуля из шаблона, даже если `current_body` существует.

`scheduleSave` в `QuestionnairePage` вызывает только `saveAnswers` — тело документа (`editor.getJSON()`) не сохраняется.

---

## Файлы для изучения перед исполнением

```
frontend/src/components/questionnaire/QuestionnairePage.tsx    — loadT (строки 67–78), scheduleSave (строка 86)
frontend/src/components/questionnaire/DocumentWorkspace.tsx    — Props, инициализация setContent (строки 131–137)
frontend/src/api/documentsApi.ts                               — getById, updateBody
frontend/src/types/document.ts                                 — Document.currentBody
```

---

## Файлы для изменения

```
frontend/src/components/questionnaire/QuestionnairePage.tsx
  — передавать currentBody в DocumentWorkspace как новый prop
  — в scheduleSave: после saveAnswers вызвать updateBody(id, editorBodyRef.current) если есть правки
  — добавить ref/callback для получения JSON из DocumentWorkspace

frontend/src/components/questionnaire/DocumentWorkspace.tsx
  — добавить prop: initialBody?: unknown (current_body из сервера)
  — в инициализации: если initialBody != null → setContent(initialBody), иначе renderPreview(templateBody, ...)
  — добавить prop: onBodyChange?: (body: JSONContent) => void — вызывать в onUpdate
```

---

## Поэтапное исполнение

1. **`DocumentWorkspace`**: добавить prop `initialBody?: unknown`. В `useEffect` инициализации: если `initialBody` задан — `editor.commands.setContent(initialBody)`, иначе `renderPreview(...)`.
2. **`DocumentWorkspace`**: добавить prop `onBodyChange?: (body: JSONContent) => void`. Вызывать в `useEditor.onUpdate`.
3. **`QuestionnairePage`**: в `loadT` сохранить `doc.currentBody` в state `const [docCurrentBody, setDocCurrentBody] = useState<unknown>(null)`. Передать в `DocumentWorkspace` как `initialBody={docCurrentBody}`.
4. **`QuestionnairePage`**: добавить `editorBodyRef = useRef<JSONContent | null>(null)`. Обновлять через `onBodyChange` коллбэк из `DocumentWorkspace`.
5. **`QuestionnairePage`**: в `scheduleSave` после `saveAnswers` вызвать `documentsApi.updateBody(id, editorBodyRef.current)` (fire-and-forget, ошибку в `console.warn`).
6. **Порядок приоритетов**: сначала загружать `current_body`, потом сохранять — чтобы не затереть чужие правки на первом сохранении.

---

## Автоматические тесты

| Сценарий | Тип теста | Файл |
|---|---|---|
| DocumentWorkspace использует initialBody если задан | unit | `DocumentWorkspace.test.tsx` |
| scheduleSave вызывает updateBody если editorBodyRef не null | unit | `QuestionnairePage.test.tsx` |

```bash
cd frontend && npm test -- --run
```

---

## Чеклист ручного тестирования

- [x] Создать новый документ → заполнить ОЛ → отредактировать свободный текст → перезагрузить страницу → правки сохранены
- [x] Открыть документ с существующим `current_body` → правая панель показывает `current_body`, не шаблон
- [x] Редактировать свободный текст + ответы → DOCX экспорт содержит актуальный текст
- [x] Регрессия: ответы по-прежнему сохраняются; параметры обновляются в реальном времени

---

## История

| Дата | Событие |
|---|---|
| 2026-04-16 | Задача создана (подзадача TASK-006) |
| 2026-04-17 | Ревью выполнено: WARNING×2 (TASK-009, TASK-010), SUGGESTION×2 |

---

## Завершение задачи

- [x] Автотесты запущены и проходят (`npm test`)
- [x] Ручной чеклист выполнен полностью
- [x] Код прошёл ревью (`/review`)
- [x] Git-коммит создан с сообщением: `feat(fill): load current_body and persist body edits in DocumentWorkspace`
- [x] Статус обновлён в этом файле: `DONE`
- [x] Реестр обновлён: [docs/tasks/README.md](README.md)
