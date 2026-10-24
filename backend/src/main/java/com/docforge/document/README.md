# Модуль `document`

Управление документами, созданными из шаблонов.

## Ответственность

- CRUD документов (создание из шаблона, список, удаление)
- Хранение ответов на опросный лист (`answers`)
- Генерация тела документа (`generated_body`) из шаблона + ответов
- Хранение актуального состояния после правок (`current_body`)
- Поддержка Yjs CRDT-состояния для коллаборации (`yjs_state`)
- Экспорт в DOCX / PDF

## Хранение

| Хранилище  | Сущность              | Назначение                                     |
|------------|-----------------------|------------------------------------------------|
| PostgreSQL | `DocumentRegistry`    | Метаданные: name, status, owner, template_id   |
| MongoDB    | `Document`            | Тело документа, ответы, yjs_state              |

`id` одинаков в обоих хранилищах — генерируется приложением (`UUID.randomUUID()`).

### PostgreSQL — `document_registry`

```
id          UUID PK  (= MongoDB _id)
template_id UUID FK → template_registry
name        VARCHAR(500) NOT NULL
owner_id    UUID FK → users
status      VARCHAR(50) DEFAULT 'DRAFT'   -- DRAFT | GENERATED | EXPORTED
created_at  TIMESTAMPTZ
updated_at  TIMESTAMPTZ
```

### PostgreSQL — `document_participants`

```
document_id UUID FK → document_registry  (ON DELETE CASCADE)
user_id     UUID FK → users
role        VARCHAR(50) DEFAULT 'EDITOR'  -- OWNER | EDITOR | VIEWER
joined_at   TIMESTAMPTZ
PK (document_id, user_id)
```

### MongoDB — `documents`

```json
{
  "_id": "uuid",
  "template_id": "uuid",
  "template_version": 1,
  "answers": { "param_code": "значение" },
  "generated_body": { /* TipTap JSON */ },
  "current_body":   { /* TipTap JSON */ },
  "yjs_state":      "<binary>",
  "created_at": "ISO-8601",
  "updated_at": "ISO-8601"
}
```

## API

```
GET    /api/documents                         — мои документы
POST   /api/documents                         — создать из шаблона
GET    /api/documents/{id}                    — получить документ
DELETE /api/documents/{id}                    — удалить

GET    /api/documents/{id}/questionnaire      — опросный лист с учётом видимости
PUT    /api/documents/{id}/questionnaire/answers — сохранить ответы

POST   /api/documents/{id}/generate           — сгенерировать тело из ответов

GET    /api/documents/{id}/export/docx        — экспорт DOCX
GET    /api/documents/{id}/export/pdf         — экспорт PDF

GET    /api/documents/{id}/participants       — список участников
POST   /api/documents/{id}/participants       — добавить участника
DELETE /api/documents/{id}/participants/{uid} — убрать участника
```

## Зависимости

- `com.docforge.template.TemplateService` — загрузка шаблона для генерации
- `com.docforge.user.UserService` — получение текущего пользователя

Никогда не импортируй `TemplateRepository` или `UserRepository` напрямую.

## Межмодульный доступ к шаблонам

`DocumentService.hasAccessibleDocumentForTemplateVersion(templateId, versionNumber, userId)`
— есть ли у пользователя доступ к документу, созданному из указанной версии шаблона.
Используется модулем `template` (только через этот интерфейс), чтобы участник
совместной работы мог прочитать снимок черновой версии, к которой привязан документ
(BUG-059). Реализация — `DocumentRegistryRepository.findByTemplateIdAndTemplateVersionNumber`
+ `DocumentPermissionService.getEffectiveRole(...)`.
