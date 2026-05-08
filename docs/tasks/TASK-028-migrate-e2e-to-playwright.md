---
id: TASK-028
title: Миграция E2E-тестов с Selenium (Java) на Playwright (TypeScript)
type: refactor
status: TODO
agents: /frontend, /selenium
created: 2026-04-18
gh_issue: 49
---

## Постановка

Проект использует Selenium WebDriver (Java) для E2E-тестов в модуле `/e2e`. После добавления Playwright MCP появилась возможность запускать браузерные тесты через `@playwright/mcp` прямо из Claude Code. Необходимо перенести существующие тесты на Playwright (TypeScript), разместив их в `frontend/e2e/`, и удалить модуль Java-e2e.

## Мотивация

| Аспект | Selenium (текущий) | Playwright (цель) |
|--------|--------------------|--------------------|
| Язык | Java | TypeScript (единая кодовая база с frontend) |
| Скорость | Медленный, ChromeDriver/GeckoDriver зависимости | Быстрый, браузеры встроены |
| Отладка агентами | Bash `./gradlew test` | MCP `@playwright/mcp` + `npx playwright test` |
| Автоожидание | Явные `WebDriverWait` | Встроенное auto-wait |
| Параллелизм | Ручной | Встроенный |
| Трассировки | Нет | Trace Viewer, скриншоты, видео |
| CI | Сложная настройка ChromeDriver | `playwright install --with-deps` |

## Файлы для изучения

- `e2e/src/test/java/com/docforge/e2e/tests/` — 4 тестовых класса (AuthTest, TemplateListTest, TemplateDesignerTest, FormattingTest)
- `e2e/src/test/java/com/docforge/e2e/base/` — BaseTest, ApiClient, AppConfig
- `e2e/src/test/java/com/docforge/e2e/pages/` — Page Objects (LoginPage, TemplateListPage, TemplateDesignerPage)
- `e2e/build.gradle.kts` — зависимости Selenium модуля
- `frontend/package.json` — для добавления Playwright

## Покрываемые сценарии (перенести всё)

### AuthTest (~129 строк)
- TC-A01: Успешный логин → редирект, хедер показывает displayName
- TC-A02: Неверный пароль → toast с ошибкой, остаться на /login
- TC-A03: Logout → редирект на /login, защищённый маршрут требует повторного входа

### TemplateListTest (~135 строк)
- TC-TL01: Список шаблонов отображается
- TC-TL02: Создание нового шаблона
- TC-TL03: Удаление шаблона (с подтверждением)

### TemplateDesignerTest (~158 строк)
- TC-TD01: Добавление параметра в шаблон
- TC-TD02: Публикация шаблона

### FormattingTest (~162 строк)
- TC-FT01: Применение Bold и Italic в редакторе
- TC-FT02: Проверка форматирования сохраняется

## Этапы выполнения

### Этап 1: Инициализация Playwright в frontend
- [ ] `cd frontend && npx playwright install --with-deps chromium`
- [ ] Создать `frontend/playwright.config.ts` (baseURL: http://localhost:5173, timeouts, трассировки)
- [ ] Добавить в `frontend/package.json`: `"test:e2e": "playwright test"`
- [ ] Создать `frontend/e2e/` директорию

### Этап 2: Вспомогательный слой (аналог base/)
- [ ] `frontend/e2e/helpers/api-client.ts` — REST-клиент для регистрации тестовых пользователей (аналог ApiClient.java)
- [ ] `frontend/e2e/helpers/auth.ts` — глобальный setup: создать test-user через API, сохранить storageState
- [ ] `frontend/playwright.config.ts` — globalSetup для auth

### Этап 3: Page Objects
- [ ] `frontend/e2e/pages/LoginPage.ts`
- [ ] `frontend/e2e/pages/TemplateListPage.ts`
- [ ] `frontend/e2e/pages/TemplateDesignerPage.ts`

### Этап 4: Тестовые файлы
- [ ] `frontend/e2e/tests/auth.spec.ts` — TC-A01, TC-A02, TC-A03
- [ ] `frontend/e2e/tests/template-list.spec.ts` — TC-TL01..TL03
- [ ] `frontend/e2e/tests/template-designer.spec.ts` — TC-TD01, TC-TD02
- [ ] `frontend/e2e/tests/formatting.spec.ts` — TC-FT01, TC-FT02

### Этап 5: Удаление Selenium-модуля
- [ ] Удалить директорию `e2e/`
- [ ] Убрать включение модуля из корневого `settings.gradle.kts` (если есть)
- [ ] Обновить `docs/architecture.md` — убрать упоминание Selenium

### Этап 6: Интеграция с MCP
- [ ] Проверить работу через `@playwright/mcp` из Claude Code (команда `/selenium` → обновить на Playwright)
- [ ] Обновить skill `/selenium` → `/playwright` в docs/agents.md

## Технические заметки

**Авторизация:** Playwright рекомендует `storageState` для переиспользования сессии. Один раз логинимся в `globalSetup`, сохраняем cookies — все тесты стартуют уже авторизованными (быстрее Selenium).

**TipTap в тестах:** `page.locator('.ProseMirror')` для редактора, `page.keyboard.type()` для ввода текста. Форматирование через `page.keyboard.press('Control+b')`.

**Параллелизм:** Playwright запускает тесты параллельно по умолчанию. Тесты с общим пользователем должны быть в одном `test.describe` с `test.use({ storageState })`.

## Тесты

После миграции выполнить:
```bash
cd frontend && npx playwright test --reporter=list
```
Все 10+ тест-кейсов должны пройти в headless-режиме.

## Ручной чеклист

- [ ] `npx playwright test` завершается без ошибок
- [ ] Trace Viewer показывает корректные шаги для TC-A01
- [ ] Модуль `e2e/` удалён, проект компилируется без него

## Чеклист завершения

- [ ] Playwright установлен и настроен в `frontend/`
- [ ] Все 4 набора тестов перенесены (AuthTest, TemplateList, TemplateDesigner, Formatting)
- [ ] Selenium-модуль `e2e/` удалён
- [ ] Git-коммит создан: `refactor(e2e): migrate from Selenium to Playwright TypeScript`
- [ ] Статус обновлён: DONE
- [ ] Реестр обновлён: docs/tasks/README.md

## История

| Дата | Автор | Изменение |
|---|---|---|
| 2026-04-18 | nkamaev | Создана задача |
