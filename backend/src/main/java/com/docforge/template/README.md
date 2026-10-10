# Модуль template

Хранение и доступ к шаблонам документов в MongoDB.

## Ответственность
- MongoDB-модель шаблона со всеми вложенными структурами
- Доступ к коллекции `templates` через `TemplateRepository`

## Связь с PostgreSQL
`Template.id` == `template_registry.id` — единый UUID. Метаданные шаблона
(название, категория, публикация, права) хранятся в PostgreSQL (`template_registry`).
Полное тело шаблона — только в MongoDB.

## Структура модели (`model/`)

```
Template                          — корневой документ (@Document "templates")
├── id: UUID                      — совпадает с template_registry.id
├── version: int
├── parameters: List<Parameter>
│   ├── code: String              — уникальный код (snake_case)
│   ├── label: String
│   ├── type: ParameterType       — TEXT | NUMBER | DATE | FIO | BOOLEAN | SELECT | DIRECTORY
│   ├── required: boolean
│   ├── defaultValue: Object      — строка, число, boolean или $TODAY/$TOMORROW
│   ├── options: List<String>     — только для SELECT
│   └── validation: ParameterValidation
│       ├── min: Double
│       ├── max: Double
│       └── pattern: String
├── questionnaire: Questionnaire
│   ├── title: String
│   └── pages: List<QuestionnairePage>
│       ├── title: String
│       └── questions: List<Question>
│           ├── id: String
│           ├── parameterCode: String
│           ├── label: String
│           ├── hint: String
│           ├── order: int
│           └── visibilityCondition: Condition   — null = всегда видим
│               ├── type: ConditionType          — SIMPLE | PYTHON
│               ├── expression: String
│               └── scriptId: String             — ссылка на Script.id (для PYTHON)
├── body: Map<String, Object>     — TipTap JSON (ProseMirror)
│                                   кастомные ноды: parameter_field, conditional_block
│   ├── directoryId: String       — UUID справочника (только для DIRECTORY)
│   ├── directoryFields: List<String> — поля справочника (только для DIRECTORY)
├── formatting: DocumentFormatting
│   ├── pageSize: String          — "A4", "A3", "Letter"
│   ├── orientation: String       — "portrait" | "landscape"
│   ├── margins: PageMargins      — top/right/bottom/left в мм
│   ├── defaultFont: String
│   ├── defaultFontSize: int
│   └── lineSpacing: double
└── scripts: List<Script>
    ├── id: String                — ссылается из Condition.scriptId
    ├── name: String
    ├── code: String              — Python: должен присваивать result = <boolean>
    └── description: String
```

## Ключевые классы

| Класс | Файл |
|-------|------|
| `Template` | `model/Template.java` |
| `Parameter` | `model/Parameter.java` |
| `ParameterType` | `model/ParameterType.java` — TEXT, NUMBER, DATE, FIO, BOOLEAN, SELECT, **DIRECTORY** |
| `ParameterValidation` | `model/ParameterValidation.java` |
| `Questionnaire` | `model/Questionnaire.java` |
| `QuestionnairePage` | `model/QuestionnairePage.java` |
| `Question` | `model/Question.java` |
| `Condition` | `model/Condition.java` |
| `ConditionType` | `model/ConditionType.java` |
| `Script` | `model/Script.java` |
| `DocumentFormatting` | `model/DocumentFormatting.java` |
| `PageMargins` | `model/PageMargins.java` |
| `TemplateRepository` | `TemplateRepository.java` |

## Экспорт

Экспорт шаблонов реализован в модуле `com.docforge.export`:

| Класс | Описание |
|-------|---------|
| `DocxExportService` / `DocxExportServiceImpl` | TipTap JSON → DOCX (Apache POI 5 XWPF) |
| `PdfExportService` / `PdfExportServiceImpl` | TipTap JSON → HTML → PDF (openhtmltopdf + PDFBox) |
| `ExportController` | `POST /api/templates/{id}/export/docx` и `.../pdf` |

Изображения из шаблона хранятся в `com.docforge.upload` и доступны по `/api/uploads/images/{filename}`.

## Правила
- Другие модули обращаются к шаблонам только через `TemplateService`.
  **Не импортируй `TemplateRepository` из других пакетов.** (Исключение: `ExportController` — прямой доступ к MongoDB для чтения тела шаблона без лишних DTO.)
- `body` хранится как `Map<String, Object>` — структура определяется TipTap-редактором.
- MongoDB-поля в snake_case задаются через `@Field("field_name")` (Spring Data MongoDB).
- Версионирование шаблонов: `TemplateVersion` (MongoDB) + `TemplateSnapshot` (PostgreSQL), миграции V008, V015, V016.
- Тип `DIRECTORY` ссылается на справочник через `directoryId`; поля справочника автоматически создают составные параметры (`paramCode__fieldKey`).
