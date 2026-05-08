# План улучшения редактора шаблонов

> **Статус:** Выполнено  
> **Создан:** 2026-04-09  
> **Завершён:** 2026-04-10  
> **Стек:** TipTap 3 + ProseMirror + React 18 + TypeScript + Spring Boot 3.3

---

## Структура плана

Все работы разбиты на 10 этапов. Этапы 1–3 независимы и могут выполняться параллельно. Этапы 4–9 зависят от результатов предыдущих (общая инфраструктура). Этап 10 — финальная документация.

---

## Этап 1. Базовое форматирование текста

**Агент:** `/frontend`  
**Зависимости:** нет  
**Файлы:**
- `frontend/src/components/template-designer/TemplateDesigner.tsx` — добавить extensions
- `frontend/src/components/template-designer/EditorToolbar.tsx` — добавить кнопки
- `frontend/src/components/template-designer/TemplateDesigner.css` — стили для новых элементов

### Задачи

- [ ] Установить пакеты:
  ```
  @tiptap/extension-strike
  @tiptap/extension-subscript
  @tiptap/extension-superscript
  @tiptap/extension-color
  @tiptap/extension-highlight
  @tiptap/extension-font-family
  @tiptap/extension-font-size
  @tiptap/extension-text-style
  ```
- [ ] Подключить все extensions в `EXTENSIONS` массив `TemplateDesigner.tsx`
- [ ] В `EditorToolbar.tsx` добавить группу «Начертание»:
  - Зачёркнутый (Strike)
  - Надстрочный (Superscript)
  - Подстрочный (Subscript)
- [ ] В `EditorToolbar.tsx` добавить группу «Цвет»:
  - Выпадающий пикер цвета текста (ColorPicker Ant Design 5.5+)
  - Выпадающий пикер фонового цвета (Highlight)
- [ ] В `EditorToolbar.tsx` добавить:
  - Select «Шрифт» (список: Times New Roman, Arial, Calibri, Courier New)
  - Select «Размер» (список: 8, 9, 10, 11, 12, 14, 16, 18, 20, 24, 28, 36pt)
- [ ] В `EditorToolbar.tsx` добавить Select «Межстрочный интервал» (1.0 / 1.15 / 1.5 / 2.0)

---

## Этап 2. Выравнивание и отступы абзаца

**Агент:** `/frontend`  
**Зависимости:** нет  
**Файлы:**
- `frontend/src/components/template-designer/TemplateDesigner.tsx`
- `frontend/src/components/template-designer/EditorToolbar.tsx`

### Задачи

- [ ] Установить пакет `@tiptap/extension-text-align`
- [ ] Подключить `TextAlign.configure({ types: ['heading', 'paragraph', 'numberedClause'] })`
- [ ] В тулбаре добавить группу «Выравнивание»:
  - По левому / По центру / По правому / По ширине (иконки Ant Design: AlignLeftOutlined и т.д.)
- [ ] Реализовать команды отступа абзаца через кастомное расширение `Indent`:
  - `Tab` — увеличить отступ (margin-left + 2em)
  - `Shift+Tab` — уменьшить отступ
  - Кнопки в тулбаре: IndentOutlined / OutdentOutlined
- [ ] Реализовать «красную строку» (first-line indent) — кнопка в тулбаре, toggle `text-indent: 1.25cm`

---

## Этап 3. Настройки страницы

**Агент:** `/architect` → `/backend` → `/frontend`  
**Зависимости:** нет (параллельно с 1 и 2)

### Подэтап 3а — Архитектор: модель данных

**Агент:** `/architect`

- [ ] Расширить поле `formatting` в MongoDB-документе шаблона (уже существует как `template.formatting`):
  ```json
  {
    "formatting": {
      "pageFormat": "A4",
      "orientation": "portrait",
      "margins": {
        "top": 20,
        "bottom": 20,
        "left": 30,
        "right": 15
      },
      "defaultFont": "Times New Roman",
      "defaultFontSize": 14,
      "lineSpacing": 1.5
    }
  }
  ```
- [ ] Обновить Java-record `TemplateFormatting` в backend (если есть) или создать новый

### Подэтап 3б — Backend: сохранение formatting

**Агент:** `/backend`  
**Файлы:**
- `backend/src/main/java/com/docforge/template/` — TemplateDocument, TemplateRequest/Response

- [ ] Убедиться, что `TemplateUpdateRequest` принимает `formatting` объект
- [ ] Убедиться, что `TemplateResponse` возвращает `formatting`
- [ ] Добавить Java-record `PageFormatting` с полями: `pageFormat`, `orientation`, `margins` (record `Margins`), `defaultFont`, `defaultFontSize`, `lineSpacing`

### Подэтап 3в — Frontend: UI настроек страницы

**Агент:** `/frontend`  
**Файлы:**
- `frontend/src/components/template-designer/TemplateDesigner.tsx`
- `frontend/src/components/template-designer/TemplateDesigner.css`
- новый файл: `frontend/src/components/template-designer/PageSettingsDrawer.tsx`

- [ ] Создать компонент `PageSettingsDrawer` (Ant Design Drawer):
  - Select «Формат»: A4 / A3 / Letter
  - Radio «Ориентация»: Книжная / Альбомная
  - 4 InputNumber «Поля» (мм): верх / низ / лево / право
  - Select «Шрифт по умолчанию»
  - InputNumber «Размер шрифта по умолчанию»
  - Select «Межстрочный интервал»
- [ ] В `TemplateDesigner.tsx` добавить кнопку «Параметры страницы» (SettingOutlined) в шапку
- [ ] Применять `formatting` как CSS-переменные на `.tiptap-page`:
  ```css
  --page-width, --page-height, --margin-top, --margin-bottom, --margin-left, --margin-right
  ```
- [ ] При альбомной ориентации: ширина 297mm, высота 210mm
- [ ] Сохранять `formatting` вместе с шаблоном (уже проходит через существующий save)

---

## Этап 4. Работа с таблицами

**Агент:** `/frontend`  
**Зависимости:** желательно после Этапа 5 (контекстное меню — общая инфраструктура)  
**Файлы:**
- `frontend/src/components/template-designer/extensions/TableBubbleMenu.tsx` (новый)
- `frontend/src/components/template-designer/TemplateDesigner.tsx`

### Задачи

- [ ] Создать плавающую панель `TableBubbleMenu` (появляется при клике внутри таблицы):
  - Добавить строку выше (`addRowBefore`)
  - Добавить строку ниже (`addRowAfter`)
  - Удалить строку (`deleteRow`)
  - Добавить колонку слева (`addColumnBefore`)
  - Добавить колонку справа (`addColumnAfter`)
  - Удалить колонку (`deleteColumn`)
  - Объединить ячейки (`mergeCells`) — активно когда выделено несколько
  - Разбить ячейку (`splitCell`) — активно когда ячейка span > 1
  - Удалить таблицу (`deleteTable`)
- [ ] Подключить `TableBubbleMenu` в `TemplateDesigner.tsx`
- [ ] Добавить выравнивание содержимого ячейки (через `setCellAttribute`)
- [ ] Добавить цвет фона ячейки (через `setCellAttribute('backgroundColor', ...)`)

> Все команды уже реализованы в `@tiptap/extension-table` — нужен только UI.

---

## Этап 5. Контекстное меню (правый клик)

**Агент:** `/frontend`  
**Зависимости:** нет  
**Файлы:**
- новый файл: `frontend/src/components/template-designer/ContextMenu.tsx`
- `frontend/src/components/template-designer/TemplateDesigner.tsx`

### Задачи

- [ ] Создать кастомный ProseMirror plugin `contextMenuPlugin`, перехватывающий событие `contextmenu`
- [ ] Реализовать компонент `ContextMenu` (Ant Design Dropdown с `open` / `position`):
  - **На тексте:** Вырезать / Копировать / Вставить / --- / Жирный / Курсив / Подчёркнутый / Зачёркнутый / --- / Вставить параметр / Вставить ссылку
  - **На таблице / ячейке:** дублировать операции из TableBubbleMenu (этап 4)
  - **На нумерованном пункте:** Изменить уровень (1–5) / Изменить стиль нумерации / Начать новый список / --- / Вставить ссылку на пункт
  - **На параметре:** Перейти к параметру в боковой панели / Редактировать параметр
- [ ] Отключить браузерное контекстное меню через `e.preventDefault()` только внутри редактора
- [ ] Закрывать меню по клику вне / Escape

---

## Этап 6. Вставка дополнительных элементов

**Агент:** `/frontend` (ссылки, линия, разрыв страницы) + `/backend` (изображения)  
**Зависимости:** нет

### Подэтап 6а — Frontend: ссылки, линия, разрыв страницы

**Агент:** `/frontend`

- [ ] Установить `@tiptap/extension-link`
- [ ] В тулбаре — кнопка «Ссылка» (LinkOutlined): Modal с input URL, открывается в новой вкладке
- [ ] Установить `@tiptap/extension-horizontal-rule`
- [ ] В тулбаре — кнопка «Горизонтальная линия» (MinusOutlined)
- [ ] Реализовать кастомный Node `PageBreak`:
  - Отображается как горизонтальная пунктирная линия с надписью «Разрыв страницы»
  - В CSS `@media print` / в экспорте: `page-break-before: always`
- [ ] В тулбаре — кнопка «Разрыв страницы» (в группе «Вставить»)

### Подэтап 6б — Изображения

**Агент:** `/backend` → `/frontend`

- [ ] **Backend:** создать endpoint `POST /api/uploads/images` — принимает multipart, сохраняет в хранилище (локально или S3), возвращает URL
- [ ] **Backend:** создать endpoint `GET /api/uploads/images/{id}` — отдаёт файл
- [ ] **Frontend:** установить `@tiptap/extension-image`
- [ ] **Frontend:** в тулбаре — кнопка «Изображение» (PictureOutlined): Modal с drag-n-drop загрузкой или вставкой URL
- [ ] **Frontend:** при вставке — загружать файл на backend, вставлять URL в node `image`

### Подэтап 6в — Колонтитулы (отложено / сложно)

> Колонтитулы (верхний / нижний) и номер страницы — **вынести за рамки текущего плана**. TipTap не имеет страничной модели; эти элементы реализуются на уровне экспорта (DOCX/PDF). Реализовать в рамках Этапа 9 (Экспорт).

---

## Этап 7. Поиск и замена

**Агент:** `/frontend`  
**Зависимости:** нет  
**Файлы:**
- новый файл: `frontend/src/components/template-designer/SearchReplacePanel.tsx`
- `frontend/src/components/template-designer/TemplateDesigner.tsx`

### Задачи

- [ ] Установить `@tiptap/extension-search-and-replace`
- [ ] Создать компонент `SearchReplacePanel` (выпадающий drawer / inline panel сверху редактора):
  - Input «Найти» + кнопки «Предыдущее» / «Следующее» / счётчик «3 из 7»
  - Input «Заменить» + кнопки «Заменить» / «Заменить всё»
  - Чекбоксы: «Учитывать регистр» / «Слово целиком»
- [ ] Горячие клавиши: `Ctrl+F` — открыть панель поиска, `Escape` — закрыть
- [ ] Подсвечивать совпадения через decoration в ProseMirror (входит в расширение)

---

## Этап 8. Слэш-команды и Bubble Menu

**Агент:** `/frontend`  
**Зависимости:** Этапы 1–2 завершены  
**Файлы:**
- новый файл: `frontend/src/components/template-designer/SlashCommandMenu.tsx`
- `frontend/src/components/template-designer/TemplateDesigner.tsx`

### Задачи

#### Bubble Menu (при выделении текста)
- [ ] Подключить `@tiptap/react` BubbleMenu
- [ ] Показывать при наличии текстового выделения:
  - Жирный / Курсив / Подчёркнутый / Зачёркнутый
  - Цвет текста / Фон
  - Ссылка
  - Вставить параметр (открывает dropdown)
  - Обернуть в условный текст

#### Floating Menu (при пустой строке)
- [ ] Подключить FloatingMenu из `@tiptap/react`
- [ ] Показывать при курсоре в пустой строке:
  - «+ Параметр», «+ Условие», «+ Таблица», «+ Пункт», «+ Изображение», «/ Команда»

#### Slash-команды (`/`)
- [ ] Реализовать расширение `SlashCommand` на базе `Suggestion` из TipTap:
  - Триггер: `/` в начале строки или после пробела
  - Команды:
    | Ввод | Действие |
    |------|----------|
    | `/h1`, `/h2`, `/h3` | Заголовок 1–3 |
    | `/bullet`, `/список` | Маркированный список |
    | `/numbered`, `/нумер` | Нумерованный список |
    | `/clause`, `/пункт` | Нумерованный пункт |
    | `/table`, `/таблица` | Вставить таблицу 3×3 |
    | `/param`, `/параметр` | Открыть список параметров |
    | `/if`, `/условие` | Вставить условный блок |
    | `/hr`, `/линия` | Горизонтальная линия |
    | `/image`, `/фото` | Вставить изображение |
    | `/break`, `/разрыв` | Разрыв страницы |
  - Фильтрация команд по набранному тексту
  - Навигация: стрелки ↑↓, Enter, Escape

---

## Этап 9. Предпросмотр и экспорт

**Агент:** `/backend` (генерация) + `/frontend` (UI)  
**Зависимости:** Этап 3 (поля страницы в `formatting`)

### Подэтап 9а — Предпросмотр

**Агент:** `/frontend`

- [ ] Разблокировать кнопку «Предпросмотр» в `TemplateDesigner.tsx`
- [ ] Открывать Modal с readonly-версией TipTap (расширения те же, но `editable: false`)
- [ ] Применять `formatting` страницы в предпросмотре
- [ ] Поддержка параметров-заполнителей (показывать placeholder вместо пустых полей)

### Подэтап 9б — Экспорт DOCX

**Агент:** `/backend`

- [ ] Добавить зависимость `docx4j` или `apache-poi-ooxml` в `build.gradle`
- [ ] Создать сервис `DocxExportService`:
  - Принимает TipTap JSON + `formatting`
  - Конвертирует JSON в DOCX (paragraph per block, runs per inline)
  - Поддержка: Bold/Italic/Underline/Strike/Color/FontSize/FontFamily
  - Поддержка: Heading 1–3, списки, таблицы (с colspan/rowspan)
  - Поддержка: выравнивание, отступы, межстрочный интервал
  - Поддержка: колонтитулы (из `formatting.header` / `formatting.footer`) — статический текст + номер страницы
  - Подстановка параметров: `parameterField` → `{{code}}` (для последующей замены) или сразу значение
- [ ] Endpoint: `POST /api/templates/{id}/export/docx` → возвращает файл `application/vnd.openxmlformats-officedocument.wordprocessingml.document`

### Подэтап 9в — Экспорт PDF

**Агент:** `/backend`

- [ ] Добавить зависимость (вариант A: `openhtmltopdf` + `pdfbox`; вариант B: через Puppeteer headless — только если есть Docker)
- [ ] Создать сервис `PdfExportService`:
  - Рендерит TipTap JSON → HTML → PDF
  - Применяет те же CSS-стили что и редактор (шрифт, отступы, поля)
- [ ] Endpoint: `POST /api/templates/{id}/export/pdf`

### Подэтап 9г — Frontend: кнопки экспорта

**Агент:** `/frontend`

- [ ] В шапке `TemplateDesigner.tsx` добавить Dropdown «Экспорт»:
  - Экспорт в DOCX
  - Экспорт в PDF
- [ ] По клику — POST запрос, скачивание файла через `URL.createObjectURL`

---

## Этап 10. Обновление документации

**Агент:** `/architect`  
**Зависимости:** все предыдущие этапы завершены

### Задачи

- [ ] Обновить `docs/architecture.md`:
  - Секция «WYSIWYG-редактор» — полный перечень поддерживаемых возможностей
  - Добавить секцию «Настройки страницы» — описание модели `PageFormatting`
  - Обновить список расширений TipTap
  - Обновить API-контракты: добавить endpoint загрузки изображений, экспорта DOCX/PDF
  - Обновить `formatting` в схеме MongoDB-документа шаблона
- [ ] Обновить `backend/src/main/java/com/docforge/template/README.md`:
  - Добавить `DocxExportService`, `PdfExportService`, `ImageUploadService`
- [ ] Создать/обновить `frontend/src/components/template-designer/README.md`:
  - Описание всех компонентов: ContextMenu, TableBubbleMenu, SearchReplacePanel, SlashCommandMenu, PageSettingsDrawer
  - Описание кастомных расширений TipTap
- [ ] Закрыть этот план: проставить статус «Выполнено» и дату

---

## Карта зависимостей и порядок выполнения

```
Параллельно (старт одновременно):
├── Этап 1: Форматирование текста       (/frontend)
├── Этап 2: Выравнивание и отступы      (/frontend)
├── Этап 3а: Модель данных              (/architect)
├── Этап 5: Контекстное меню            (/frontend)
├── Этап 7: Поиск и замена             (/frontend)
└── Этап 6а: Ссылки, линия, разрыв     (/frontend)

После Этапа 3а:
└── Этап 3б: Backend formatting         (/backend)
    └── Этап 3в: UI настроек страницы   (/frontend)

После Этапа 5:
└── Этап 4: Таблицы (BubbleMenu)        (/frontend)

После Этапов 1 + 2:
└── Этап 8: Slash + BubbleMenu          (/frontend)

После Этапа 3в:
└── Этап 9а: Предпросмотр               (/frontend)
    Этап 9б: Экспорт DOCX              (/backend)
    Этап 9в: Экспорт PDF               (/backend)
    └── Этап 9г: Кнопки экспорта       (/frontend)

После всего:
└── Этап 10: Документация               (/architect)
```

---

## Приоритеты (если нужно поэтапно)

| Приоритет | Этапы | Обоснование |
|-----------|-------|-------------|
| **P1 — Сейчас** | 1, 2, 5, 4 | Базовые ожидания от текстового редактора |
| **P2 — Следующий спринт** | 3, 7, 8 | UX-улучшения, удобство работы |
| **P3 — Позднее** | 6, 9 | Требует backend-работы |
| **P4 — Финал** | 10 | Документация после всего |

---

## Пакеты npm для установки (сводка)

```bash
npm install \
  @tiptap/extension-strike \
  @tiptap/extension-subscript \
  @tiptap/extension-superscript \
  @tiptap/extension-color \
  @tiptap/extension-highlight \
  @tiptap/extension-font-family \
  @tiptap/extension-font-size \
  @tiptap/extension-text-style \
  @tiptap/extension-text-align \
  @tiptap/extension-link \
  @tiptap/extension-image \
  @tiptap/extension-horizontal-rule \
  @tiptap/extension-search-and-replace \
  @tiptap/suggestion
```

> `@tiptap/extension-font-size` может потребовать кастомной реализации через `TextStyle` marks — проверить наличие в реестре пакетов при установке.
