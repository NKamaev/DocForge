# DocForge-Next — Рабочие процессы (Operating Model)

## Onboarding нового разработчика

1. Прочитать `AGENTS.md` — проектный контракт
2. Изучить `docs/specs/layers.md` — архитектурные слои
3. Изучить `docs/architecture.md` — полное ТЗ
4. Запустить окружение: `./dev.sh`
5. Попробовать агентов: `@frontend`, `@backend`, `@architect`

## Процесс разработки фичи

```
PRD / задача → @architect → @backend / @frontend → @test → @review → commit
```

### 1. Планирование (@architect или @layer-architect)
- Разобрать требования, создать задачи в task-tracker
- Определить какие модули и слои затрагиваются
- Спроектировать DTO контракты (если новый API)
- Обновить `docs/specs/contracts/` при необходимости

### 2. Backend (@backend, @migrate)
```bash
# Flyway миграция
skill:flyway-migration

# Service + Controller + DTO
# JUnit + Testcontainers тесты
cd backend && ./gradlew test
```

### 3. Frontend (@frontend, @tiptap-specialist, @template-architect)
```bash
# UI компоненты (shadcn/ui)
skill:ui-migration-shadcn

# Кастомные TipTap ноды
skill:tiptap-node-add

# Vitest
cd frontend && npm test -- --run
```

### 4. Тестирование (@test)
- Unit тесты на бизнес-логику (JUnit / Vitest)
- E2E тесты Playwright (TDD: RED → GREEN)
```bash
skill:test-write
npx playwright test
```

### 5. Ревью (@review, @layer-architect)
- @review: качество кода, соответствие конвенциям
- @layer-architect: проверка границ слоев и контрактов
- Human review для критических изменений

### 6. Коммит
```bash
git commit -m "type(scope): description"
# task-tracker: task_transition { id: "TASK-NNN", to: "DONE", commit_hash: "..." }
```

## Миграция UI (текущая задача)

Специализированный процесс для замены Ant Design → shadcn/ui.

### Процесс
1. `@ui-migration` — запуск агента миграции
2. `skill:ui-migration-shadcn` — пошаговая инструкция
3. Playwright тесты пишутся ДО изменений (RED)
4. Context7 для актуальных API shadcn/ui и Tailwind
5. Дизайн из `docs/design/` (ClaudeDesign)

### Приоритет компонентов
1. Button, Input, Card — база
2. Table, Dialog, Select — сложные
3. TipTap Toolbar, ParticipantPanel — доменные

## Работа с задачами (task-tracker)

### Через агентов
```
@task создать задачу "Заменить Button на shadcn/ui" в проекте DocForge-Next
```

### Через MCP инструменты
- `task-tracker_task_list` — список задач
- `task-tracker_task_create` — создать задачу
- `task-tracker_task_transition` — сменить статус
- `task-tracker_board_overview` — доска задач

### Статусы
```
TODO → IN_PROGRESS → REVIEW → DONE
                  → BLOCKED → TODO
```

## Работа с ошибками (bug-report)

### Создание репорта
1. Пользователь нажимает кнопку "Сообщить об ошибке"
2. Заполняет описание, выбирает один или несколько элементов на странице (multi-select с накоплением)
3. POST /api/feedback → audit_log с массивом `elements: [{selector, label}]`

### Исправление
1. `@debug` — диагностика
2. `@test` — тест, воспроизводящий баг (RED)
3. `@backend` / `@frontend` — исправление
4. `@test` — верификация (GREEN)

## Автономная миграция (опционально)

При использовании Kiro Autopilot / Agent Teams:

### Процесс
1. Оркестратор (build mode) разбивает задачу на чанки по 200 строк
2. `@test` пишет RED Playwright тесты
3. `@ui-migration` реализует GREEN
4. `@review` + `@layer-architect` проверяют параллельно
5. Автоматический PR после прохождения всех проверок

### Лимиты
- Максимум 3 попытки исправления на задачу
- 200 строк максимум на один diff
- Human approve для критических изменений (безопасность, бэкенд)

## Коммуникационные паттерны

### Запрос помощи
- `/bug` — сообщить об ошибке с контекстом
- `/task` — создать/найти задачу
- `/help` — показать доступные агенты и скиллы

### Эскалация
1. Сначала агент по домену (@backend, @frontend, @tiptap-specialist)
2. Если не решается — @layer-architect (архитектурный контекст)
3. Если архитектурная проблема — @architect (перепроектирование)

## Quality gates (перед коммитом)

| Gate | Команда | Критерий |
|------|---------|----------|
| Backend тесты | `cd backend && ./gradlew test` | Все зелёные |
| Frontend сборка | `cd frontend && npx tsc --noEmit` | Нет TS ошибок |
| Frontend тесты | `cd frontend && npm test -- --run` | Все зелёные |
| E2E тесты | `npx playwright test` | Критические сценарии |
| Code review | `@review` | Нет замечаний |
| Layer check | `@layer-architect` | Нет нарушений слоев |
