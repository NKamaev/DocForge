---
id: TASK-013
title: Исследование полезных MCP-серверов для проекта
type: research
status: DONE
agents: /architect
created: 2026-04-18
gh_issue: 13
---

## Постановка

Изучить, какие MCP-серверы (Model Context Protocol) могут быть полезны для разработки DocForge: ускорить работу агентов, повысить качество кода, сократить расход токенов.

## Вопросы для исследования

### Интеграция с инструментами проекта
1. Есть ли MCP-сервер для MongoDB (чтение схем, выполнение запросов)?
2. Есть ли MCP-сервер для PostgreSQL (просмотр схем, explain-планы)?
3. Есть ли MCP-сервер для Gradle (запуск задач, просмотр зависимостей)?
4. Есть ли MCP-сервер для GitHub (просмотр PR, issues, code review)?

### Качество кода
5. MCP для статического анализа (ESLint, Checkstyle результаты)?
6. MCP для покрытия тестами (JaCoCo, Vitest coverage)?
7. MCP для поиска по документации (Spring Boot, TipTap, Yjs, Ant Design)?

### Экономия токенов и эффективность
8. Какие MCP-серверы позволяют избежать чтения больших файлов целиком?
9. MCP-серверы с кешированием результатов поиска?
10. Есть ли MCP для Playwright/Puppeteer — автоматизация браузерного тестирования?

### Специфика DocForge
11. MCP для работы с DOCX/PDF (Apache POI, iText)?
12. MCP для мониторинга WebSocket-соединений?
13. MCP для управления Docker Compose (статус контейнеров, логи)?

## Источники для изучения

- [MCP Servers Registry](https://github.com/modelcontextprotocol/servers)
- [Anthropic MCP docs](https://modelcontextprotocol.io/)
- [Claude Code MCP integration](https://docs.anthropic.com/claude/docs/mcp)
- Поиск по `mcp server postgresql`, `mcp server mongodb`, `mcp server github` и т.д.

## Критерии оценки каждого сервера

- **Полезность**: насколько часто нужен в текущих задачах DocForge
- **Экономия токенов**: позволяет ли избежать дорогих операций чтения/поиска
- **Надёжность**: звёзды на GitHub, активная поддержка, актуальная версия
- **Безопасность**: не передаёт данные на внешние серверы без необходимости
- **Сложность установки**: npm/uvx install vs компиляция

## Выводы

### Оценка по каждому серверу

| Сервер | Статус | Stars | Приоритет | Обоснование |
|--------|--------|-------|-----------|-------------|
| **MongoDB** | Активен (v1.9.0, март 2026) | 1K+ | ✅ ВЫСОКИЙ | Прямая поддержка MongoDB, официально от MongoDB |
| **GitHub** | Активен | 29K | ✅ ВЫСОКИЙ | Управление PR/issues, официальный от GitHub |
| **Playwright** | Активен | 31K | ⭐ СРЕДНИЙ | Современное E2E-тестирование, эффективнее Selenium |
| **PostgreSQL** | Устарел (архив июль 2025) | 74K | ⚠️ НЕТ | Заархивирован — использовать community-версии при необходимости |
| **Docker** | Community | — | ❌ НИЗКИЙ | Риск docker.sock, текущего dev.sh достаточно |
| **Filesystem** | Официальный | — | ❌ НИЗКИЙ | Дублирует встроенные инструменты Claude Code |
| **ESLint** | Официальный | — | ❌ НИЗКИЙ | Дублирует `npm run lint` через Bash |
| **Redis** | Активен | 485 | ⭐ ОПЦИОН. | Полезен при активной отладке Redis-кеша |
| **Brave Search** | Официальный | — | ❌ НИЗКИЙ | Дублирует встроенный WebSearch |

**По вопросам исследования:**
- Gradle MCP — не найден актуальный сервер; `./gradlew` через Bash достаточно
- Checkstyle/JaCoCo MCP — не существуют; запускать через Bash
- DOCX/PDF MCP — не существуют; Apache POI используется напрямую в коде
- WebSocket мониторинг MCP — не существует; используем логи приложения
- Spring Boot / Java docs MCP — не для потребления, Spring AI для создания MCP-серверов

### Рекомендуемые к установке (высокий приоритет)

#### 1. MongoDB MCP Server (`mongodb-mcp-server`)
- **Версия:** v1.9.0 (март 2026), поддерживается MongoDB
- **Установка:** `npx mongodb-mcp-server@latest`
- **Плюсы:** Прямые запросы к коллекциям templates/documents, агрегации на естественном языке, read-only режим, подтверждение для деструктивных операций
- **Токены:** Экономит на ручном чтении схем и данных

#### 2. GitHub MCP Server (`github/github-mcp-server`)
- **Версия:** Активно поддерживается GitHub, 29K stars
- **Установка:** `npx @modelcontextprotocol/server-github`
- **Плюсы:** Управление PR, issues, CI/CD, code search без переключения контекста
- **Токены:** Экономит на ручной навигации по GitHub

### Рекомендуемые к изучению (средний приоритет)

#### 3. Playwright MCP Server (`@playwright/mcp`)
- **Версия:** v31K stars, Microsoft, активно развивается
- **Установка:** `npx @playwright/mcp@latest`
- **Плюсы:** Браузерная автоматизация через accessibility tree (без скриншотов = экономия токенов), кроссбраузерность
- **Когда использовать:** Если проект перейдёт с Selenium на Playwright для E2E-тестов

### Отклонённые (с обоснованием)

- **PostgreSQL (@modelcontextprotocol/server-postgres)** — заархивирован, community-альтернативы не устоялись
- **Docker MCP** — требует docker.sock, значительный риск безопасности, dev.sh достаточно
- **Filesystem MCP** — полностью дублирует Read/Glob/Grep инструменты Claude Code
- **ESLint MCP** — дублирует `npm run lint` через Bash
- **Brave Search MCP** — дублирует встроенный WebSearch

### Инструкции по установке рекомендованных серверов

Добавить в `.claude/settings.json` секцию `mcpServers`:

```json
{
  "mcpServers": {
    "mongodb": {
      "command": "npx",
      "args": ["-y", "mongodb-mcp-server@latest", "--readOnly"],
      "env": {
        "MDB_MCP_CONNECTION_STRING": "mongodb://localhost:27017/docforge"
      }
    },
    "github": {
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-github"],
      "env": {
        "GITHUB_PERSONAL_ACCESS_TOKEN": "<ваш PAT с repo + workflow scopes>"
      }
    }
  }
}
```

**Требования:**
- Node.js 20.19.0+ для MongoDB MCP
- GitHub PAT: сгенерировать на https://github.com/settings/tokens с правами `repo`, `workflow`
- После изменения `.claude/settings.json` перезапустить Claude Code

**Playwright (опционально, только при переходе на Playwright E2E):**
```json
"playwright": {
  "command": "npx",
  "args": ["-y", "@playwright/mcp@latest"]
}
```

## Чеклист завершения

- [x] Изучено не менее 10 MCP-серверов
- [x] Каждый оценён по критериям выше
- [x] Сформирован короткий список (топ-3) для установки
- [x] Для топ-3 написаны инструкции по установке в `.claude/settings.json`
- [x] Git-коммит создан: `docs(research): MCP servers research findings`
- [x] Статус обновлён: DONE
- [x] Реестр обновлён: docs/tasks/README.md

## История

| Дата | Автор | Изменение |
|---|---|---|
| 2026-04-18 | nkamaev | Создана задача |
| 2026-04-18 | claude | Исследование проведено, выводы заполнены |
