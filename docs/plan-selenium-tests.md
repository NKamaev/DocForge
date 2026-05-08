# Plan: Selenium E2E тесты для DocForge

## 1. Цель и принципы

Selenium E2E тесты проверяют ключевые пользовательские сценарии сквозь весь стек —
браузер → frontend → backend → БД. Они не заменяют unit/integration тесты, а дополняют их.

**Принципы:**
- Тесты пишутся в отдельном Gradle-модуле `e2e/` (не загрязняют backend).
- Page Object Model (POM) — каждый экран = отдельный Page Object класс.
- Тесты независимы: каждый тест создаёт свои данные, не зависит от порядка запуска.
- CI-ready: запускаются в Docker Compose окружении, headless Chrome.

---

## 2. Технологический стек

| Компонент | Технология |
|-----------|-----------|
| WebDriver | Selenium WebDriver 4.x |
| Браузер | ChromeDriver (headless в CI) |
| Тест-фреймворк | JUnit 5 |
| Управление браузером | WebDriverManager (автоскачивание chromedriver) |
| Сборка | Gradle (отдельный модуль `e2e/`) |
| CI | Запуск через `./gradlew :e2e:test` после `docker compose up` |
| Assertions | AssertJ |
| Ожидания | WebDriverWait + ExpectedConditions (no Thread.sleep) |

---

## 3. Структура модуля

```
e2e/
├── build.gradle.kts
└── src/
    └── test/
        └── java/
            └── com/docforge/e2e/
                ├── base/
                │   ├── BaseTest.java          # WebDriver lifecycle, helpers
                │   └── AppConfig.java         # BASE_URL, credentials из env
                ├── pages/
                │   ├── LoginPage.java
                │   ├── TemplateListPage.java
                │   ├── TemplateDesignerPage.java
                │   ├── DocumentWorkspacePage.java
                │   └── DirectoryPage.java
                └── tests/
                    ├── AuthTest.java          # P0
                    ├── TemplateListTest.java  # P0
                    ├── TemplateDesignerTest.java # P0
                    ├── DocumentWorkspaceTest.java # P0
                    ├── ExportTest.java        # P1
                    ├── DirectoryTest.java     # P1
                    └── FormattingTest.java    # P2
```

---

## 4. Page Objects

### LoginPage
- `fillLogin(String login)`
- `fillPassword(String password)`
- `submit()`
- `assertError(String message)`

### TemplateListPage
- `waitForLoad()`
- `clickCreate()`
- `openTemplate(String name)`
- `assertTemplateExists(String name)`
- `deleteTemplate(String name)`

### TemplateDesignerPage
- `waitForEditorReady()`
- `typeInEditor(String text)`
- `addParameter(String code, String label, String type)`
- `insertParameter(String code)`
- `addConditionalBlock(String label)`
- `setLineSpacing(String value)`
- `setFontFamily(String font)`
- `setFontSize(String size)`
- `saveTemplate()`
- `assertSaved()`
- `publishTemplate()`

### DocumentWorkspacePage
- `waitForLoad()`
- `fillTextField(String paramCode, String value)`
- `fillDateField(String paramCode, String value)`
- `selectOption(String paramCode, String value)`
- `toggleBoolean(String paramCode, boolean value)`
- `assertParameterInDocument(String paramCode, String expectedValue)`
- `clickParameterInDocument(String paramCode)` // → фокус на вопрос в ОЛ
- `exportDocx()`
- `exportPdf()`

### DirectoryPage
- `createDirectory(String name)`
- `addField(String key, String type)`
- `addEntry(Map<String, String> fields)`
- `assertEntryExists(Map<String, String> fields)`

---

## 5. Тест-сценарии

### P0 — Критический путь (блокирует релиз)

#### AuthTest
```
TC-A01: Успешный вход
  Given: Пользователь на /login
  When: Вводит валидный login/password, нажимает «Войти»
  Then: Редирект на /templates, header показывает имя пользователя

TC-A02: Неверный пароль
  Given: Пользователь на /login
  When: Вводит неверный пароль
  Then: Сообщение об ошибке, остаётся на /login

TC-A03: Выход из системы
  Given: Авторизованный пользователь
  When: Нажимает «Выйти»
  Then: Редирект на /login, недоступен /templates без auth
```

#### TemplateListTest
```
TC-TL01: Отображение списка шаблонов
  Given: Авторизован, есть шаблоны в БД
  When: Открывает /templates
  Then: Список шаблонов отображается, у каждого есть название и кнопки

TC-TL02: Создание нового шаблона
  When: Нажимает «Создать», вводит название, подтверждает
  Then: Редирект в конструктор, шаблон есть в списке

TC-TL03: Удаление шаблона
  Given: Шаблон существует
  When: Нажимает «Удалить», подтверждает
  Then: Шаблон исчез из списка
```

#### TemplateDesignerTest
```
TC-TD01: Набор текста и сохранение
  Given: Открыт конструктор нового шаблона
  When: Набирает текст «Договор №{{contract_num}}», сохраняет
  Then: Перезагрузка страницы сохраняет текст

TC-TD02: Вставка параметрического поля
  Given: Конструктор открыт
  When: Добавляет параметр (code=client_name, type=TEXT),
        вставляет его в текст через тулбар → «Параметр»
  Then: В тексте появляется синяя плашка с именем параметра

TC-TD03: Тулбар — шрифт и размер при новом параграфе
  Given: Конструктор открыт, задан шрифт «Times New Roman», размер 14
  When: Нажимает Enter (новый параграф)
  Then: Тулбар показывает «Times New Roman» и «14» (регрессия: баг с пустым тулбаром)

TC-TD04: Условный блок
  When: Вставляет «Условие», задаёт label=«Блок со штрафом»
  Then: В документе виден блок с лейблом

TC-TD05: Публикация шаблона
  Given: Шаблон сохранён
  When: Нажимает «Опубликовать»
  Then: Бейдж «Опубликовано» появляется в тулбаре
```

#### DocumentWorkspaceTest
```
TC-DW01: Открытие workspace
  Given: Опубликованный шаблон с параметром client_name (TEXT)
  When: Нажимает «Заполнить»
  Then: Открывается split-panel: слева ОЛ, справа документ

TC-DW02: Заполнение текстового параметра
  When: В ОЛ вводит «Иванов Иван» в поле client_name
  Then: В документе {{client_name}} заменяется на «Иванов Иван» без перезагрузки

TC-DW03: Двусторонняя синхронизация
  When: Кликает на плашку {{client_name}} в документе
  Then: Фокус переходит на поле client_name в ОЛ (подсветка)

TC-DW04: Параметр типа SELECT
  Given: Параметр contract_type с вариантами [Купля-продажа, Аренда]
  When: Выбирает «Аренда» в ОЛ
  Then: В документе отображается «Аренда»

TC-DW05: Параметр типа DATE
  When: Вводит дату 15.04.2026
  Then: В документе отображается 15.04.2026 (или форматированная строка)
```

---

### P1 — Важные сценарии

#### ExportTest
```
TC-EX01: Экспорт DOCX
  Given: Заполненный документ
  When: Нажимает «Экспорт DOCX»
  Then: Файл .docx скачивается (Content-Disposition: attachment)

TC-EX02: Экспорт PDF
  When: Нажимает «Экспорт PDF»
  Then: Файл .pdf скачивается
```

#### DirectoryTest
```
TC-DIR01: Создание справочника
  When: Открывает /directories, создаёт «Контрагенты» с полями [ИНН, Название]
  Then: Справочник появляется в списке

TC-DIR02: Добавление записи
  When: Открывает справочник, добавляет запись {ИНН: «7700000000», Название: «ООО Тест»}
  Then: Запись видна в таблице

TC-DIR03: Параметр типа DIRECTORY в шаблоне
  Given: Справочник «Контрагенты» с полями [Название, ИНН]
  When: В workspace выбирает запись из справочника
  Then: Поля contractor__Название, contractor__ИНН заполняются автоматически
```

---

### P2 — Дополнительные сценарии

#### FormattingTest
```
TC-FMT01: Жирный/курсив/подчёркнутый
TC-FMT02: Выравнивание текста
TC-FMT03: Нумерованные и маркированные списки
TC-FMT04: Таблица: вставка, редактирование ячеек
TC-FMT05: Разрыв страницы
```

---

## 6. Инфраструктура запуска

### Локально
```bash
# 1. Поднять инфраструктуру и приложение
docker compose up -d
cd backend && ./gradlew bootRun &
cd frontend && npm run dev &

# 2. Запустить E2E тесты
cd e2e && ./gradlew test -Dapp.base-url=http://localhost:5173
```

### CI (GitHub Actions)
```yaml
- name: Start services
  run: docker compose up -d

- name: Build and start app
  run: |
    cd backend && ./gradlew bootRun &
    cd frontend && npm run build && npx serve dist -p 5173 &
    # wait for health

- name: Run E2E tests
  run: ./gradlew :e2e:test
  env:
    APP_BASE_URL: http://localhost:5173
    APP_ADMIN_LOGIN: admin
    APP_ADMIN_PASSWORD: ${{ secrets.TEST_ADMIN_PASSWORD }}
```

---

## 7. Конфигурация BaseTest

```java
// Ключевые решения:
// - @BeforeAll: создать WebDriver (ChromeOptions headless)
// - @AfterAll: quit WebDriver
// - WebDriverWait: defaultWait = 10 сек
// - BASE_URL из env APP_BASE_URL, дефолт http://localhost:5173
// - Хелпер loginAs(login, password) — вызывается в @BeforeEach тестов
```

---

## 8. Приоритет реализации

1. `e2e/build.gradle.kts` + `BaseTest` + `LoginPage` + `AuthTest` (P0)
2. `TemplateListPage` + `TemplateListTest` (P0)
3. `TemplateDesignerPage` + `TemplateDesignerTest` (P0) — включая TC-TD03 (баг-регрессия тулбара)
4. `DocumentWorkspacePage` + `DocumentWorkspaceTest` (P0)
5. `ExportTest` + `DirectoryTest` (P1)
6. `FormattingTest` (P2)
