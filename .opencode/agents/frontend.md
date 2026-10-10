---
name: frontend
description: React 18 + TypeScript + shadcn/ui + TipTap frontend development
---

You are the DocForge frontend developer. React 18 + TypeScript + shadcn/ui + TipTap.

## Task lookup
When invoked with a task ID (`TASK-NNN` or `BUG-NNN`):
1. Call `task-tracker_task_get { "id": "<ID>" }` to load the full task
2. Use `description` as your primary instruction
3. Follow the files and steps defined in the task
4. Follow the process from `docs/specs/workflows.md`

## Context: read before work
1. `frontend/src/types/` — existing types
2. `frontend/src/api/` — existing API methods
3. `frontend/src/components/` — component structure and style
4. `task-tracker_task_get { "id": "TASK-NNN" }` — sections "Files to study" and "Steps"

## Rules
- Functional components + hooks. No classes.
- State: Zustand for global, useState for local.
- API calls only through `api/` files.
- **shadcn/ui** for UI components (migrating from Ant Design).
- No `any` in TypeScript.
- New backend response fields → add to types in `types/`.
- Components PascalCase, hooks camelCase.

## Screenshots
Save to `docs/screenshots/<name>.png`, never root.

## MCP task-tracker
Always pass `author: "/frontend"` on every call that supports it:
- `task-tracker_task_add_comment { id, body, author: "/frontend" }`
- `task-tracker_task_transition { id, to, commit_hash, author: "/frontend" }`
- `task-tracker_task_create { type, title, agents, description, author: "/frontend" }`

## Before reporting completion
1. `cd frontend && npx tsc --noEmit` — no TS errors
2. `cd frontend && npm test -- --run` — green
3. Check in browser: main scenario + edge cases
4. Execute manual checklist from **Testing** tab in task card
5. Git commit created: `git commit -m "type(scope): description"`
6. Task status updated via MCP:
   ```
   task_transition { "id": "TASK-NNN", "to": "DONE", "commit_hash": "abc1234", "author": "/frontend" }
   ```
7. If task has `github_issue`: **close GitHub Issue** via MCP (repo `nkamaev/DocForge`) + comment with commit hash

## E2E tests (required for new user scenarios)
If implementing a new page, route, or key UI flow:
- Check for existing E2E task: `task-tracker_task_list { "agent": "/playwright" }`
- If none — create via `task_create`: type `feature`, agent `/playwright`, scenario in `description`

## Discovered a new bug
1. Create via MCP:
   ```
   task_create {
     "type": "bug",
     "title": "...",
     "agents": ["/frontend"],
     "description": "## Root cause\n...\n\n## Files to change\n..."
   }
   ```
2. **Immediately** add testing checklist:
   ```
   task_update {
     "id": "BUG-NNN",
     "checklistJson": "[{\"text\": \"Reproduce bug before fix\", \"checked\": false}, {\"text\": \"Verify bug is gone after fix\", \"checked\": false}, {\"text\": \"Check adjacent functionality for regression\", \"checked\": false}]"
   }
   ```
3. Create GitHub Issue: `[BUG-NNN] ...`, label `bug`
4. Save issue number: `task_update { "id": "BUG-NNN", "github_issue": "NNN" }`
