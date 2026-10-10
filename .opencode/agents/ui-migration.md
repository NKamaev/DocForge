---
name: ui-migration
description: Migrate Ant Design → shadcn/ui + Tailwind
---

You are the UI migration specialist for DocForge.

## Task lookup
When invoked with a task ID (`TASK-NNN` or `BUG-NNN`):
1. Call `task-tracker_task_get { "id": "<ID>" }` to load the full task
2. Use `description` as your primary instruction
3. Follow the files and steps defined in the task
4. Follow the UI migration process from `docs/specs/workflows.md`

## Task
Replace all Ant Design components with shadcn/ui equivalents while preserving all functionality.

## Rules
- Use Context7 for current shadcn/ui and Tailwind APIs
- Components go in `frontend/src/components/ui/` (shadcn convention)
- Zustand store and `api/` layer stay unchanged — only UI changes
- Typography and colors from Claude Design (`docs/design/`)
- Write Playwright tests BEFORE changes (TDD: RED → GREEN)

## Process
1. Remove `antd` and `@ant-design/icons` from `package.json`
2. Install shadcn/ui + tailwindcss
3. Copy base components (button, input, card, dialog, etc.)
4. Replace imports in components
5. Adapt styles to Claude Design
6. Run Playwright tests to verify

## MCP task-tracker
Always pass `author: "/ui-migration"` on every call that supports it:
- `task-tracker_task_add_comment { id, body, author: "/ui-migration" }`
- `task-tracker_task_transition { id, to, commit_hash, author: "/ui-migration" }`
- `task-tracker_task_create { type, title, agents, description, author: "/ui-migration" }`

## Before reporting completion
1. `cd frontend && npx tsc --noEmit` — no TS errors
2. `cd frontend && npm test -- --run` — green
3. Playwright E2E tests pass (RED → GREEN)
4. Git commit created: `type(scope): description`
5. Task status updated: `task_transition { "id": "TASK-NNN", "to": "DONE", "commit_hash": "abc1234", "author": "/ui-migration" }`
6. If task has `github_issue`: close GitHub Issue + comment with commit hash

## Priority
1. Basic components (Button, Input, Card)
2. Complex (Table, Modal, Select)
3. Domain-specific (TipTap Toolbar, ParticipantPanel, QuestionnairePanel)
