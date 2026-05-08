# Модуль export

Экспорт шаблонов документов в DOCX и PDF.

## API

| Метод | Путь | Описание |
|-------|------|---------|
| `POST` | `/api/templates/{id}/export/docx` | Скачать шаблон как DOCX |
| `POST` | `/api/templates/{id}/export/pdf` | Скачать шаблон как PDF |

Оба endpoint'а возвращают файл с заголовком `Content-Disposition: attachment`.

## Архитектура

```
TipTap JSON (Map<String, Object>)
        │
        ├── DocxExportServiceImpl ──── Apache POI XWPF ──→ byte[] .docx
        │
        └── PdfExportServiceImpl ───── HTML-рендер ──→ openhtmltopdf ──→ byte[] .pdf
```

### DOCX (DocxExportServiceImpl)

Использует **Apache POI 5 (XWPF)** для прямой генерации DOCX из TipTap JSON.

**Поддерживаемые элементы:**
- Блоки: `paragraph`, `heading` (H1–H3), `bulletList`, `orderedList`, `numberedClause`, `table`, `horizontalRule`, `pageBreak`, `conditionalBlock`
- Инлайн: `text` с marks (`bold`, `italic`, `underline`, `strike`, `subscript`, `superscript`, `textStyle`, `link`, `highlight`), `hardBreak`, `parameterField`
- Форматирование страницы: размер (A4/A3/Letter), ориентация, поля

**Параметрические поля** отображаются как `{{display}}` — серым цветом.

### PDF (PdfExportServiceImpl)

TipTap JSON → HTML-строка → PDF через **openhtmltopdf + PDFBox**.

HTML генерируется внутри сервиса (без внешних вызовов). CSS-стили учитывают `DocumentFormatting`: размер страницы, поля, шрифт, межстрочный интервал.

## Ключевые классы

| Класс | Описание |
|-------|---------|
| `DocxExportService` | Интерфейс |
| `DocxExportServiceImpl` | Apache POI реализация |
| `PdfExportService` | Интерфейс |
| `PdfExportServiceImpl` | openhtmltopdf реализация |
| `ExportController` | REST-контроллер (читает `Template` напрямую из `TemplateRepository`) |

## Зависимости

```kotlin
implementation("org.apache.poi:poi-ooxml:5.3.0")
implementation("com.openhtmltopdf:openhtmltopdf-pdfbox:1.0.10")
```
