# DocForge — Design Prompt for Claude Designer

## Project Overview

**DocForge** is a document-construction web application (SPA). Users build reusable document templates with smart fill-in fields, conditional blocks, and questionnaires, then generate ready-to-export documents (DOCX / PDF) by answering a guided form.

Primary language of the UI: **Russian**.

---

## Design System

| Token | Value |
|---|---|
| Framework | Ant Design 5 (React 18) |
| Primary color | `#1677FF` (Ant Design default blue) |
| Success / Published | `#52c41a` green |
| Warning / Unsaved | `#faad14` amber |
| Error / Danger | `#ff4d4f` red |
| Neutral border | `#d9d9d9` |
| Surface | `#ffffff` |
| Background | `#f5f5f5` (page canvas), `#f0f2f5` (full-page bg), `#fafafa` (table header) |
| Font | System (Ant Design default), document body uses `Times New Roman, serif` 14pt |
| Border radius | 6px (Ant Design default) |
| Shadow (page card) | `0 1px 4px rgba(0,0,0,0.15)` |

All interactive controls use Ant Design components: `Button`, `Table`, `Card`, `Form`, `Input`, `Modal`, `Drawer`, `Tabs`, `Steps`, `Tag`, `Space`, `Typography`, `Tooltip`, `Popconfirm`, `Badge`, `List`, `Avatar`, `Dropdown`.

---

## Global Layout

The app uses `antd Layout` with three zones:

```
┌─────────────────────────────────────────────────────────────┐
│  HEADER  (64px, dark #001529)                               │
│  [DocForge logo]  [Nav menu]  …  [Avatar] [Name] [Logout]   │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  CONTENT  (flex-fill, #f5f5f5 or white depending on page)  │
│                                                             │
├─────────────────────────────────────────────────────────────┤
│  FOOTER  (text-center, #999) — "DocForge © 2026"           │
└─────────────────────────────────────────────────────────────┘
```

### Header details
- Background: Ant Design dark theme (`#001529`)
- Left side: **"DocForge"** white bold 18px logo, then horizontal dark `Menu` with items:
  - Главная · Мои документы · Шаблоны · Справочники
- Right side: `Avatar` (UserOutlined icon) + white display name + text "Выйти" button (LogoutOutlined)
- Height: 64px, padding 0 24px

---

## Screens

### 1. Login / Register Page (`/login`)

Full-page centered card on `#f0f2f5` background.

```
┌──────────────────────────────┐
│         DocForge             │  ← Title level 3, centered
│  ┌───────────────────────┐   │
│  │  Вход  │  Регистрация │   │  ← Ant Design Tabs
│  ├───────────────────────┤   │
│  │  [🔑 Логин input]     │   │
│  │  [🔒 Пароль input]    │   │
│  │  [  Войти  ] (block)  │   │
│  └───────────────────────┘   │
└──────────────────────────────┘
```

- Card width: 420px
- Inputs: `size="large"` with prefix icons
- Primary blue full-width submit button

Register tab adds: логин (min 3), отображаемое имя, email (optional), пароль (min 6).

---

### 2. Home Page (`/`)

Padding 24px content area.

```
Добро пожаловать, <name>!          ← Title level 2
DocForge — конструктор документов  ← Secondary paragraph

┌──────────────┐  ┌──────────────┐
│ 📝 Шаблоны   │  │ 📄 Документы │   ← hoverable Cards, 8-col on md
│              │  │              │
│ Создание и   │  │ Генерация и  │
│ управление   │  │ редактирование│
└──────────────┘  └──────────────┘
```

- Responsive grid: `xs=24, sm=12, md=8`
- Cards are clickable with `cursor: pointer` and `hoverable` prop

---

### 3. Templates Page (`/templates`)

Table-based list with header action bar.

```
Шаблоны                    [+ Создать шаблон]   ← space-between flex row

┌────────────────┬──────────┬───────────┬──────────┬───────────────────────┐
│ Название       │ Категория│ Статус    │ Дата     │ Действия              │
├────────────────┼──────────┼───────────┼──────────┼───────────────────────┤
│ Договор аренды │ Договоры │ ●Опубл.   │ 01.04.26 │ Открыть Снять Удалить │
│ Заявление      │ —        │ ○Черновик │ 15.03.26 │ Открыть Опубл. Удалить│
└────────────────┴──────────┴───────────┴──────────┴───────────────────────┘
[Всего: 12]                                        [1] [2]  20/стр ▼
```

- **Статус** column: `<Tag color="green">Опубликован</Tag>` or `<Tag color="default">Черновик</Tag>`
- Actions: link-style buttons (`type="link"`, small)
- Delete uses `Popconfirm` confirmation
- Publish/unpublish uses `Popconfirm` confirmation
- Pagination: total shown left, page changer right

---

### 4. Template Designer (`/templates/:id/edit`)

Full-height split layout (no footer, height = 100vh − 64px header).

#### 4a. Designer Header bar (white, border-bottom)

```
[←]  Template Name  [⚠ Несохранённые изменения]    [🔍][⚙][⬇ Экспорт ▼][Сохранить][Опубликовать]
```

- Back arrow button (ArrowLeftOutlined, text style)
- Title level 4
- Unsaved badge: amber `ExclamationCircleOutlined` + warning text (12px)
- Right buttons: Search toggle, Page settings, Export dropdown (DOCX/PDF), Save (primary), Publish (green border)
- When **published**: Save is disabled, "Снять с публикации" (danger) replaces Publish button
- Yellow info banner appears below header when published: `PublishedBanner`

#### 4b. Body: Editor 70% + Sidebar 30%

```
┌─────────────────────────────────┬────────────────────────────────┐
│  EDITOR (70%)                   │  SIDEBAR (30%)                 │
│  [Toolbar row]                  │  [Параметры|Опросный|Фразы|..] │
│  [Search/Replace panel]         │                                │
│  ┌─────────────────────────┐    │  ← Tabs (Ant Design Tabs)      │
│  │  A4 white page card     │    │                                │
│  │  (box-shadow, margins)  │    │                                │
│  │  TipTap ProseMirror     │    │                                │
│  │  rich text editor       │    │                                │
│  └─────────────────────────┘    │                                │
│  (gray #f5f5f5 canvas bg)       │  (white bg)                    │
└─────────────────────────────────┴────────────────────────────────┘
```

**Editor canvas**: `padding: 24px 48px`, bg `#f5f5f5`, overflow auto.

**A4 page card** (`.tiptap-page`):
- White background, `box-shadow: 0 1px 4px rgba(0,0,0,0.15)`
- Width 210mm, min-height 297mm (A4 portrait); configurable A3/Letter/landscape
- Margins: top 20mm, right 15mm, bottom 20mm, left 30mm
- Font: Times New Roman 14pt, line-height 1.5
- Centered horizontally with `margin: 0 auto`

**Toolbar** (`EditorToolbar`): compact horizontal strip of icon buttons above the editor.
Formatting groups: Bold/Italic/Underline/Strike | Font family picker | Font size | Color | Highlight | Alignment | Lists | Indent | Tables | Links | Images | Line spacing | Paragraph spacing | Insert parameter/clause/phrase buttons.

**Sidebar tabs**:
- **Параметры** — list of template parameters with code/label/type, +add button
- **Опросный лист** — questionnaire page/question editor
- **Фразы** — phrasings (alternative text variants)
- **Скрипты** — placeholder ("Скрипты появятся здесь")

**Special inline nodes** in the editor (rendered as colored chips/badges):
- `ParameterField` — blue chip showing the parameter label, e.g. `[ФИО Арендатора]`
- `ConditionalBlock` — block with dashed border and "Условный блок" label
- `ConditionalInline` — inline span with condition marker
- `NumberedClause` — auto-numbered clause (§ 1., § 1.1, etc.)
- `PhrasingsRef` — phrase reference chip
- `ClauseRef` — cross-reference chip
- `PageBreak` — dashed horizontal separator with "── Разрыв страницы ──" label

---

### 5. Document Workspace / Questionnaire Page (`/documents/:id/fill`)

Split layout (height = 100vh − 64px). No footer visible.

```
┌──────────────────────────────┬──────────────────────────────────────────┐
│  QUESTIONNAIRE (420px fixed) │  DOCUMENT WORKSPACE (flex 1)             │
│                              │  [Toolbar strip]                         │
│  Опросный лист               │  ┌──────────────────────────────────┐    │
│                              │  │  A4 page card                    │    │
│  [Steps: Шаг 1 > Шаг 2 > …] │  │  editable TipTap + Yjs collab.  │    │
│                              │  │                                  │    │
│  ┌──────────────────────┐    │  │  ParameterField nodes become     │    │
│  │  Страница 1          │    │  │  interactive input chips         │    │
│  │                      │    │  └──────────────────────────────────┘    │
│  │  Вопрос 1 ─────────  │    │                                          │
│  │  [  input field   ]  │    │  Panel header (fafafa bg):                │
│  │                      │    │  [hint text] [👥 participants] [⏱][DOCX][PDF]│
│  │  Вопрос 2 ─────────  │    │                                          │
│  │  [  select        ]  │    │                                          │
│  └──────────────────────┘    │                                          │
│                              │                                          │
│  [← Назад]      [Далее →]    │                                          │
└──────────────────────────────┴──────────────────────────────────────────┘
```

**Left panel (questionnaire)**:
- Fixed 420px width, white bg, right border `#d9d9d9`
- Scrollable inner content with padding 20px 16px
- Title level 4 (questionnaire title)
- Multi-step: Ant Design `Steps` (small, current page highlighted)
- Questions in a `Card size="small"` with `Form layout="vertical"`
- Active/highlighted question: bg `#E6F4FF`, blue 2px border, smooth transition 0.25s
- Bottom nav strip: back/forward small buttons, border-top `#f0f0f0`

**Right panel header** (8px 16px padding, `#fafafa` bg):
- Secondary text hint (13px)
- `ParticipantsPanel`: colored dot avatars for connected users (real-time collab via Yjs/WebSocket)
- History button (HistoryOutlined)
- DOCX and PDF export small buttons

**Participants panel**: small colored avatar badges showing connected users' initials/colors. Color derived from login hash → HSL.

**Revision history drawer**: slides from right, width 360px. `List` of revisions with date, comment, and "Восстановить" button.

---

### 6. Directories Page (`/directories`)

```
Справочники                  [+ Создать справочник]

┌─── Card ──────────────────────────────────────┐
│ Города           →                            │
│ 45 записей                                    │
└───────────────────────────────────────────────┘
```

Grid of hoverable cards. Each card navigates to `/directories/:id`.

---

### 7. Directory Detail Page (`/directories/:id`)

```
[← Справочники]  Города

┌────────────────┬─────────────────┬──────────────┐
│ Поле 1         │ Поле 2          │ Действия     │
├────────────────┼─────────────────┼──────────────┤
│  Москва        │ 495             │ Ред. / Удал. │
│  СПб           │ 812             │ Ред. / Удал. │
└────────────────┴─────────────────┴──────────────┘
                                        [+ Добавить запись]
```

---

### 8. My Documents Page (`/documents`)

Similar to Templates page — table with: название, шаблон, дата, действия (Открыть, Удалить).
"Создать документ" button opens a modal to pick a template.

---

## Modals

| Modal | Trigger | Content |
|---|---|---|
| CreateTemplateModal | "+ Создать шаблон" | Form: Название (required), Описание, Категория — primary "Создать" button |
| CreateDocumentModal | "+ Создать документ" | Form: Название (required), выбор шаблона из Select — primary "Создать" button |
| ImageUploadModal | Toolbar image button | URL input or file upload |
| PhrasingEditorModal | Edit phrasing | Phrase name + variant text editor |
| PhrasingsRefInsertModal | Insert phrasing ref | List of phrasings to choose from |
| PreviewModal | Preview button | Rendered document preview |

All modals use `antd Modal`, width 480-600px, confirm/cancel button pair.

---

## Drawers

| Drawer | Width | Content |
|---|---|---|
| PageSettingsDrawer | 360px | Page size (A4/A3/Letter), orientation, margins, default font/size/line-spacing |
| Revision history | 360px | List of revisions with date and restore button |

---

## Context Menu

Right-click in the editor (when not published) shows a custom context menu portal:
- Insert ParameterField
- Insert ConditionalBlock
- Insert NumberedClause
- Wrap in ConditionalInline

---

## Search & Replace Panel

Appears below toolbar (collapsible, Ctrl+F). Two inputs (find / replace) + Previous/Next/Replace/Replace All buttons inline.

---

## Special Visual Patterns

### Unsaved changes indicator
Small amber badge (`ExclamationCircleOutlined`) + "Есть несохранённые изменения" warning text (12px) next to template title.

### Published banner
Full-width info alert (`Alert type="info"`) just below the designer header: "Шаблон опубликован. Редактирование заблокировано." with option to unpublish.

### Tag colors
- `color="green"` → Опубликован
- `color="default"` (gray) → Черновик

### Slash command menu
Appears inline in editor on `/` keystroke. Floating list of commands (insert parameter, table, image, etc.) positioned at cursor. Keyboard-navigable (↑/↓, Enter, Escape).

### Table bubble menu
Appears when cursor is inside a table cell. Shows table-management actions: add/delete row, add/delete column, merge/split cells, column alignment, text orientation.

### Text bubble menu
Appears on text selection. Shows quick formatting: Bold, Italic, Underline, code, link, highlight.

---

## Responsiveness Notes

The app is primarily desktop-first. The navigation header collapses (menu items overflow) on narrow widths. The template designer and questionnaire pages are strictly two-column desktop layouts. The home page cards and lists are responsive with `antd Row/Col` grid.

---

## Key UX Interactions

1. **Click on parameter chip in document** → highlights the corresponding question in the left questionnaire panel (blue border animation, auto-scroll)
2. **Autosave** every 30s when there are unsaved changes (silent, no notification)
3. **Ctrl+S** → manual save with success notification
4. **Ctrl+F** → toggle search/replace panel
5. **Publish flow** → Popconfirm → template becomes read-only (editor disabled, save grayed out)
6. **Real-time collaboration** → multiple users see each other's cursors and answers update live via Yjs + WebSocket

---

## File/Export Flows

- Template: Export DOCX/PDF via Dropdown menu in designer header
- Document: Export DOCX/PDF via small buttons in workspace panel header
- Both trigger file download via `<a>` click on blob URL
