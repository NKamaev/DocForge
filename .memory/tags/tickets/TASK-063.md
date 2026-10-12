# TASK-063 — Полировка diff-view (baseline, E2E, точность)

- **Статус:** открыт (частично закрыт TASK-064)
- **Создан:** 2026-10-10
- **Источник:** ревью TASK-019-2

## Обновление 2026-10-12 (после TASK-064)

- Пункт 4 («точность диффа — `insertText`/`getJSON()` vs строка текста») — **устарел**: plain-text diff удалён, теперь structural diff (`utils/structuralDiff.ts`).
- Пункт 5 (дубли `data-testid`) — тесты diff переписаны на новый `DocumentDiffView`.
- Formatting `pageStyle` для diff-страницы — **закрыт** (`DocumentDiffView` использует `formattingToCssVars`).
- Остаются: п.1 (семантика baseline «текущее»), п.2 (Playwright E2E), п.3 (guard).

## Пункты

1. **Направление baseline «С текущим состоянием»** — при compare=current и
   target=revision N семантика цвета инвертируется (текст, добавленный после N,
   рендерится как DELETE/красный). Уточнить подпись или поменять направление.
   (`DiffViewer.tsx:106-108,134-136`)
2. **Playwright E2E** для сценария diff/истории редакций (сейчас только unit).
   Вынести в `frontend/e2e/` или `frontend/tests/`.
3. **Guard** — не показывать diff-кнопку, если у документа нет редакций.
4. **Точность диффа** — diff по `insertText`/`getJSON()` vs строка текста
   (`extractTextRecursive`), оценить ложные срабатывания на форматировании.
5. **Дубли `data-testid`** — `diff-chunk-${type}` дублируется при нескольких
   чанках одного типа; тесты уже используют `data-chunk-type`.
