# DocForge — Backlog доработок

> **Дата составления:** 2026-04-16  
> **Статус:** планирование

---

## Шаг 0. Фиксация текущих изменений в git ✅

**Агент:** ручная команда / `/devops`  
**Приоритет:** выполнить ПЕРВЫМ, до начала любых доработок.  
**Статус:** выполнено (коммит `874da12`, 2026-04-16)

Накопившиеся изменения (publish/unpublish шаблонов, Directories-модуль, chip-поле параметра, доработки ToolBar и пр.) не входят ни в одну из задач ниже — их нужно выгрузить отдельным коммитом.

```bash
git add -A
git commit -m "feat: accumulated changes — directories module, publish/unpublish, param chip, toolbar improvements"
git push origin master
```

---

## Задача 1. Версионность шаблонов ✅

**Агент:** `/backend`, `/migrate`  
**Статус:** выполнено (коммит `b23be6c`, 2026-04-16)

### Суть
Каждое сохранение опубликованного шаблона должно создавать неизменяемый снимок (snapshot). Документы привязываются к конкретному снимку, а не к «живому» шаблону.

### Модель данных

#### PostgreSQL (миграция V008)
```sql
CREATE TABLE template_version (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_id     UUID NOT NULL REFERENCES template_registry(id) ON DELETE CASCADE,
    version_number  INT  NOT NULL,
    created_at      TIMESTAMPTZ DEFAULT now(),
    created_by      UUID REFERENCES users(id),
    comment         TEXT,
    UNIQUE (template_id, version_number)
);
CREATE INDEX idx_template_version_tid ON template_version(template_id, version_number DESC);
```

#### MongoDB — новая коллекция `template_snapshots`
```jsonc
{
  "_id": "uuid-версии",            // = template_version.id
  "template_id": "uuid-шаблона",
  "version_number": 3,
  "body":         { /* TipTap JSON — полная копия на момент публикации */ },
  "parameters":   [ /* полная копия */ ],
  "questionnaire": { /* полная копия */ },
  "formatting":   { /* полная копия */ },
  "scripts":      [ /* полная копия */ ],
  "created_at":   "ISO-8601"
}
```

### Логика версионирования

1. При **публикации шаблона** (`POST /api/templates/{id}/publish`) — автоматически создаётся новый снимок: `version_number = max(existing) + 1`. Тело шаблона копируется из MongoDB-документа `templates` в `template_snapshots`.
2. Поле `version` в `template_registry` — это номер последней опубликованной версии (для отображения в UI).
3. Черновик (неопубликованный шаблон) версии не создаёт; разработчик может редактировать шаблон сколько угодно.
4. Новый эндпоинт: `GET /api/templates/{id}/versions` — список всех версий с датами и комментариями.
5. Новый эндпоинт: `GET /api/templates/{id}/versions/{versionNumber}` — содержимое конкретного снимка.

### Изменения в существующих классах
- `TemplateService` / `TemplateServiceImpl` — добавить `publishWithSnapshot()`, `listVersions()`, `getVersion()`.
- `TemplateController` — добавить эндпоинты версий.
- `TemplateVersionRepository` (JPA), `TemplateSnapshotRepository` (Spring Data MongoDB) — новые репозитории.

### Git-коммит по завершении задачи
```
feat(template): template versioning — snapshot on publish, version history API
```

---

## Задача 2. Документы привязываются к версии шаблона и не изменяются ✅

**Агент:** `/backend`  
**Зависит от:** Задача 1  
**Статус:** выполнено (коммит `a421454`, 2026-04-16)

### Суть
- При создании документа (`POST /api/documents`) — фиксируется `template_snapshot_id` (UUID снимка) и `template_version_number`.
- Генерация (`POST /api/documents/{id}/generate`) использует **снимок**, не живой шаблон.
- После генерации `generated_body` и `current_body` — заморожены: изменения шаблона не влияют на уже созданные документы.

### Изменения в модели

#### MongoDB — `documents`
Добавить поля:
```jsonc
{
  "template_snapshot_id": "uuid-снимка",   // новое
  "template_version_number": 3              // новое (для отображения)
}
```

#### PostgreSQL — `document_registry`
Миграция V009:
```sql
ALTER TABLE document_registry
    ADD COLUMN template_snapshot_id UUID,
    ADD COLUMN template_version_number INT;
```

### Изменения в коде
- `DocumentServiceImpl.create()` — при создании документа: найти актуальный опубликованный снимок (`latestPublishedVersion`), сохранить его UUID.
- `DocumentGenerator` — принимать `TemplateSnapshot` (вместо `Template`) для генерации.
- `DocumentController` — в ответе `GET /api/documents/{id}` добавить `templateVersionNumber`.

### Git-коммит по завершении задачи
```
feat(document): create document from template snapshot; generation uses snapshot
```

---

## Задача 3. История правок документа и откат ✅

**Агент:** `/backend`, `/frontend`  
**Зависит от:** Задача 2  
**Статус:** выполнено (коммит `8af25ff`, 2026-04-16)

### Суть
Каждое явное сохранение документа (вручную или авто-save через Yjs debounce) создаёт ревизию. Пользователь видит список ревизий и может откатиться к любой из них.

### Модель данных

#### MongoDB — новая коллекция `document_revisions`
```jsonc
{
  "_id": "uuid-ревизии",
  "document_id": "uuid-документа",
  "revision_number": 5,
  "body": { /* TipTap JSON — снимок current_body на момент сохранения */ },
  "answers": { /* снимок answers */ },
  "saved_by": "uuid-пользователя",
  "saved_at": "ISO-8601",
  "comment": "Автосохранение"   // или пользовательский комментарий
}
```

#### PostgreSQL — миграция V010
```sql
CREATE TABLE document_revision (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id     UUID NOT NULL REFERENCES document_registry(id) ON DELETE CASCADE,
    revision_number INT NOT NULL,
    saved_by        UUID REFERENCES users(id),
    saved_at        TIMESTAMPTZ DEFAULT now(),
    comment         TEXT,
    UNIQUE (document_id, revision_number)
);
CREATE INDEX idx_doc_revision ON document_revision(document_id, revision_number DESC);
```

### API
```
GET    /api/documents/{id}/revisions                  — список ревизий (meta, без body)
GET    /api/documents/{id}/revisions/{revNum}         — тело ревизии
POST   /api/documents/{id}/revisions/{revNum}/restore — откатиться (создаёт новую ревизию)
```

### Frontend
- В `DocumentEditor.tsx` — кнопка «История» в тулбаре.
- Выдвижная панель (Ant Design `Drawer`) со списком ревизий: дата, автор, комментарий.
- Клик по ревизии — предпросмотр, кнопка «Восстановить».

### Git-коммит по завершении задачи
```
feat(document): revision history — save, list, restore revisions
```

---

## Задача 4. Создание/редактирование объектов справочника прямо из опросного листа ✅

**Агент:** `/frontend`  
**Статус:** выполнено (коммит `f970592`, 2026-04-16)

### Суть
В опросном листе для полей типа `DIRECTORY` добавить две кнопки рядом с select:
1. **«+ Создать»** — открывает модальное окно с пустой формой нового объекта справочника.
2. **«✏ Редактировать»** — открывает модальное окно с формой текущего выбранного объекта (активна только когда что-то выбрано).

После сохранения в модальном окне — список записей обновляется автоматически, новая/изменённая запись выбирается.

### Изменения
- `DirectoryField.tsx` (или аналог в `field-types/`) — добавить две кнопки (Ant Design `Button`).
- Новый компонент `DirectoryEntryModal.tsx` — форма создания/редактирования записи справочника.
  - Поля формы генерируются динамически по `schemaFields` справочника.
  - Поддерживаемые типы полей: `text`, `number`, `date`, `boolean`, `select` (если задан).
- API-вызов `POST /api/directories/{id}/entries` и `PUT /api/directories/{id}/entries/{eid}` — уже есть.

### Git-коммит по завершении задачи
```
feat(questionnaire): create/edit directory entry inline — modal from DirectoryField
```

---

## Задача 5. Диагностика и исправление условного текста ✅

**Агент:** `/debug`, `/tiptap`  
**Статус:** диагностика проведена (2026-04-16). Все сценарии работают корректно:
- `conditionalBlock`: `content: 'block+'` → таблицы допустимы внутри блока.
- Нумерованные пункты: `WorkspaceNumberedClauseNode` + `updateHiddenPositions` исключают скрытые пункты.
- DOCX-экспорт шаблона: рендерит весь контент (правильно, т.к. ответов нет).
- DOCX-экспорт документа: будет использовать pre-rendered `current_body` (Задача 6).  
Код изменений не потребовал.

### Суть
Провести исследование и починить (при необходимости) поведение `conditional_block` и `conditional_inline` в следующих сценариях:

| Сценарий | Ожидаемое поведение | Проверить |
|---|---|---|
| Весь абзац — условный текст | Отступы и форматирование абзаца сохраняются при показе/скрытии | `renderPreview.ts` + `ConditionalBlockNode` |
| Нумерованный пункт (`numbered_clause`) внутри условного блока | При скрытии пункт исключается из нумерации (`AutoNumberingPlugin` с фильтром `isHidden`) | `AutoNumberingPlugin.ts` |
| Таблица внутри условного блока | Таблица корректно скрывается/показывается | `ConditionalBlockNode` content schema |
| Экспорт DOCX: условные блоки | Скрытые блоки не попадают в DOCX | `DocxExportServiceImpl` |

**Если `AutoNumberingPlugin` не учитывает скрытые блоки** — добавить в него фильтр.  
**Если `content` у `conditional_block` не допускает `table`** — расширить content-схему.

### Git-коммит по завершении задачи
```
fix(tiptap): conditional block — indentation, numbered clauses, table inside, docx export
```

---

## Задача 6. Кнопки экспорта в DOCX/PDF в редакторе документа ✅

**Агент:** `/frontend`, `/backend`  
**Статус:** выполнено (коммиты `d2a1550`, `8af25ff`, 2026-04-16). Экспорт документа использует DocxExportService с форматированием из снимка шаблона.

### Суть
Сейчас экспорт реализован только для **шаблонов** (`POST /api/templates/{id}/export/docx`). Для **документов** эндпоинты заложены в архитектуре, но не реализованы и кнопок на фронте нет.

### Backend — `DocumentController` + `DocumentService`
Реализовать эндпоинты:
```
GET /api/documents/{id}/export/docx
GET /api/documents/{id}/export/pdf
```
Логика: взять `current_body` документа + `formatting` из снимка шаблона → передать в `DocxExportService` / `PdfExportService`.

### Frontend
- В `DocumentEditor.tsx` (тулбар) добавить кнопки «Скачать DOCX» и «Скачать PDF».
- При клике — запрос на соответствующий эндпоинт, скачивание blob-файла.
- Показывать `Spin` / `message.loading` на время генерации.
- Те же кнопки добавить в `QuestionnairePage` (рабочее пространство) — после генерации документа.

### Git-коммит по завершении задачи
```
feat(export): download document as DOCX/PDF — backend endpoints + frontend buttons
```

---

## Задача 7. Прозрачные границы таблиц при экспорте ✅

**Агент:** `/backend`  
**Статус:** выполнено (коммит `3d17cee`, 2026-04-16)

### Суть
Сейчас при экспорте в DOCX таблицы получают непрозрачные (чёрные) границы. Нужно: если в TipTap-таблице граница явно не задана — генерировать таблицу без видимых рамок (прозрачные/нулевые бордеры) в DOCX.

### Изменения в `DocxExportServiceImpl`
В методе `renderTable()`:
- По умолчанию ставить `CTTblBorders` с `none` для всех сторон (top, bottom, left, right, insideH, insideV).
- Читать атрибут `borderStyle` из TipTap JSON (если задан) и применять его.

В методе `renderTable()` в `PdfExportServiceImpl`:
- CSS для `<table>`: `border-collapse: collapse; border: none;` по умолчанию.
- Ячейки `<td>/<th>`: `border: none;` если не задано иное.

### Git-коммит по завершении задачи
```
fix(export): transparent table borders by default in DOCX/PDF export
```

---

## Задача 8. Логические группы условий (скобки)

**Агент:** `/frontend`, `/backend`

### Суть
В визуальном конструкторе условий (`ConditionBuilder.tsx`) добавить возможность группировать условия в скобки с операторами AND/OR. Пример: `(A AND B) OR C`.

### Модель данных (расширение `Condition`)

Текущая структура `simple`-условия — плоская. Нужно добавить рекурсивный тип:

```typescript
// Расширенная структура условия
type ConditionNode =
  | { type: 'leaf';  field: string; op: string; value: unknown }
  | { type: 'group'; operator: 'AND' | 'OR'; children: ConditionNode[] };

interface Condition {
  type: 'simple' | 'python';
  // для simple — корневой ConditionNode:
  root?: ConditionNode;
  // legacy поддержка:
  expression?: string;
  scriptId?: string;
}
```

### Backend — `ConditionEvaluator`
Добавить рекурсивный evaluator для `ConditionNode`:
- `leaf` — вычислить простое сравнение.
- `group` — рекурсивно вычислить детей и применить AND/OR.
- Обратная совместимость: если `expression` строка — парсить как раньше.

### Frontend — `ConditionBuilder.tsx`
- Кнопка «+ Группа» — добавляет вложенную группу с AND/OR.
- Визуально: вложенный блок с отступом и заголовком `AND` / `OR`.
- Drag-and-drop для перестановки условий и групп (AntD или react-dnd).

### Git-коммит по завершении задачи
```
feat(conditions): logical condition groups (parentheses) — nested AND/OR in ConditionBuilder
```

---

## Задача 9. Логические поля справочников в конструкторе условий

**Агент:** `/frontend`, `/backend`

### Суть
В конструкторе условий (`ConditionBuilder.tsx`) сейчас доступны только параметры шаблона. Нужно добавить поля справочников (которые могут быть boolean, select и пр.) в список доступных для условий.

### Логика

Параметр типа `DIRECTORY` с `directoryFields: ["is_active", "type"]` — каждое поле справочника доступно как `bank__is_active`, `bank__type` (уже работает для подстановки в текст). Нужно то же самое раскрыть в `ConditionBuilder`.

### Изменения
- `ConditionBuilder.tsx` — при загрузке параметров типа `DIRECTORY` подгружать схему справочника и добавлять производные поля `{code}__{fieldKey}` с соответствующими типами.
- `ConditionEvaluator` (backend) — уже умеет работать с `bank__fieldKey` из `answers`. Проверить и покрыть тестом.
- `DirectoryFieldType` в enum — добавить метаданные о типе поля (чтобы UI знал, какой компонент сравнения рендерить: текст, число, boolean, select).

### Git-коммит по завершении задачи
```
feat(conditions): directory boolean/select fields available in condition builder
```

---

## Задача 10. Иммутабельность значений справочника в документах + подсветка устаревших

**Агент:** `/backend`, `/frontend`, `/tiptap`

### Суть
Созданный документ не должен автоматически меняться при изменении записи справочника. Если данные источника изменились, это подсвечивается в документе. Пользователь может применить обновлённые данные явно.

### Архитектура решения

#### 1. Снимок ответов при генерации (backend)
При `POST /api/documents/{id}/generate`:
- Для каждого параметра типа `DIRECTORY`: в `answers` сохранять не только `bank = "entry-uuid"`, но и полный снимок полей:
```jsonc
"answers": {
  "bank":       "entry-uuid-123",
  "bank__name": "Сбербанк",           // снимок на момент генерации
  "bank__bik":  "044525225",
  "_directory_snapshots": {
    "bank": {
      "entry_id": "entry-uuid-123",
      "values": { "name": "Сбербанк", "bik": "044525225" },
      "snapshot_at": "2026-04-16T10:00:00Z"
    }
  }
}
```

#### 2. Трекинг изменений (backend)
Новый сервисный метод `DirectoryService.checkAnswersForStaleness(documentId)`:
- Для каждого `_directory_snapshots[param]` — загрузить актуальную запись из MongoDB.
- Сравнить поля: если отличаются — пометить как устаревшие.
- Вернуть `Map<paramCode, StalenessInfo>` где `StalenessInfo = { isStale: bool, currentValues: Map, snapshotValues: Map }`.

Новый эндпоинт:
```
GET /api/documents/{id}/staleness   — список устаревших полей справочников
```

#### 3. Подсветка в редакторе (frontend + tiptap)
- `WorkspaceParameterFieldNode` — при загрузке документа проверять staleness.
- Если поле устарело — рендерить с оранжевой/жёлтой обводкой и тултипом «Данные справочника изменились. Нажмите для обновления».
- Клик на такое поле — вопрос в ОЛ, где кнопка «Применить актуальные данные».

#### 4. Применение обновлённых данных
В `DirectoryField.tsx`:
- Если выбранная запись изменилась — показывать badge «Обновлено» рядом с select.
- Кнопка «Применить изменения» — обновляет `answers._directory_snapshots` и значения в `current_body`.

### Git-коммит по завершении задачи
```
feat(document): directory value snapshots; stale field highlighting in editor
```

---

## Задача 11. Таблицы без заливки 2×1 по умолчанию ✅

**Агент:** `/tiptap`, `/backend`  
**Статус:** выполнено (коммит `3d17cee`, 2026-04-16) — экспорт исправлен; в редакторе заливки не было (TableCellExtension: backgroundColor=null по умолчанию)

### Суть
При создании таблицы в конструкторе шаблонов / редакторе документа ячейки должны создаваться без фоновой заливки (backgroundColor = none). Сейчас, по всей видимости, применяется заливка для чётных строк (паттерн 2×1).

### Изменения

#### TipTap (frontend) — `TableCell` / `TableHeader` extensions
- Проверить атрибуты `backgroundColor` в кастомном `TableCellNode` (если есть) или в стандартном TipTap Table.
- В `addAttributes()` для `backgroundColor` установить `default: null` (не `'#f0f0f0'` или подобное).
- CSS в `TemplateDesigner.css` / `DocumentEditor.css`: убрать `nth-child` правила с фоновым цветом для ячеек таблицы.

#### Export (backend) — `DocxExportServiceImpl`
- В `renderTable()` убрать логику, добавляющую `CTShd` (shading) для ячеек по умолчанию.
- Заливка применяется только если явно задана в TipTap JSON (`attrs.backgroundColor`).

#### Export (backend) — `PdfExportServiceImpl`
- CSS для `td, th`: `background-color: transparent;` по умолчанию. Инлайн-стиль только при наличии явного значения.

### Git-коммит по завершении задачи
```
fix(table): no background fill by default — TipTap editor, DOCX and PDF export
```

---

## Порядок реализации (рекомендуемый)

```
Шаг 0  → Задача 11 → Задача 7  → Задача 5
                                         ↓
Задача 1 → Задача 2 → Задача 3  → Задача 6
                                         ↓
Задача 4 → Задача 10
                ↓
Задача 8 → Задача 9
```

| # | Задача | Агент | Сложность | Зависимости |
|---|---|---|---|---|
| 0 | Commit текущих изменений | git | trivial | — |
| 1 | Версионность шаблонов | `/backend` + `/migrate` | высокая | 0 |
| 2 | Документы из снимка шаблона | `/backend` | средняя | 1 |
| 3 | История ревизий документа | `/backend` + `/frontend` | высокая | 2 |
| 4 | CRUD справочника из ОЛ | `/frontend` | средняя | 0 |
| 5 | Диагностика условного текста | `/debug` + `/tiptap` | средняя | 0 |
| 6 | Экспорт документа DOCX/PDF | `/frontend` + `/backend` | средняя | 2 |
| 7 | Прозрачные границы таблиц | `/backend` | низкая | 0 |
| 8 | Логические группы условий | `/frontend` + `/backend` | высокая | 0 |
| 9 | Поля справочников в условиях | `/frontend` + `/backend` | средняя | 0 |
| 10 | Иммутабельность справочников | `/backend` + `/frontend` + `/tiptap` | очень высокая | 2 |
| 11 | Таблицы без заливки по умолчанию | `/tiptap` + `/backend` | низкая | 0 |
