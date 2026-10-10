# План перехода DocForge на методологию OpenCode

**Цель**: Перенести проект на методологию OpenCode (Stage 1→5), заменить Ant Design на shadcn/ui, переписать фронтенд по дизайну из DocForge.zip.

**Структура**:
- Текущий проект: `/home/nkamaev/Projects/DocForge` (оставить как есть, референс)
- Новый проект: `/home/nkamaev/Projects/DocForge-Next` (копия + рефакторинг)

---

## Этап 1: Подготовка и Stage 1 (1-2 дня)

### 1.1 Копирование проекта
```bash
cp -r /home/nkamaev/Projects/DocForge /home/nkamaev/Projects/DocForge-Next
cd /home/nkamaev/Projects/DocForge-Next
git init  # новая история
```

### 1.2 Базовый проектный контракт (Stage 1)
Создать `AGENTS.md` (основной файл для OpenCode):
```markdown
# DocForge-Next — Проектный контракт

## Проект
DocForge-Next — конструктор документов. Модульный монолит: Spring Boot 3.3 + React 18 + TypeScript.

## Документация
- docs/architecture.md — ТЗ, архитектура, модель данных, API
- docs/specs/layers.md — описание слоев и контрактов
- docs/specs/contracts/ — спецификации контрактов
- docs/design/ — дизайн из DocForge.zip (ClaudeDesign)

## Быстрый старт
docker-compose up -d
cd backend && ./gradlew bootRun
cd frontend && npm run dev

## Архитектурные правила
- Модульный монолит: user, template, document, collaboration, directory
- Модули общаются ТОЛЬКО через интерфейсы *Service
- НИКОГДА не импортируй Repository другого модуля
- Каждый модуль содержит README.md

## Слои (Layers)
- API Layer: тонкие контроллеры
- Service Layer: бизнес-логика
- Domain Layer: чистая бизнес-логика (в разработке)
- Data Access Layer: Repository, Entity/Document
- Integration Layer: GraalPython, Redis, Export

## Конвенции кода

### Java (backend/)
- Java 21, virtual threads
- DTO — Java records (*Request, *Response, *DTO)
- Логирование — SLF4J
- Миграции: Flyway V{NNN}__{description}.sql

### TypeScript (frontend/)
- React 18, функциональные компоненты, hooks
- State: Zustand
- API: файлы в api/, Типы: в types/
- UI: shadcn/ui + Tailwind CSS (замена Ant Design)
- Именование: PascalCase для компонентов, camelCase для хуков

## Тестирование
- Backend: JUnit 5 + Mockito (*Test.java), Testcontainers (*IT.java)
- Frontend: Vitest + React Testing Library
- Frontend E2E: **Playwright (обязательно)**

## Специализированные команды (slash-commands)
/architect, /backend, /frontend, /tiptap, /test, /review, /devops, /migrate, /api, /debug, /ui-migration
```

> **Важно**: `CLAUDE.md` удалить или сделать симлинком для совместимости с Claude Code.

---

## Этап 2: Stage 2 — OpenCode инфраструктура (2-3 дня)

### 2.1 Создать `opencode.json` (корень проекта)
```json
{
  "$schema": "https://opencode.ai/config.json",
  "project": "DocForge-Next",
  "instructions": [
    "AGENTS.md",
    "docs/architecture.md",
    "docs/specs/layers.md"
  ],
  "mcp": {
    "context7": {
      "type": "local",
      "command": ["npx", "-y", "@upstash/context7-mcp"],
      "description": "Актуальная документация библиотек (shadcn/ui, Tailwind, TipTap)"
    },
    "playwright": {
      "type": "local",
      "command": ["npx", "@playwright/mcp@latest"],
      "description": "E2E тестирование и выбор элементов UI"
    }
  },
  "agent": {
    "plan": {
      "tools": { "bash": false, "write": false, "edit": false }
    },
    "build": { "tools": { "all": true } },
    "backend-dev": {
      "tools": { "read": true, "write": true, "edit": true, "bash": true },
      "mcp": { "context7": "allow" }
    },
    "frontend-dev": {
      "tools": { "read": true, "write": true, "edit": true, "bash": true },
      "mcp": { "context7": "allow", "playwright": "allow" }
    },
    "ui-migration": {
      "tools": { "read": true, "write": true, "edit": true, "bash": true },
      "mcp": { "context7": "allow", "playwright": "allow" }
    }
  },
  "permission": {
    "tool": { "webfetch": "ask", "websearch": "allow" },
    "skill": { "*": "allow", "experimental-*": "deny" }
  }
}
```

### 2.2 Запуск MCP серверов
При старте OpenCode MCP серверы запускаются автоматически согласно `opencode.json`.

Для ручной проверки:
```bash
# Context7 (документация)
npx -y @upstash/context7-mcp

# Playwright (E2E + выбор элементов)
npx @playwright/mcp@latest
```

**Важно для разработчика**: Playwright MCP дает агентам инструменты:
- `browser_navigate` — открыть страницу
- `browser_take_screenshot` — скриншот
- `browser_click` / `browser_element_select` — выбор элемента (как в Claude Design)
- `browser_fill_form` — заполнение форм
- `browser_evaluate` — выполнение JavaScript

### 2.3 Создать спецификации слоев
`docs/specs/layers.md`:
```markdown
# Архитектурные слои DocForge-Next

## Обзор
Модульный монолит с явным разделением на слои. Каждый слой имеет строго определенную зону ответственности.

## Слои бэкенда (com.docforge.{module})

### API Layer
- Пакет: `controller/`
- Ответственность: HTTP маппинг, валидация DTO, вызов сервисов
- Контракт: REST API (docs/architecture.md секция 4)

### Service Layer
- Пакет: `service/`
- Ответственность: координация, вызовы доменных объектов и репозиториев
- Контракт: интерфейсы `*Service.java`

### Domain Layer
- Пакет: `domain/`
- Ответственность: чистая бизнес-логика (условия, форматирование, генерация)

### Data Access Layer
- Пакет: `repository/`, `model/`
- Ответственность: работа с PostgreSQL (JPA) и MongoDB

### Integration Layer
- Пакет: `integration/`, `export/`
- Ответственность: GraalPython, Redis, экспорт DOCX/PDF

## Слои фронтенда (frontend/src/)

### UI Layer
- Папка: `components/`, `pages/`
- Ответственность: React-компоненты, shadcn/ui

### State Layer
- Папка: `store/`
- Ответственность: Zustand хранилища

### API Layer
- Папка: `api/`
- Ответственность: HTTP клиенты к бэкенду

### Types Layer
- Папка: `types/`
- Ответственность: TypeScript типы

### Domain Layer
- Папка: `domain/`
- Ответственность: бизнес-правила фронтенда
```

### 2.4 Создать папку `docs/specs/contracts/`
- `api-contracts.md` — REST API из architecture.md
- `service-contracts.md` — интерфейсы сервисов
- `data-contracts.md` — схемы БД

### 2.5 Создать `instructions/`
- `security.md` — правила безопасности
- `style.md` — стили кода

---

## Этап 3: Stage 3 — Project-specific агенты (1-2 дня)

Создать в `.opencode/agents/`:

1. `layer-architect.md` — анализ слоев и контрактов
2. `tiptap-specialist.md` — специалист по TipTap 3, кастомные ноды
3. `template-architect.md` — шаблоны, параметры, условия
4. `document-collab-specialist.md` — Yjs, WebSocket, экспорт
5. `ui-migration.md` — миграция на shadcn/ui, адаптация дизайна
6. `format-engineer.md` — форматирование (ФИО, даты, числа)

Пример `ui-migration.md`:
```markdown
---
name: ui-migration
description: Миграция фронтенда с Ant Design на shadcn/ui. Адаптация дизайна ClaudeDesign.
tools: [read, write, edit, bash]
---

# UI Migration Agent

Специализированный агент для перевода проекта с Ant Design на shadcn/ui.

## Задачи
1. Замена всех компонентов Ant Design на shadcn/ui аналоги
2. Настройка Tailwind CSS
3. Адаптация дизайна из DocForge.zip (ClaudeDesign)
4. Сохранение функциональности TipTap редактора

## Правила
- Используй Context7 для актуальных API shadcn/ui и Tailwind
- Компоненты копируются в `frontend/src/components/ui/` (shadcn принцип)
- Типографика и цвета берутся из дизайна DocForge.zip
- Zustand store и api/ не меняются (только UI слой)

## Процесс миграции
1. Удалить antd из package.json
2. Установить shadcn/ui + tailwindcss
3. Скопировать базовые компоненты (button, input, card, dialog...)
4. Заменить импорты в компонентах
5. Адаптировать стили под ClaudeDesign
```

---

## Этап 4: Stage 4 — Skills и permissions (1 день)

Создать в `.opencode/skills/`:

1. `template-create/SKILL.md`
```markdown
---
name: template-create
description: Стандартизированный процесс создания шаблона
---
## Шаги
1. Создать параметры (Parameters)
2. Настроить опросный лист (Questionnaire)
3. Добавить условия (Conditions)
4. Проверить через TemplateValidator
```

2. `tiptap-node-add/SKILL.md` — добавление кастомных нод TipTap
3. `flyway-migration/SKILL.md` — создание миграций Flyway
4. `test-write/SKILL.md` — написание тестов (JUnit/Vitest/Playwright)
5. `ui-migration-shadcn/SKILL.md` — пошаговая миграция UI
6. `export-fix/SKILL.md` — исправление экспорта DOCX/PDF
7. `param-format-add/SKILL.md` — добавление форматов параметров
8. `bug-report/SKILL.md` — процесс создания репорта об ошибке с выбором элемента

Пример `bug-report/SKILL.md`:
```markdown
---
name: bug-report
description: Создание репорта об ошибке с выбором элемента (как в Claude Design)
---
## Процесс
1. Пользователь нажимает кнопку "Сообщить об ошибке"
2. Открывается модальное окно с текстовым полем
3. Пользователь может выбрать элемент на странице (через Playwright MCP или browser API)
4. Кнопка выбора элемента активирует "крестик" (crosshair) — клик по проблемному элементу
5. В модалку подставляется CSS-селектор выбранного элемента
6. Пользователь пишет описание ошибки и отправляет
7. Отправляется POST /api/feedback с данными элемента и описанием
```

---

## Этап 4.5: Настройка Playwright (обязательно!)

### Зачем нужен Playwright
- E2E тесты обязательны для проверки работы фронтенда после миграции на shadcn/ui
- Проверка доступности элементов, навигации, форм
- Интеграция с функционалом «Сообщить об ошибке»

### Установка
```bash
cd /home/nkamaev/Projects/DocForge-Next/frontend
npm init playwright@latest
# Выбрать: TypeScript, tests/ папку, GitHub Actions: NO
```

### Настройка playwright.config.ts
```typescript
import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './tests',
  fullyParallel: true,
  webServer: {
    command: 'npm run dev',
    url: 'http://localhost:5173',
    reuseExistingServer: !process.env.CI,
  },
  use: {
    baseURL: 'http://localhost:5173',
    screenshot: 'only-on-failure',
    trace: 'on-first-retry',
  },
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
    { name: 'firefox', use: { ...devices['Desktop Firefox'] } },
  ],
});
```

### Базовые тесты (tests/)
Создать `tests/homepage.spec.ts`:
```typescript
import { test, expect } from '@playwright/test';

test('домашняя страница загружается', async ({ page }) => {
  await page.goto('/');
  await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
});

test('навигация в шаблоны', async ({ page }) => {
  await page.goto('/');
  await page.getByText('Шаблоны').click();
  await expect(page).toHaveURL(/.*templates/);
});
```

### Запуск
```bash
npx playwright test          # headless
npx playwright test --ui     # визуальный режим
```

---

## Этап 4.7: Функционал «Сообщить об ошибке» (как в Claude Design)

### Описание функционала
При нажатии кнопки «Сообщить об ошибке» на фронтенде:
1. Открывается модальное окно с текстовым полем
2. Пользователь может выбрать элемент на странице (через Playwright MCP `browser_element_select`)
3. Кнопка выбора элемента активирует «крестик» (crosshair) — пользователь кликает по проблемному элементу
4. В модалку подставляется CSS-селектор выбранного элемента
5. Пользователь пишет описание ошибки и отправляет

### Реализация на фронтенде
Создать компонент `ErrorReportButton.tsx`:
```tsx
// frontend/src/components/ErrorReportButton.tsx
import { useState } from 'react';
import { Button } from '@/components/ui/button'; // shadcn
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';

export function ErrorReportButton() {
  const [open, setOpen] = useState(false);
  const [selectedElement, setSelectedElement] = useState('');
  const [description, setDescription] = useState('');

  const handleSelectElement = async () => {
    // Интеграция с Playwright MCP (через бекенд API)
    // Вызывает эндпоинт /api/debug/select-element
    const res = await fetch('/api/debug/select-element', { method: 'POST' });
    const data = await res.json();
    setSelectedElement(data.selector); // например: "button#submit"
  };

  const handleSubmit = async () => {
    await fetch('/api/feedback', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        element: selectedElement,
        description,
        url: window.location.pathname,
      }),
    });
    setOpen(false);
  };

  return (
    <>
      <Button variant="outline" size="sm" onClick={() => setOpen(true)}>
        Сообщить об ошибке
      </Button>
      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Сообщить об ошибке</DialogTitle>
          </DialogHeader>
          <div className="space-y-4">
            <div>
              <label>Проблемный элемент:</label>
              <div className="flex gap-2">
                <input
                  value={selectedElement}
                  readOnly
                  placeholder="Выберите элемент на странице"
                  className="flex-1 p-2 border rounded"
                />
                <Button onClick={handleSelectElement} type="button">
                  Выбрать элемент
                </Button>
              </div>
            </div>
            <div>
              <label>Описание ошибки:</label>
              <textarea
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                className="w-full p-2 border rounded"
                rows={4}
              />
            </div>
            <Button onClick={handleSubmit}>Отправить</Button>
          </div>
        </DialogContent>
      </Dialog>
    </>
  );
}
```

### Бэкенд API (Spring Boot)
Добавить контроллер `FeedbackController.java`:
```java
@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {
    @PostMapping
    public void reportIssue(@RequestBody FeedbackRequest request) {
        // Сохранение в audit_log или отправка в систему трекинга
        log.info("Bug report: element={}, desc={}", request.element(), request.description());
    }

    record FeedbackRequest(String element, String description, String url) {}
}
```

### Интеграция с Playwright MCP (для выбора элемента)
Создать эндпоинт `/api/debug/select-element`, который:
1. Через WebSocket/SSE подключается к запущенному Playwright
2. Активирует `browser_element_select`
3. Возвращает CSS-селектор выбранного элемента

Или упрощенный вариант (чисто фронтенд):
Использовать нативный API браузера `document.elementsFromPoint()` при клике в режиме выбора.

---

## Этап 5: Stage 5 — Operating Model (1 день)

Обновить `AGENTS.md` с описанием командных процессов.

Создать `docs/specs/workflows.md`:
```markdown
# Рабочие процессы DocForge-Next

## Onboarding нового разработчика
1. Прочитать AGENTS.md
2. Изучить docs/specs/layers.md
3. Запустить docker-compose up -d
4. Попробовать /frontend и /backend команды

## Процесс разработки фичи
1. /architect — спроектировать изменения
2. /backend или /frontend — реализация
3. /tiptap (для редактора) — специфичные задачи
4. /test — написание тестов (вкл. Playwright E2E)
5. /review — код-ревью

## Миграция UI (текущая задача)
1. /ui-migration — запуск агента миграции
2. Следовать skill: ui-migration-shadcn
3. Использовать Context7 для актуальных API
4. Дизайн брать из docs/design/ (распаковка DocForge.zip)
5. Обязательно покрыть Playwright тестами

## Ревью и выкатка
1. /review проверяет соответствие слоям и контрактам
2. Human review для критических изменений
3. Проверка Playwright тестов: npx playwright test
4. git commit с понятным сообщением
```

---

## Этап 6: Миграция UI — Ant Design → shadcn/ui (3-5 дней)

### 6.1 Подготовка фронтенда
```bash
cd /home/nkamaev/Projects/DocForge-Next/frontend
npm uninstall antd @ant-design/icons
npx shadcn@latest init  # настройка Tailwind + shadcn
```

### 6.2 Копирование базовых компонентов
```bash
npx shadcn@latest add button input card dialog table select checkbox radio...
```

### 6.3 Распаковка дизайна
```bash
cd /home/nkamaev/Projects/DocForge-Next/docs/design
unzip ../../archive/reference/DocForge.zip -d .  # распаковать дизайн (архивирован в docs/archive/)
```
> После распаковки — показать структуру, чтобы адаптировать компоненты.

### 6.4 Замена компонентов (через агента)
Вызвать: `@ui-migration начать замену Ant Design на shadcn/ui по дизайну из docs/design/`

Приоритет:
1. Базовые компоненты (Button, Input, Card)
2. Сложные (Table, Modal, Select)
3. Специфичные (TipTap Toolbar, ParticipantPanel)

### 6.5 Добавление кнопки «Сообщить об ошибке»
```bash
# Создать компонент
touch frontend/src/components/ErrorReportButton.tsx
# Добавить кнопку в AppLayout.tsx
@ui-migration добавить кнопку «Сообщить об ошибке» в шапку сайта с функционалом выбора элемента
```

Приоритет тестирования:
1. `tests/homepage.spec.ts` — базовая навигация
2. `tests/template-design.spec.ts` — работа конструктора
3. `tests/error-report.spec.ts` — функционал жалобы на дизайн

---

## Этап 7: Финализация (1 день)

1. Проверить, что все агенты и скиллы работают
2. Запустить тесты: `./gradlew test`, `npm run test`, `npx playwright test`
3. Обновить README.md со ссылкой на новую структуру
4. Сделать git commit с описанием перехода

---

## Итоговая структура проекта

```
DocForge-Next/
├── .opencode/
│   ├── agents/          # Stage 3 (6+ агентов)
│   └── skills/          # Stage 4 (8+ скиллов)
├── docs/
│   ├── specs/           # Stage 2 (layers.md, contracts/, workflows.md)
│   ├── design/          # Дизайн из DocForge.zip
│   └── architecture.md
├── backend/             # Бэкенд (те же модули)
├── frontend/            # Фронтенд (shadcn/ui + Tailwind)
│   ├── tests/           # Playwright E2E тесты (обязательно!)
│   └── src/
│       └── components/
│           └── ErrorReportButton.tsx  # Кнопка жалобы на дизайн
├── opencode.json        # Stage 2 (конфиг с MCP)
├── AGENTS.md            # Stage 1 (обновленный)
└── docker-compose.yml
```

---

## Использование

Этот план передается OpenCode как инструкция:
```
@layer-architect выполни план из docs/specs/migration-plan.md
```

Или поэтапно:
1. `@layer-architect` — Этапы 1-2 (подготовка, Stage 1-2)
2. `@ui-migration` — Этапы 3-4 (Stage 3-4, миграция UI)
3. `@ui-migration` — Этап 4.5-4.7 (Playwright + функционал жалобы)
4. `@layer-architect` — Этапы 5-7 (Stage 5, финализация)

---

## Этап 8: Автономная миграция (Kiro Workflow, опционально)

### Что это
Автономный режим работы (Kiro Autopilot / OpenCode Agent Teams), когда агент сам планирует, делает, тестирует и отправляет на ревью, почти не требуя участия человека.

### Последовательность (200 строк лимит на задачу)

#### 8.1 Настройка Kiro Provider (опционально)
Если используете Kiro (AWS CodeWhisperer) вместо Anthropic:
```json
// ~/.config/opencode/opencode.json
{
  "provider": {
    "kiro": {
      "npm": "kiro-ai-provider",
      "name": "Kiro",
      "models": {
        "claude-sonnet-4-6": {
          "name": "Claude Sonnet 4.6",
          "attachment": true, "reasoning": true, "tool_call": true,
          "limit": { "context": 1000000, "output": 64000 }
        },
        "claude-opus-4-6": {
          "name": "Claude Opus 4.6",
          "attachment": true, "reasoning": true, "tool_call": true,
          "limit": { "context": 1000000, "output": 64000 }
        },
        "auto": {
          "name": "Kiro Auto",
          "attachment": true, "reasoning": true, "tool_call": true,
          "limit": { "context": 1000000, "output": 64000 }
        }
      }
    }
  }
}
```

Аутентификация:
```bash
# Вариант A: API Key
# Создать ~/.aws/sso/cache/kiro-auth-token.json
{
  "accessToken": "ksk-YOUR_API_KEY_HERE",
  "refreshToken": "",
  "expiresAt": "2027-01-01T00:00:00.000Z",
  "region": "us-east-1"
}

# Вариант B: OAuth (Builder ID / IAM Identity Center)
opencode auth login --provider kiro
```

#### 8.2 Автономный план миграции (через `/workflow` или агента)

Создать задачу для автономного выполнения (лимит 200 строк на дифф):

```markdown
## Autonomous Migration Task

**Task**: Migrate DocForge from Ant Design to shadcn/ui + Tailwind, implement Playwright E2E tests, add bug-report button.

**Constraints**:
- Max 200 lines per file change
- No new dependencies without approval
- TDD mode: write Playwright tests FIRST (RED), then implement (GREEN)
- Max 3 fix attempts before stopping

**Steps** (auto-scheduled by orchestrator):
1. `@ui-migration` → Replace antd with shadcn/ui (button, input, card)
2. `@test` → Write RED Playwright tests for homepage navigation
3. `@ui-migration` → Implement components to pass tests (GREEN)
4. `@ui-migration` → Replace complex components (Table, Modal, Select)
5. `@test` → Write RED tests for template designer
6. `@ui-migration` → Implement to pass (GREEN)
7. `@ui-migration` → Add ErrorReportButton with element selector
8. `@test` → Write RED test for bug-report flow
9. `@ui-migration` → Implement bug-report (GREEN)
10. `@review` (parallel: `@check` + `@simplify`) → Final review

**Routing**:
- UI changes → `@ui-migration` (with Context7 + Playwright MCP)
- Test writing → `@test` (Playwright E2E)
- Review → `@check` (code quality) + `@simplify` (design compliance)
```

#### 8.3 Запуск автономного workflow

```bash
# Вариант A: Через /workflow (если доступно)
/workflow DOC-123  # ID задачи в трекере

# Вариант B: Через основного агента в режиме Autopilot
# Переключить в Kiro Autopilot mode (или build агент с полными правами)
@layer-architect выполни автономную миграцию по плану из docs/specs/migration-plan.md Этап 8.2

# Агент будет:
# 1. Планировать (по 200 строк)
# 2. Писать тесты (RED)
# 3. Реализовывать (GREEN)
# 4. Отправлять на ревью
# 5. Создавать PR после прохождения всех проверок
```

#### 8.4 Ограничения и безопасность

- **Итерации**: максимум 3 попытки исправления на задачу
- **Контекст**: максимум 200 строк диффа на один файл
- **Тесты**: обязательный TDD (RED → GREEN)
- **Review**: перед финальным PR обязательны `@check` + `@simplify` в параллели
- **Human gate**: критические изменения (бэкенд, безопасность) требуют ручного approve

#### 8.5 Структура агентов для автономной миграции

```
OpenCode Agent Teams:
├── Orchestrator (main agent, build mode)
│   ├── Plans tasks (200-line chunks)
│   ├── Dispatches to specialists
│   └── Monitors iterations (max 3)
│
├── @ui-migration (specialist)
│   ├── Tools: read, write, edit, bash
│   ├── MCP: context7, playwright
│   └── Task: shadcn/ui migration
│
├── @test (specialist)
│   ├── Tools: read, write, edit, bash
│   ├── MCP: playwright
│   └── Task: Write RED Playwright tests first
│
├── @check (reviewer)
│   ├── Tools: read (no write/edit)
│   └── Task: Code quality, patterns, maintainability
│
├── @simplify (reviewer)
│   ├── Tools: read (no write/edit)
│   └── Task: Design compliance with Claude Design
│
└── @layer-architect (supervisor)
    ├── Tools: read, grep, glob
    └── Task: Ensure layer/contract compliance
```

### Как это вписать в Operating Model

Добавить в `docs/specs/workflows.md`:

```markdown
## Автономная разработка (Kiro-style)

### Когда использовать
- Задача затрагивает 3+ файлов
- Работа с UI миграцией (Ant Design → shadcn/ui)
- Нужно покрыть Playwright E2E тестами
- Cross-cutting concerns (фронтенд + бэкенд API)

### Процесс
1. `/workflow` или `@layer-architect` в build mode
2. Планирование (автоматическое разбиение на 200-строчные задачи)
3. TDD: `@test` пишет RED тесты → `@ui-migration` реализует GREEN
4. Параллельный ревью: `@check` + `@simplify`
5. Автоматический PR после прохождения всех проверок

### Лимиты
- Максимум 3 попытки исправления
- 200 строк максимум на один diff
- Human approve для критических изменений
```

---

## Этап 9: Агенты текущего проекта и TaskTracker MCP

### 9.1 Агенты текущего проекта (DocForge)

Изучение существующих агентов в `/home/nkamaev/Projects/DocForge`:

#### Найденные агенты:
1. **`layer-architect`** (`.opencode/agents/layer-architect.md`)
   - Роль: Анализ слоев и контрактов DocForge
   - Инструменты: read, grep, glob, bash, edit, write
   - Права: чтение всех слоев, проверка нарушений
   - Особенности: работает с `CLAUDE.md` (не `AGENTS.md` — нужно мигрировать)

#### Slash-команды (`.claude/commands/agents.md`):
   - `/architect` — архитектурное планирование
   - `/backend` — разработка бэкенда (Java 21, Spring Boot)
   - `/frontend` — разработка фронтенда (React 18, Ant Design → shadcn/ui)
   - `/tiptap` — работа с TipTap редактором
   - `/test` — написание тестов (JUnit, Vitest)
   - `/review` — код-ревью
   - `/devops` — Docker, docker-compose
   - `/migrate` — миграция данных/схем
   - `/api` — работа с REST API
   - `/debug` — отладка

#### План миграции агентов:
1. Переименовать `CLAUDE.md` → `AGENTS.md` (или создать симлинк)
2. Скопировать `.opencode/agents/layer-architect.md` в `DocForge-Next/.opencode/agents/`
3. Конвертировать slash-команды из `.claude/commands/` в агентов `.opencode/agents/`:
   - `architect.md`, `backend.md`, `frontend.md`, `tiptap.md`, `test.md`, `review.md`
4. Добавить MCP инструменты (Context7, Playwright) в конфиг агентов

---

### 9.2 TaskTracker MCP — Система отслеживания задач

#### Что это
TaskTracker MCP — сервер для управления задачами через Model Context Protocol. Интегрируется с Linear, TrackingTime.

#### Основные инструменты:
- `create_task_file` — создание файла задач проекта
- `add_task` — добавление задач с описанием и подзадачами
- `update_task_status` — обновление статуса задач
- `get_next_task` — получение следующей невыполненной задачи
- `parse_prd` — конвертация PRD в структурированные задачи
- `expand_task` — разбиение задач на подзадачи
- `estimate_task_complexity` — оценка сложности задач

#### Установка и настройка (самописный JAR):

Ваш сервер уже собран и находится по пути:
```
/home/nkamaev/.tasktracker/bin/task-tracker-mcp.jar
```

Настройка переменных окружения (согласно вашему MCP конфигу):
```bash
# Базовый URL сервера задач
TASKTRACKER_BASE_URL=http://localhost:8765/api/v1

# Актор для записи (идентификатор агента)
TASKTRACKER_ACTOR=mcp:claude-code
```

Добавление в `opencode.json` (уже выполнено):
```json
{
  "mcp": {
    "task-tracker": {
      "type": "local",
      "command": "java",
      "args": ["-jar", "/home/nkamaev/.tasktracker/bin/task-tracker-mcp.jar"],
      "env": {
        "TASKTRACKER_BASE_URL": "http://localhost:8765/api/v1",
        "TASKTRACKER_ACTOR": "mcp:claude-code"
      },
      "description": "TaskTracker MCP - self-hosted Java task tracking"
    }
  },
  "agent": {
    "layer-architect": {
      "mcp": { "task-tracker": "allow", "context7": "allow" }
    },
    "ui-migration": {
      "mcp": { "task-tracker": "allow", "context7": "allow", "playwright": "allow" }
    }
  }
}
```

#### Использование в миграции:

1. **Создание задач миграции**:
```
@layer-architect создай задачи миграции в TaskTracker на основе docs/specs/migration-plan.md
```

2. **Автоматическое разбиение**:
```
@ui-migration разбей задачу "Миграция UI" на подзадачи через task-tracker
```

3. **Отслеживание прогресса**:
```bash
# Получить следующую задачу
npx -y @upstash/context7-mcp  # (для документации)
uv run task-tracker  # (для управления задачами)

# Внутри OpenCode:
await mcp.get_next_task(project_name="DocForge-Next")
```

4. **Обновление статуса**:
```typescript
// Агент автоматически обновляет статус
await mcp.update_task_status({
  project_name: "DocForge-Next",
  task_title: "Замена Ant Design на shadcn/ui",
  subtask_title: "Button компоненты",
  status: "done"
})
```

#### Интеграция с рабочим процессом:

```markdown
## Обновленный процесс разработки (с TaskTracker)

1. **Планирование** (TaskTracker):
   - `/architect` создает задачи в TaskTracker
   - `parse_prd` конвертирует требования в задачи

2. **Выполнение** (автоматическое):
   - `@ui-migration` берет следующую задачу через `get_next_task`
   - Выполняет задачу (лимит 200 строк)
   - Обновляет статус через `update_task_status`

3. **Тестирование**:
   - `@test` пишет Playwright тесты (RED)
   - `@ui-migration` реализует (GREEN)

4. **Ревью**:
   - `@check` + `@simplify` проверяют код
   - Обновляют статус задачи в TaskTracker
```

#### Структура данных (локальное хранение):
```
~/.tasktracker/
├── tasks.json      # Задачи проекта
├── plan.json       # План миграции
└── notes.json      # Заметки к задачам
```

---

## Итоговое резюме

План миграции DocForge → DocForge-Next покрывает:
1. **Stage 1-5** методологии OpenCode (проектный контракт, инфраструктура, агенты, скиллы, operating model)
2. **UI миграция** Ant Design → shadcn/ui + Tailwind CSS
3. **Обязательные Playwright E2E тесты** для фронтенда
4. **Функционал «Сообщить об ошибке»** с выбором элемента (как в Claude Design)
5. **MCP интеграция** (Context7 для документации, Playwright для E2E + element select, TaskTracker для задач)
6. **Автономная миграция** (Kiro Autopilot / OpenCode Agent Teams) с лимитом 200 строк на задачу
7. **Миграция агентов** из текущего проекта (slash-команды → .opencode/agents/)
8. **TaskTracker MCP** — отслеживание задач миграции с интеграцией Linear

План сохранен в `docs/specs/migration-plan.md` и готов к использованию:
```
@layer-architect выполни план из docs/specs/migration-plan.md
```

### Быстрый старт с TaskTracker:
```bash
# 1. Установка
git clone https://github.com/reminia/task-tracker.git
cd task-tracker && uv build

# 2. Настройка в opencode.json (см. раздел 9.2)

# 3. Создание задач миграции
@layer-architect создай задачи в TaskTracker на основе migration-plan.md

# 4. Автоматическое выполнение
@ui-migration выполни следующую задачу из TaskTracker
```
