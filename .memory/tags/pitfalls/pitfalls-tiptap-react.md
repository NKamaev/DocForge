# Pitfalls — TipTap/ProseMirror + React

> Тег: `pitfalls:tiptap-react` · Обновлено: `2026-10-10`

## Класс проблем: валидность схемы ProseMirror (TASK-064)

- **Контейнеры ограничивают детей.** `bulletList`/`orderedList` допускают только `listItem`, `table` — только `tableRow`, `tableRow` — только ячейки; эти типы НЕ в `group: block`. Обёртка-`diffBlock` (`group: block`) как сиблинг внутри такого контейнера → схемa-invalid (`RangeError: Invalid content for node ...`).
- **TipTap молча рендерит невалидное** — content-check отключён (`enableContentCheck` по умолчанию `false`). Ловить только через `getSchema(extensions)` + `PMNode.fromJSON(schema, doc).check()` в тестах.
- **`codeBlock` запрещает marks** (`marks: ''`) → нельзя вешать `diffInsert`/`diffDelete` на его текст. Обходить как opaque (delete+insert целым блоком).
- **Приём для списков/таблиц (structuralDiff):** «именованные» дети не оборачивать — сохранять `listItem`/`tableRow`, маркируя либо инлайн-контентом (`listItem`, content `paragraph block*` — марки на текст), либо внутренние блочные листья (`tableRow > tableCell > diffBlock > paragraph`).
- **Источник:** TASK-064, `frontend/src/utils/structuralDiff.ts`, тесты со schema-guard.

## Класс проблем: life-cycle и поток обновлений в редакторе

### setContent эмитит onUpdate → холостой save (TASK-009)
- **Симптом:** при загрузке документа с `current_body` происходят лишние write-операции.
- **Корень:** `editor.commands.setContent(content)` по умолчанию вызывает `onUpdate` (emitUpdate=true) → `handleBodyChange` → debounce → `updateBody` с теми же данными.
- **Решение:** `setContent(body, { emitUpdate: false })` при инициализации.
- **Источник:** `docs/archive/tasks/tasks/TASK-009-suppress-onupdate-on-init.md`.

### NodeView: один render / стабильная структура
- **Правило:** NodeView должен иметь единый корневой render; скрытие — через style, а не разными `return`. Иначе React remount → мерцание/ошибки Yjs. (BUG-013, тег `pitfalls:conditional-rendering`.)
- **Похожее:** инлайновый параметр в workspace — storage-driven пере-рендер через transaction meta `'answers-updated'`, НЕ через смену attrs/узлов (`WorkspaceParameterFieldNode.tsx:63-73`).

### Две сущности fontSize
- `FontSizeExtension.ts` (designer) и `document-editor/FontSize.ts` (workspace) — независимые дубликаты одной mark. Менять обе.

### Duplicate-расширения и коллаборация
- `StarterKit` в workspace с отключёнными `undoRedo`/`link`/`underline` — включение их конфликтует с таковыми в других расширениях (`DocumentWorkspace.tsx:82-109`).
- `Collaboration.configure({document: ydoc})` — расширение обязано подключаться с тем же ydoc, что и `useCollaboration`.

<!-- Новые записи добавляй СВЕРХУ. -->