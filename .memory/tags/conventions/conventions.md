# Конвенции проекта

> Тег: `conventions:conventions` · Обновлено: `2026-10-10`

## [2026-10-10 10:00] Backend

- **Пакеты:** `com.docforge.{module}`; REST `/api/{resource}` (мн. число, kebab-case); PG-таблицы snake_case; Mongo-коллекции мн. число.
- **Controller** — тонкий (mapping → service → DTO). Вся логика в `*Service`/`*ServiceImpl`. DTO — Java records (`*Request`, `*Response`, `*DTO`).
- **Модульная изоляция:** кросс-модульные вызовы только через `*Service` интерфейсы; сервисы возвращают DTO, не entity; `@Transactional` на методах сервисов; `Repository` своего модуля — только свой.
- **Ошибки:** кастомные exceptions + `GlobalExceptionHandler` (ProblemDetail, common/exception, 64 строки).
- **Миграции:** Flyway `V{NNN}__{description}.sql`; JPA `ddl-auto: validate`.
- **Тесты:** unit `*Test.java` (JUnit 5 + Mockito), integration `*IT.java` (`@SpringBootTest`, профиль `test`, контейнеры из docker-compose). `*Test.java` — в `src/test/java/com/docforge/`.
- **Источник:** `AGENTS.md`, `docs/architecture.md:6.2`, explore backend report.

## Frontend

- Функциональные компоненты + hooks; Zustand — только для глобального auth; состояние страниц — компонент-локальное.
- API через `src/api/*.ts` (см. `frontend:api-layer`). Типы — `src/types/` (interfaces), там же `templateModel.ts` (enum-ы ParameterType, форматы, Condition).
- UI — shadcn/ui (`components/ui/`) + Tailwind; редактор — TipTap, кастомные расширения в extensions/.
- Тесты: Vitest + React Testing Library (jsdom, setup `src/test/setup.ts`); E2E Playwright в `frontend/e2e/` (3 chromium-проекта, baseURL :5173).
- Команды: `npm run dev | build | test | test:e2e`; backend `./gradlew bootRun | test`.

## Прочее

- Единственный источник правды кода: при противоречии тега памяти и кода — верить коду, тег помечать устаревшим.
- Структура Агентской делегации и правила — в `AGENTS.md`.

<!-- Новые записи добавляй СВЕРХУ. -->