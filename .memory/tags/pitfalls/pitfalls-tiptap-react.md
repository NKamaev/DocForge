# Pitfalls — TipTap/ProseMirror + React

> Тег: `pitfalls:tiptap-react` · Обновлено: `2026-10-10`

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