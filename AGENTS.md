# DocForge

Modular document constructor. Successor to [DocForge](/home/nkamaev/Projects/DocForge).

## Долговременная память (`.memory/`)

Память проекта версионируется в Git и хранится в `.memory/`:
- теги — `.memory/tags/{category}/{file}.md`;
- сводки — `.memory/daily/YYYY-MM-DD.md`, `.memory/weekly/YYYY-Www.md`, `.memory/monthly/YYYY-MM.md`;
- реестр тегов — `.memory/tag-index.md` (единственный источник списка тегов).

Навык: `skill:memory-manager` — полная процедура управления памятью и Bootstrap.

### В начале сессии

1. Прочитай `.memory/tag-index.md` — при большом размере только релевантные секции (Grep).
2. Всегда загружай `best-practices.md` и набор `pitfalls-*.md`, если они есть.
3. Догрузи теги, релевантные запросу пользователя.
4. При противоречии тега и кода — верь коду, тег пометь устаревшим и обнови.

### В конце сессии

1. Создай/обнови тег тикета или тематический тег; зарегистрируй новый тег в `tag-index.md`.
2. Запиши вывод в `.memory/daily/YYYY-MM-DD.md`.
3. Проверь целостность: `.opencode/skills/memory-manager/scripts/memory-lint.sh`.
4. Закоммить изменения памяти: `git add .memory/ && git commit -m "memory: <описание>"`.
   Для `.memory/` это явное исключение из правила «не коммитить без запроса».

### Рантаймы без project-skills

Если `skill:memory-manager` недоступен — выполняй шаги выше вручную по этому блоку;
шаблоны файлов лежат в `.opencode/skills/memory-manager/templates/`.

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

## Delegation policy

The main agent (build mode) is a **COORDINATOR first** — it decomposes epics, delegates specialized work, and verifies results. However, for **small, well-understood tasks** (1–5 files) that follow clear existing patterns, the main agent CAN implement directly without the overhead of delegation.

**Guiding principle:** Delegate when it adds value; implement directly when the pattern is obvious and the context is already loaded. If uncertain — delegate.

### Hard rules

| # | Rule | Rationale |
|---|------|-----------|
| 1 | **Delegate** complex backend work (new modules, intricate business logic, architecture changes). **Can implement** small endpoints, DTOs, or service methods that follow an existing pattern. | If context (existing files, conventions) is already loaded, direct implementation is more efficient. |
| 2 | **Delegate** complex frontend work (new pages, non-trivial state management, new component patterns). **Can implement** small components or UI tweaks that follow an existing pattern. | When analogous components exist and API calls follow a convention, direct implementation is safe. |
| 3 | **NEVER** write test files directly (JUnit, Vitest, Playwright) | `@test` knows test structure, mocking, and coverage gaps |
| 4 | **NEVER** write TipTap/ProseMirror extensions directly | `@tiptap-specialist` has ProseMirror schema, node view, and plugin expertise |
| 5 | **NEVER** create/modify Docker/CI/CD configs directly | `@devops` knows multi-stage builds, healthchecks, and secrets |
| 6 | **ALWAYS** determine the domain of every task before acting | Without domain detection, delegation to the wrong agent is impossible |
| 7 | **Delegate multi-domain epics** (backend + frontend + tests). **Can implement** single-domain changes of 1–5 files when following clear existing patterns. | Complexity and cross-domain coordination justify delegation overhead. |

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

This small task is delegated because it spans frontend + tests + review — three distinct domains. The main agent CANNOT write tests (rule #3), so delegation is the right call. For a purely frontend change (e.g. tweaking a single component's Tailwind class), the main agent could implement directly.

### What the main agent DOES do

The main agent's legitimate activities are **coordination first, with direct implementation where appropriate**:

- ✅ Read and understand the task description from task-tracker
- ✅ Decompose complex tasks into single-domain subtasks
- ✅ Determine which subagent(s) are needed via the delegation table
- ✅ Delegate to the correct agent with **full context**: task ID, files to study, expected outcome
- ✅ Coordinate across agents — sequential (wait for A before B) or parallel
- ✅ **Implement small, well-understood tasks directly** (1–5 files, existing patterns, loaded context)
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

If uncertain whether a task qualifies for direct implementation, err on the side of delegation. Complex or cross-domain work MUST be delegated. For simple, pattern-based changes with loaded context, direct implementation is acceptable.

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
- `skill:memory-manager` — long-term project memory (`.memory/`): tags, daily/weekly/monthly summaries

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
