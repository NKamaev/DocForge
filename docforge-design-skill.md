---
name: docforge-web
description: |
  Document constructor web app — template designer, TipTap document editor,
  real-time collaboration, folder-based document management. Built with
  React 18, TypeScript, Ant Design 5, TipTap 2, Zustand, Y.js.
triggers:
  - "docforge"
  - "document constructor"
  - "template designer"
  - "document editor"
  - "collaborative editor"
  - "doc forge"
od:
  mode: prototype
  platform: desktop
  scenario: product
  preview:
    type: html
    entry: index.html
  design_system:
    requires: true
    sections: [color, typography, layout, components, motion, voice]
  default_for: false
  featured: true
---

# DocForge — Document Constructor Web App

Design a modular monolith document constructor: template designer → parameter-driven
document generation → real-time collaborative editing. Target: SaaS for legal,
HR, and finance teams who need audit-ready document workflows.

## Product context

DocForge is a **module monolith** (Spring Boot 3.3 backend + React 18 frontend).
Modules communicate only via `*Service` interfaces. PostgreSQL (JPA) + MongoDB
(@Document) + Redis. Java 21 virtual threads enabled.

### Core user flows

1. **Template Designer** — visual editor with slash commands, parameter insertion
   `{{param}}`, conditional blocks `{{#if param}}...{{/if}}`, phrasing variants.
   Side panel manages parameters and questionnaire.
2. **Document Fill** — answer questionnaire → auto-fill template → preview.
3. **Collaborative Editor** — TipTap 2 + Y.js WebSocket, cursor tracking,
   multi-user editing with conflict resolution.
4. **Document Management** — folder tree, drag-and-drop, list/grid view toggle,
   status tracking (draft / generated / exported), bulk actions.
5. **Role-Based Access** — OWNER / EDITOR / MANAGER / VIEWER permissions.

## Design system (frozen tokens)

Use these exact values — do not introduce new color primitives.

### Color

```css
:root {
  /* Brand — warm indigo */
  --brand-h: 232;
  --brand: oklch(0.55 0.16 232);
  --brand-soft: oklch(0.96 0.03 232);

  /* Accents */
  --amber: oklch(0.75 0.14 85);
  --green: oklch(0.60 0.16 160);
  --red: oklch(0.55 0.20 25);
  --violet: oklch(0.50 0.20 310);

  /* Neutrals — warm-cool hybrid */
  --ink-1: oklch(0.15 0.02 280);   /* heading text */
  --ink-2: oklch(0.30 0.02 280);   /* body text */
  --ink-3: oklch(0.45 0.02 280);   /* secondary text */
  --ink-4: oklch(0.60 0.02 280);   /* placeholder */
  --bg: oklch(0.98 0.005 280);      /* page background */
  --surface: oklch(1 0 0);          /* card / panel */
  --border: oklch(0.90 0.01 280);   /* divider */

  /* Density modes (data-density attribute) */
  /* compact: 30px rows, regular: 36px, comfy: 42px */
}
```

Dark mode via `[data-theme="dark"]` — invert ink/bg, keep brand hue.

### Typography

| Role | Family | Weight | Notes |
|------|--------|--------|-------|
| UI | Inter Tight / Söhne / Inter | 400–600 | `--font-ui` |
| Document | Source Serif 4 / Georgia | 400–700 | `--font-doc` |
| Monospace | JetBrains Mono | 400–500 | `--font-mono` |

Font pairings configurable via `data-fonts`: `grotesk` / `serif-display` / `mono-ui`.

### Spacing & Radius

- Radius: 4px (xs), 8px (sm), 12px (md), 16px (xl)
- Shadows: `card` / `page` / `pop` using oklch alpha

## Page structure (App shell)

```
┌──────────────────────────────────────────────────────┐
│  Topbar (56px)                                       │
│  [Logo]  Главная | Мои документы | Шаблоны |        │
│           Справочники                                │
│                                    [👤] [?] [🔔]    │
├──────────┬───────────────────────────────────────────┤
│ Sidebar  │  Main content (routes via <Outlet />)     │
│ (232px)  │                                           │
│          │                                           │
│ Recent   │  HomePage:                                │
│ Docs     │    Stat cards (templates / documents /     │
│          │    collaborators)                         │
│ Bookmarks│    "Continue working" document list       │
│          │    Quick action cards                     │
│ Teams    │                                           │
│          │  DocumentsPage:                           │
│          │    Folder tree (left) + list/grid toggle   │
│          │    Drag-and-drop, bulk actions             │
│          │                                           │
│          │  TemplatesPage:                           │
│          │    Pill tabs (All/Published/Drafts)        │
│          │    Search bar, template table              │
│          │                                           │
│          │  TemplateDesignerPage:                    │
│          │    TipTap toolbar + editor (left)          │
│          │    Parameters/Questionnaire panel (right)  │
│          │                                           │
│          │  QuestionnairePage:                       │
│          │    Split: questionnaire panel (left)       │
│          │           document preview (right)          │
└──────────┴───────────────────────────────────────────┘
```

## Resource map

```
docforge-web/
├── SKILL.md                ← you're reading this
├── assets/
│   └── template.html       ← seed with DocForge token system
└── references/
    ├── layouts.md          ← page skeletons (topbar, sidebar, editor, etc.)
    ├── components.md       ← Ant Design component mappings
    └── checklist.md        ← P0/P1/P2 self-review for DocForge
```

## Workflow

### Step 0 — Pre-flight

1. **Read `assets/template.html`** — token system + class inventory. Every class
   in `references/layouts.md` must be defined here.
2. **Read `references/layouts.md`** — topbar, sidebar, document list, editor,
   questionnaire split, template table.
3. **Read `references/components.md`** — Ant Design component usage patterns
   (Table, Modal, Dropdown, Tag, notification, message).

### Step 1 — Copy seed

Copy `assets/template.html` → `index.html`. Replace `:root` tokens with
DocForge values above. Set brand mark text "DocForge" in topnav.

### Step 2 — Plan pages to render

Default set (state in one sentence to user before writing):

| Priority | Page | Layout from `layouts.md` |
|----------|------|--------------------------|
| 1 | HomePage | stat-cards + continue-list + quick-actions |
| 2 | DocumentsPage | folder-tree + doc-list (list/grid toggle) |
| 3 | TemplateDesignerPage | editor-toolbar + tiptap-editor + param-panel |
| 4 | QuestionnairePage | split-layout (questionnaire \| preview) |
| 5 | TemplatesPage | pill-tabs + search-bar + template-table |

### Step 3 — Paste and fill

For each page, copy `<section data-page="...">` from `layouts.md` into
`<main id="content">`. Replace `[REPLACE]` with real copy:

- Topbar: "Главная", "Мои документы", "Шаблоны", "Справочники"
- Quick actions: "Создать документ", "Создать шаблон", "Импорт"
- Status badges: "Черновик", "Сформирован", "Экспортирован"
- Roles: "Владелец", "Редактор", "Менеджер", "Наблюдатель"

### Step 4 — Ant Design component integration

Use CDN-loaded antd@5.x (antd/dist/reset.css + antd). Map these components:

| UI Element | Ant Component |
|------------|---------------|
| Document table | `Table` with columns (name, status, updated, actions) |
| Template table | `Table` with publish/draft status, edit/delete actions |
| Modals | `Modal` for create-document, create-template |
| Dropdowns | `Dropdown` for document actions, user menu |
| Tags | `Tag` for status badges (color-coded) |
| Notifications | `notification` API for save/export feedback |
| Messages | `message` API for inline feedback |

### Step 5 — Self-check

Run `references/checklist.md` P0 items. Must pass:
- Brand indigo appears only on primary CTAs + eyebrows (budget: 2×/screen)
- Serif font for document preview, sans for UI
- All images use `.ph-img` placeholder class
- Mobile reflow at 920px (sidebar collapses, single column)
- `data-od-id` on every `<section>`

### Step 6 — Emit artifact

```html
<artifact identifier="docforge-web" type="text/html" title="DocForge — Document Constructor">
<!doctype html>
<html>...</html>
</artifact>
```

One sentence before artifact. Nothing after.

## Hard rules

- **Single accent (brand indigo)** — used at most twice per screen (primary CTA + section eyebrow).
- **Document font is serif** (Source Serif 4 / Georgia). UI is sans (Inter Tight).
- **Image placeholders only** — `.ph-img` class, no external stock URLs.
- **Ant Design 5.x CDN** — include `antd/dist/reset.css` and `antd` via CDN,
  don't reimplement component styles from scratch.
- **Density modes** — respect `[data-density]` attribute (compact/regular/comfy).
- **Dark mode** — respect `[data-theme="dark"]` attribute.
- **TipTap editor** — render as a contenteditable `<div>` with toolbar
  (bold, italic, heading, list, table, image, parameter-insert).
- **Y.js awareness** — render cursor indicators and user avatars for collaboration UI.

## Output contract

```
<artifact identifier="docforge-web" type="text/html" title="DocForge — Конструктор документов">
<!doctype html>
<html lang="ru">
<head>
  <meta charset="UTF-8">
  <title>DocForge</title>
  <link rel="stylesheet" href="https://unpkg.com/antd@5/dist/reset.css">
  <style>/* tokens + classes */</style>
</head>
<body>
  <!-- topbar, sidebar, main content with page sections -->
</body>
</html>
</artifact>
```
