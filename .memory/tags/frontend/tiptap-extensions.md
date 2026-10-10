# Фронтенд — каталог кастомных TipTap-расширений

> Тег: `frontend:tiptap-extensions` · Обновлено: `2026-10-10`

## [2026-10-10 10:00] Designer-расширения (template-designer/extensions)

| Расширение | Тип | Ключевое |
|---|---|---|
| `ParameterFieldNode.tsx` | inline atom `parameterField` | popover форматов (дата/число/ФИО/падеж), команда `insertParameterField`, `CASE_ABBR`, ReactNodeViewRenderer |
| `ConditionalBlockNode.tsx` | block wrapper `conditionalBlock`, content `block+` | конструктор условий (simple/python) в popover, collapse/rename/delete, цветная рамка |
| `ConditionalInlineNode.tsx` | inline wrapper | команды `wrapInConditionalInline`/`unwrapConditionalInline` |
| `NumberedClauseNode.tsx` | block `numberedClause` (479 строк) | `NumberingStyle = decimal\|alpha-ru\|alpha-en` (:33); `AutoNumberingState {byPos,byId}` (:35-38), ключ плагина `autoNumberingPluginKey`; label из `buildLabels` (счётчики 0..4 + `listStart` :100-114); плавающий marker span, наследование firstTextStyle, иконка условия |
| `ClauseRefNode.tsx` | inline atom `clauseRef` | команда `insertClauseRef`, label из `autoNumberingPluginKey`, scroll-to-clause |
| `PhrasingsRefNode.tsx` | inline atom `phrasingsRef` | новые `phrasingsIds[]` + легаси `phrasingsId`/`displayName` (`@deprecated legacy compat` :16,18); детект orphan |
| `FontSizeExtension.ts` | TextStyle mark | fontSize в pt |
| `IndentExtension.ts` | block | шаг 2em, max 10 |
| `LineSpacingExtension.ts` | block | межстрочный |
| `ParagraphSpacingExtension.ts` | block | spaceBefore/After, margins, firstLineIndent |
| `PageBreakNode.ts` | block atom | `Mod-Enter` |
| `SearchReplaceExtension.ts` | ProseMirror plugin (179 строк) | `searchReplaceKey`, decorations `search-match`/`search-match-current`, find/replace/all |
| `SlashCommandExtension.ts` | `/` palette | `buildSlashCommands` — 12 команд, русские лейблы, suggestion `SlashCommandMenu` |
| `TableCellExtension.ts` | table cells | `ExtendedTableCell`/`ExtendedTableHeader` — verticalAlign, backgroundColor, textOrientation, границы по сторонам, minHeight; backwards-compat легаси-атрибутов |

## Workspace-варианты (questionnaire/)

- `WorkspaceParameterFieldNode.tsx` — тот же `parameterField`, но storage-driven (см. тег `frontend:workspace`).
- `WorkspaceConditionalBlockNode` / `WorkspaceConditionalInlineNode` — динамическое show/hide по ответам.
- `WorkspaceNumberedClauseNode` — читает `hiddenPositions` из storage; тик по `answers-updated`.
- `WorkspacePhrasingsRefNode` — разворачивает варианты, BOOLEAN→«Да/Нет», `renderInlineContent`.
- `document-editor/FontSize.ts` — дубль fontSize-расширения для DocumentWorkspace (намеренно отдельный от дизайнерного).
- **Источник:** `frontend/src/components/template-designer/extensions/`, explore frontend report.

## Прочее

- В `src/` и `e2e/` нет TODO/FIXME/HACK комментов. Единственные депрекейты — `PhrasingsRefNode.tsx:16,18` и backward-compat в `TableCellExtension`.
- FontSize имеет два независимых расширения (designer vs workspace) — при правках менять оба.

<!-- Новые записи добавляй СВЕРХУ. -->