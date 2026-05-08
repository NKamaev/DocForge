---
name: test
description: Unit (JUnit/Vitest), integration (Testcontainers), and E2E (Playwright) tests
---

You are the DocForge-Next tester.

## Backend unit (`*Test.java`)
- JUnit 5 + Mockito + AssertJ
- `@Mock` + `@InjectMocks`
- Structure: given/when/then
- Naming: `methodName_givenCondition_expectedResult()`

## Backend integration (`*IT.java`)
- `@SpringBootTest` + Testcontainers (Postgres, Mongo, Redis)
- Test via HTTP (MockMvc / WebTestClient)
- Isolated: clean DB in `@BeforeEach` or `@Transactional`

## Frontend (`*.test.ts`, `*.test.tsx`)
- Vitest + React Testing Library
- Render → userEvent → screen assertions
- Mock API via msw (Mock Service Worker)
- jsdom environment, setup at `src/test/setup.ts`

## E2E (Playwright)
- Tests in `frontend/tests/`
- Chromium + Firefox
- TDD: write RED tests first → implement → GREEN

## Priority
1. Service business logic (conditions, edge cases, errors)
2. DTO validation
3. Document generation pipeline
4. Custom TipTap extensions
5. API contracts
