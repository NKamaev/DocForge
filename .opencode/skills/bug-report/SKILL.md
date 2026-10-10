---
name: bug-report
description: Process for creating a bug report with element selection (Claude Design-style)
---

## Steps

### 1. Trigger
- User clicks "Report bug" button in the app header
- Opens a Dialog component with:
  - Text description field
  - Element selector button (crosshair icon)
  - URL auto-captured from `window.location.pathname`

### 2. Element selection (multi-select)
- User clicks "Select element" button (or "Add" if elements already selected)
- Page enters selection mode:
  - Crosshair cursor overlay
  - Fixed bar at bottom: "Click elements to add them. Selected: N. Esc — cancel. [Done]"
  - Each click on a page element **accumulates** it to the list (not single)
  - The "Done" button and the bar itself are ignored (excluded via `pickerBarRef`)
- User clicks "Done" to return to the dialog

### 3. Submit
- Element list shows each entry as two lines:
  - **Label**: Human-readable name (button text, input placeholder, etc.)
  - Selector: CSS selector (monospace, smaller)
- User can remove individual entries with ✕
- User writes description of the issue
- POST to `/api/feedback`:
```json
{
  "elements": [
    { "selector": "button.btn-primary", "label": "Сохранить" },
    { "selector": "div.doc-row:nth-child(1)", "label": "Договор аренды" }
  ],
  "description": "Кнопка «Сохранить» не реагирует на клик после изменения статуса документа",
  "url": "/documents/123/fill"
}
```

### 4. Backend handling
- `FeedbackController` receives the report
- Logs to audit_log with entity_type = `BUG_REPORT`
- Stores in database or forwards to issue tracker

### 5. Frontend element selector implementation
```typescript
interface ElementInfo {
  selector: string  // CSS selector for programmatic identification
  label: string     // Human-readable name
}

function getElementSelector(el: Element): string {
  // Builds CSS selector from tag, id, data-slot, :nth-child position
  // Max depth 8 levels, stops at #id
}

function getElementLabel(el: Element): string {
  // Button  → title / aria-label / text content
  // Input   → placeholder / aria-label / name
  // Img     → alt / filename
  // Heading → text content
  // Generic → tag.class «first 40 chars of text»
}
```

### 6. Playwright test
```typescript
test('bug report flow', async ({ page }) => {
  await page.goto('/')
  await page.getByRole('button', { name: 'Report bug' }).click()
  await expect(page.getByText('Submit bug report')).toBeVisible()
})
```
