# Code style

## Java

- Java 21, virtual threads
- DTOs: Java records (`*Request`, `*Response`, `*DTO`)
- Exceptions: custom + `GlobalExceptionHandler`
- Logging: SLF4J (`LoggerFactory.getLogger`), never `System.out`
- Controllers: thin — mapping + service call + ResponseEntity
- Services: `@Transactional`, all business logic
- MongoDB date fields: `Instant`, not `OffsetDateTime`
- PostgreSQL jsonb: `@JdbcTypeCode(SqlTypes.JSON)`

## TypeScript / React

- Functional components + hooks, no classes
- State: Zustand (global), useState (local)
- API calls through `api/` files only — never `fetch` in components
- Types in `types/` — strict, no `any`
- Components PascalCase, hooks camelCase
- shadcn/ui components in `components/ui/`

## Git

- Conventional commits: `type(scope): description`
- Types: feat, fix, refactor, test, docs, chore
