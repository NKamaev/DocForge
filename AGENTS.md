# DocForge-Next

Modular document constructor. Successor to [DocForge](/home/nkamaev/Projects/DocForge).

## Architecture

Modular monolith: Spring Boot 3.3 (Java 21, virtual threads) + React 18 + TypeScript.

**Backend modules** (`com.docforge.{module}`): `user`, `template`, `document`, `collaboration`, `directory`, `export`, `audit`, `upload`, `feedback`.
- Modules communicate ONLY via `*Service` interfaces. Never import another module's Repository.
- Layers: `controller/` (thin) → `service/` → `repository/` + `model/` (JPA/`@Document`)
- DTOs are Java records (`*Request`, `*Response`, `*DTO`)
- Migrations: Flyway `V{NNN}__{description}.sql`
- Integration: GraalPython polyglot, Redis, Apache POI (DOCX), openhtmltopdf (PDF)

**Frontend modules** (`frontend/src/`): `api/`, `components/`, `store/` (Zustand), `types/`.
- UI: **shadcn/ui + Tailwind** (migrating from Ant Design — see `docs/specs/migration-plan.md`)
- Editor: **TipTap 3** (`@tiptap/react`)
- Collaboration: Yjs + y-websocket
- Vite dev server on `:5173`, proxies `/api` → `:8080`, `/ws` → `ws://:8080`

## Essential commands

```bash
# Dev (uses dev.sh helper)
./dev.sh                  # docker-compose up + backend + frontend
./dev.sh status           # check running services
./dev.sh backend          # restart backend only
./dev.sh frontend         # restart frontend only
./dev.sh logs [backend|frontend]

# Backend
cd backend && ./gradlew bootRun
cd backend && ./gradlew test        # JUnit 5 + Mockito (*Test.java), Testcontainers (*IT.java)

# Frontend
cd frontend && npm run dev           # vite on :5173
cd frontend && npm run test          # vitest + React Testing Library
cd frontend && npm run build         # tsc -b && vite build

# E2E (Playwright)
npx playwright test
```

## Testing conventions

- Backend unit: `*Test.java` — JUnit 5 + Mockito
- Backend integration: `*IT.java` — `@SpringBootTest` + Testcontainers
- Frontend: Vitest (`vitest run`), jsdom environment, setup at `src/test/setup.ts`
- E2E: Playwright, tests in `frontend/tests/`

## Agents

Use `@agent-name` to invoke a custom agent: `@architect`, `@backend`, `@frontend`, `@tiptap-specialist`, `@template-architect`, `@document-collab-specialist`, `@format-engineer`, `@test`, `@review`, `@debug`, `@devops`, `@task`, `@migrate`, `@ui-migration`, `@layer-architect`, `@feedback-sync`.

All agents defined in `.opencode/agents/` with prompts, permissions, and MCP access.

## Mandatory delegation policy

The main agent (build mode) is a **COORDINATOR, NOT a doer**. It MUST delegate ALL implementation work to specialized subagents. No task is too small for delegation — even a one-line backend fix goes to `@backend`, even a single-component frontend change goes to `@frontend`.

### Hard rules

| # | Rule | Rationale |
|---|------|-----------|
| 1 | **NEVER** implement backend code directly (Java, Spring, SQL, Flyway) | `@backend` has domain context, conventions, and test patterns |
| 2 | **NEVER** implement frontend code directly (React, TypeScript, CSS, TipTap) | `@frontend` has component library, store patterns, and type knowledge |
| 3 | **NEVER** write test files directly (JUnit, Vitest, Playwright) | `@test` knows test structure, mocking, and coverage gaps |
| 4 | **NEVER** write TipTap/ProseMirror extensions directly | `@tiptap-specialist` has ProseMirror schema, node view, and plugin expertise |
| 5 | **NEVER** create/modify Docker/CI/CD configs directly | `@devops` knows multi-stage builds, healthchecks, and secrets |
| 6 | **ALWAYS** determine the domain of every task before acting | Without domain detection, delegation to the wrong agent is impossible |
| 7 | **ALWAYS** delegate — even a task with a single changed file | Size is irrelevant; expertise is what matters |

### Domain detection and delegation table

| Task description | Domain | Delegate to | Fallback |
|-----------------|--------|-------------|----------|
| "Add a REST endpoint", "Fix service method", "Create JPA entity", "Write Flyway migration", "MongoDB query" | Backend | `@backend` | `@migrate` for DB |
| "Add React component", "Implement page", "Fix CSS", "Update Zustand store", "Add API client call" | Frontend | `@frontend` | `@ui-migration` for shadcn |
| "Custom ProseMirror node", "TipTap extension", "Slash command", "Plugin" | TipTap | `@tiptap-specialist` | — |
| "Design module structure", "Data model review", "API contract", "Create subtasks" | Architecture | `@architect` | `@layer-architect` for audit |
| "Template parameter type", "Questionnaire layout", "Condition builder" | Templates | `@template-architect` | `@architect` for schema |
| "Yjs sync broken", "WS disconnect", "DOCX formatting", "PDF missing text" | Collab/Export | `@document-collab-specialist` | — |
| "FIO declension wrong", "Date format locale", "Number grouping" | Formatting | `@format-engineer` | `@backend` for impl |
| "Write tests", "Add coverage", "Integration test" | Testing | `@test` | — |
| "Code review", "Check conventions" | Review | `@review` | `@layer-architect` for layers |
| "Bug — diagnose and fix" | Debug | `@debug` | domain agent |
| "Dockerfile", "docker-compose", "CI pipeline" | DevOps | `@devops` | — |
| "Migrate AntD → shadcn", "Replace Button" | UI Migration | `@ui-migration` | `@frontend` for adjacent |
| "Flyway migration file" | Database | `@migrate` | `@backend` |
| "Audit layer boundaries", "Enforce contract rules" | Layers | `@layer-architect` | `@architect` |
| "Sync feedback → tasks" | Feedback | `@feedback-sync` | `@task` |

### Epic tasks (multi-domain coordination)

When a task spans multiple domains (e.g. backend + frontend + tests):

1. **Main agent stays at coordinator level** — decomposes, delegates, verifies
2. **NEVER implement any sub-task yourself** — always delegate
3. **Sequential chain** if dependencies exist: `@architect` → `@backend` → `@frontend` → `@test`
4. **Parallel** if independent: `@backend` + `@frontend` simultaneously

**Example: "Add audit module — backend API + frontend page"**

```
Step 1: @architect        — design module structure, data model, API contract
Step 2: @backend           — implement API endpoints, service, repository
Step 3: @frontend          — implement audit log page, filters, table
Step 4: @test              — integration test for audit API + E2E for audit page
Step 5: @review            — full code review
```

**Example: "Fix button styling on login page" (small task)**

```
Step 1: @frontend          — fix the button component
Step 2: @test              — verify with Playwright snapshot
Step 3: @review            — quick review
```

Even this small task MUST NOT be done by the main agent directly.

### What the main agent DOES do

The main agent's legitimate activities are **coordination only**:

- ✅ Read and understand the task description from task-tracker
- ✅ Decompose complex tasks into single-domain subtasks
- ✅ Determine which subagent(s) are needed via the delegation table
- ✅ Delegate to the correct agent with **full context**: task ID, files to study, expected outcome
- ✅ Coordinate across agents — sequential (wait for A before B) or parallel
- ✅ Verify results — run `@review`, check test output, validate checklist
- ✅ Update task status (`task_transition`) and create git commits
- ✅ Close GitHub Issues upon completion
- ✅ Decompose epics into subtasks with `parent_id` via `task-tracker_task_create`

### What the main agent MUST NOT do

| Forbidden action | Instead |
|-----------------|---------|
| ✗ Write Java/Spring Boot code | → `@backend` |
| ✗ Write React/TypeScript/Tailwind code | → `@frontend` |
| ✗ Write TipTap/ProseMirror extensions | → `@tiptap-specialist` |
| ✗ Write test files (JUnit, Vitest, Playwright) | → `@test` |
| ✗ Create/modify Flyway migrations | → `@migrate` or `@backend` |
| ✗ Create/modify Docker/CI/CD configs | → `@devops` |
| ✗ Modify architecture docs for new modules | → `@architect` |
| ✗ Review own or any code | → `@review` |

### Subagent contract

Each subagent, upon receiving a delegation, MUST follow this protocol:

1. **Load** the task via `task-tracker_task_get { "id": "TASK-NNN" }`
2. **Read** the full `description` — follow the "Files to study", "Steps" sections
3. **Study** referenced files before writing any code
4. **Implement** the solution following their domain-specific rules
5. **Run tests** relevant to their domain (`./gradlew test`, `npm test`, `npx playwright test`)
6. **Report back** to the main agent with a summary of what was done
7. **Do NOT transition** the top-level task to DONE — only the main agent coordinates final status

### Enforcement

If the main agent catches itself about to write implementation code instead of delegating, it MUST stop and route to the appropriate subagent. If uncertain about the domain, delegate to `@architect` first for analysis.

Direct implementation by the main agent is a **violation** of this policy and should be corrected immediately.

## MCP servers

- `context7` — current docs for shadcn/ui, Tailwind, TipTap
- `playwright` — E2E testing and element selection
- `task-tracker` — task CRUD (all agents use this MCP for task operations)

## Skills

Use `skill:` in agent prompts to invoke a reusable skill:
- `skill:template-create` — standardized template creation process
- `skill:tiptap-node-add` — adding custom TipTap/ProseMirror nodes
- `skill:flyway-migration` — creating Flyway database migrations
- `skill:test-write` — writing tests (JUnit, Vitest, Playwright)
- `skill:ui-migration-shadcn` — step-by-step Ant Design → shadcn/ui migration
- `skill:export-fix` — diagnosing DOCX/PDF export issues
- `skill:param-format-add` — adding new parameter format types
- `skill:bug-report` — bug report process with element selection
- `skill:bug-report-to-task` — sync bug reports from audit_log to TaskTracker

All skills defined in `.opencode/skills/`.

## Workflows

See `docs/specs/workflows.md` for complete operating model:
- Feature development process (plan → backend/frontend → test → review → commit)
- UI migration process (Ant Design → shadcn/ui)
- Bug report and fix workflow
- Quality gates before commit

## Configuration

See `docs/architecture.md` for full technical specification.
See `docs/specs/layers.md` for architectural layer rules.
See `opencode.json` for agent permissions and MCP config.

## Key migration rules

- Preserve `api/`, `store/` (Zustand), `types/` layers — only UI layer changes
- Components go in `frontend/src/components/ui/` (shadcn convention)
- Use Context7 MCP for current shadcn/ui, Tailwind, TipTap APIs
- Write Playwright tests before/during UI changes (TDD: RED → GREEN)
