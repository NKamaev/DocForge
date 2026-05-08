# DocForge-Next

Modular document constructor. Migration target from [DocForge](/home/nkamaev/Projects/DocForge).

## Migration status

Code copied from reference (`/home/nkamaev/Projects/DocForge`), migration not yet started. Frontend still uses Ant Design (to be replaced with shadcn/ui). See `docs/specs/migration-plan.md` for full plan.

## Architecture (from reference)

Modular monolith: Spring Boot 3.3 (Java 21, virtual threads) + React 18 + TypeScript.

**Backend modules** (`com.docforge.{module}`): `user`, `template`, `document`, `collaboration`.
- Modules communicate ONLY via `*Service` interfaces. Never import another module's Repository.
- Layers: `controller/` (thin) → `service/` → `repository/` + `model/` (JPA/`@Document`)
- DTOs are Java records (`*Request`, `*Response`, `*DTO`)
- Migrations: Flyway `V{NNN}__{description}.sql`
- Integration: GraalPython polyglot, Redis, Apache POI (DOCX), openhtmltopdf (PDF)

**Frontend modules** (`frontend/src/`): `api/`, `components/`, `store/` (Zustand), `types/`.
- UI: **shadcn/ui + Tailwind** (migrating from Ant Design)
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

# Frontend (Ant Design — to be migrated to shadcn/ui)
cd frontend && npm run dev           # vite on :5173
cd frontend && npm run test          # vitest + React Testing Library
cd frontend && npm run build         # tsc -b && vite build

# E2E (planned — Playwright, not yet set up)
npx playwright test
```

## Testing conventions

- Backend unit: `*Test.java` — JUnit 5 + Mockito
- Backend integration: `*IT.java` — `@SpringBootTest` + Testcontainers
- Frontend: Vitest (`vitest run`), jsdom environment, setup at `src/test/setup.ts`
- E2E: Playwright (mandatory after UI migration), tests in `frontend/tests/`

## Key migration rules

- Preserve `api/`, `store/` (Zustand), `types/` layers — only UI layer changes
- Components go in `frontend/src/components/ui/` (shadcn convention)
- Use Context7 MCP for current shadcn/ui, Tailwind, TipTap APIs
- Write Playwright tests before/during UI changes (TDD: RED → GREEN)
