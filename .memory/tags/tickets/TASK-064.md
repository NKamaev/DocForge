# TASK-064 — Structural rich-diff редакций (вариант B)

- **Статус:** DONE
- **Создан:** 2026-10-11
- **Завершён:** 2026-10-12
- **Источники:** task-tracker MCP (TASK-064 + подзадачи 064-1…4), git log
- **Коммиты:** `077bfb2` (backend: `targetBody`/`compareBody`), `84a3674` (frontend движок/рендер + тесты)

## Суть

Заменить plain-text diff редакций (вариант A, `842890a`) на **structural rich-diff**: показывать настоящий документ (TipTap JSON) с подсветкой правок — заголовки/списки/таблицы/форматирование сохраняются, а не только текст. Движок diff — на фронте; backend отдаёт сырые тела.

## Решения (утверждены пользователем)

1. **Глубина:** блоки + инлайн word-level. Параграфы/заголовки — по словам; списки/таблицы — по элементам/строкам.
2. **Место движка:** Frontend (`utils/structuralDiff.ts`); backend — только `targetBody`/`compareBody`.
3. **Baseline:** toggle «с предыдущей редакцией» / «с текущим состоянием»; plain-text `DiffChunk` удалён.

## Декомпозиция

| Подзадача | Что | Агент | Статус |
|---|---|---|---|
| TASK-064-1 | `RevisionDiffResponse.targetBody/compareBody`; удалить `DiffChunk` + `java-diff-utils` | /backend | DONE |
| TASK-064-2 | `DiffExtensions` (diffInsert/diffDelete/diffBlock) + общий `buildWorkspaceExtensions` | /tiptap-specialist | DONE |
| TASK-064-3 | `structuralDiff` движок + read-only `DocumentDiffView` | /frontend | DONE |
| TASK-064-4 | Unit/component/backend тесты | /test | DONE |

## Реализация

- **Backend** (`077bfb2`): `GET /api/documents/{id}/revisions/{revNum}/diff?compareTo=N` →
  `{ revisionId, revisionNumber, compareToRevisionNumber, compareToCurrent, targetBody, compareBody }`.
  `compareBody` = указанная `compareTo`-редакция, либо `currentBody ?: generatedBody` при `compareToCurrent`.
  `computeRevisionDiff` отдаёт сырые тела; удалены `extractPlainText`/`extractTextRecursive`/`appendChunk`, `dto/DiffChunk.java`, зависимость `java-diff-utils`. Permission `DocumentPermission.READ` сохранён.
- **DiffExtensions** (`extensions/DiffExtensions.ts`): marks `diffInsert` (`data-diff="insert"`, зелёный фон + underline), `diffDelete` (красный + line-through); node `diffBlock { kind }` (`group: block`, `content: 'block+'`, цветной бордер). Стили — самодостаточные inline (`style` в renderHTML), отдельных CSS-классов не требуется.
- **buildWorkspaceExtensions** (`extensions/buildWorkspaceExtensions.ts`): `buildWorkspaceExtensions(options?: { collaboration?: YDoc | null; diff?: boolean })`. Без аргументов — прежнее поведение; `diff: true` добавляет diff-расширения; `collaboration` подключает Collaboration только при передаче. `DocumentWorkspace` переведён на builder.
- **structuralDiff** (`utils/structuralDiff.ts`): LCS по блокам (сигнатура = тип + текст + `leafKey` + ключи атомов-потомков), инлайн word-level для параграфов/заголовков, рекурсия в списки/таблицы, слияние соседних `diffBlock`, экспорт `structuralDiff(targetBody, compareBody)` + `diffHasChanges(doc)`.
- **DocumentDiffView** (`DiffViewer.tsx`): read-only `useEditor` c `buildWorkspaceExtensions({ diff: true })`, рендер в `.editor-canvas → .tiptap-page` со `pageStyle` из `formatting`; toggle baseline; loading/error; `onClose`.
- **formattingCssVars** (`utils/formattingCssVars.ts`): общий `formattingToCssVars` (дедуп с `TemplateDesigner.tsx`).

## Ключевой инцидент схемы (MAJOR ревью)

`structuralDiff` сначала эмитил **schema-invalid** документы: `bulletList > diffBlock > listItem`, `table > diffBlock > tableRow` (контейнеры допускают только `listItem`/`tableRow`, а те не в `group: block`). TipTap молча рендерит (content-check отключён). Фикс: «именованные» дети не оборачиваются — `listItem` помечается инлайн-марками (сохраняя `paragraph block*`), у `tableRow`/`tableCell` оборачиваются внутренние блочные листья (`tableRow > tableCell > diffBlock > paragraph`). Дополнительно `codeBlock` (`marks: ''`) — opaque: изменённый codeBlock → delete+insert `diffBlock`; вложенный в помеченный listItem — `listItem > [paragraph(marked), diffBlock > codeBlock]`.

## Тесты и проверки

- `structuralDiff.test.ts` — 31 (schema-guard через `getSchema(buildWorkspaceExtensions({diff:true}))` + `PMNode.fromJSON(schema, doc).check()` — защищает от регрессии невалидной структуры).
- `DiffViewer.test.tsx` (9), `QuestionnairePage.test.tsx` (11).
- Backend: `DocumentServiceDiffTest` (9), `DocumentControllerDiffTest` (4).
- `npx tsc -b` EXIT 0; `npm run build` OK; `npx vitest run` — только предсуществующий `useCollaboration.test.ts` (2); `./gradlew test` — 5 предсуществующих падений, новых нет.
- Линт-гигиена: 148 + 2 stray `.js` под `frontend/src` удалены (эмит отменённого агента; Vite резолвит `.js` раньше `.tsx`).
- `@review` → APPROVE (повторное ревью).

## Ограничения / follow-up

- **TASK-062** — diff не отражает изменения значений параметров (`answers`).
- **TASK-063** — часть пунктов закрыта (formatting `pageStyle`, «точность диффа» устарела); остаются baseline-семантика «текущее», Playwright E2E, guard.
- parameterField в diff-редакторе покажет текущее значение/метку (тела обычно уже с подставленным текстом).
