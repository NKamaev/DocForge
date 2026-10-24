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
  **Не импортируй `TemplateRepository` из других пакетов.** (Раньше исключение делалось для `ExportController`, но он теперь читает шаблон через `TemplateService.getReadable` — прямых импортов репозитория извне модуля нет.)
- `body` хранится как `Map<String, Object>` — структура определяется TipTap-редактором.
- MongoDB-поля в snake_case задаются через `@Field("field_name")` (Spring Data MongoDB).
- Версионирование шаблонов: `TemplateVersion` (MongoDB) + `TemplateSnapshot` (PostgreSQL), миграции V008, V015, V016.
- Тип `DIRECTORY` ссылается на справочник через `directoryId`; поля справочника автоматически создают составные параметры (`paramCode__fieldKey`).

## Права чтения (BUG-059)

Чтение проверяется в сервисном слое — контроллер лишь передаёт вызывающего
(`UUID` + флаг `ROLE_ADMIN`):

| Метод `TemplateService` | Правило |
|---|---|
| `getReadable(id, userId, admin)` | опубликованный шаблон — всем; черновик — владельцу/админу, иначе 403 «Нет доступа к шаблону» |
| `listVisible(page, size, category, published, createdAfter, userId, admin)` | не-админ видит опубликованные + свои черновики; админ — всё |
| `listVersionsVisible(templateId, userId, admin)` | владелец/админ — все версии; остальным — только опубликованные |
| `getSnapshotVisible(templateId, versionNumber, userId, admin)` | владелец/админ — любая версия; остальным — только опубликованная, либо версия, к которой привязан доступный им документ (`DocumentService.hasAccessibleDocumentForTemplateVersion`); иначе 403 «Нет доступа к версии шаблона» |

Признак «версия опубликована» — единый приватный хелпер `isPublished(version, templateId, depth)`
(BUG-058): `PUBLISH`/легаси-`NULL` → опубликована, `ROLLBACK` → если источник опубликован,
`MAJOR`/`MINOR` → черновик. Внутренние `getById`/`listVersions`/`getSnapshot` не фильтруют
и используются модулем `document` и внутренними потоками.

## Права изменения (BUG-068)

Проверка прав на изменение живёт в сервисном слое (`TemplateServiceImpl.assertCanModify`),
вызывающий резолвится из `SecurityContextHolder` — обойти правило нельзя ни через другой
эндпоинт, ни прямым вызовом сервиса.

Правило одно: **изменять шаблон может владелец (`template_registry.owner_id`) или
администратор** (`ROLE_ADMIN` в authorities либо `users.is_admin`). Иначе — 403
«Нет прав на изменение шаблона».

| Операция | Метод `TemplateService` |
|---|---|
| Обновить | `update(id, request)` |
| Удалить | `delete(id)` |
| Опубликовать / снять с публикации | `publish(id)` / `unpublish(id)` → `setPublished(id, published)` |
| Сохранить черновую версию | `saveVersion(templateId, request)` |
| Откат к версии | `rollback(templateId, versionNumber)` |
| Валидация | `validate(id)` |
| Тест Python-скрипта | `testScript(templateId, request)` |

`validate` и `testScript` — «оракулы» по содержимому черновика (второй возвращает текст
исключения скрипта, который может содержать литералы), поэтому защищены тем же правилом.

Неизвестный вызывающий (логин, которого нет в `users`) — не владелец и не администратор,
отсутствие контекста безопасности — тоже отказ: проверка закрыта по умолчанию (fail closed).
Все точки входа `/api/templates/**` требуют аутентификации (`SecurityConfig`), поэтому во
внешнем запросе вызывающий всегда есть.

В списке шаблонов (`TemplateListItemResponse`) возвращается `ownerId` — клиенту нужно знать
владельца, чтобы не показывать кнопки публикации/удаления для чужих шаблонов.

**Асимметрия с правами чтения:** read-гарды принимают вызывающего явно
(`getReadable(id, userId, admin)` и т.п.), write-гард резолвит его из
`SecurityContextHolder` — это защищает от подмены вызывающего на уровне
интерфейса сервиса. Для новых mutating-эндпоинтов копируй именно write-подход:
правило должно проверяться внутри сервиса, а не в контроллере.
