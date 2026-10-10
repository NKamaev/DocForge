# Персистентность — PostgreSQL vs MongoDB

> Тег: `architecture:persistence` · Обновлено: `2026-10-10`

## [2026-10-10 10:00] Разделение БД и договоренности по UUID

- **Факт:** PostgreSQL (JPA + Flyway) — метаданные/реестры/пользователи/аудит. MongoDB (@Document) — тела (TipTap JSON), вложенные структуры.
- **Общий UUID:** `template_registry.id` == `templates._id`, `document_registry.id` == `documents._id`. Создание пишет сначала PG, потом Mongo (`DocumentServiceImpl.java:76-101`).
- **Источник:** `docs/specs/contracts/data-contracts.md`, explore backend report.

## Маппинг таблиц/коллекций

| Контекст | PostgreSQL | MongoDB |
|---|---|---|
| Шаблон | `template_registry`, `template_version` | `templates`, `template_snapshots` |
| Документ | `document_registry`, `document_members`, `document_revision` | `documents`, `document_revisions` |
| Справочники | `directory_registry`, `directory_schema_field` | `directories` |
| Прочее | `users`, `audit_log`, `folder`, `folder_member` | — |

## Flyway V001–V016 (V007 сознательно пропущен)

- V001 users · V002 template_registry · V003 document_registry + document_participants · V004 audit_log(+индексы) · V005 directory tables · V006 field_type/field_options · V008 template_version · V009 snapshot refs · V010 document_revision · V011 folders (+default_folder_id) · V012 folder_member + `document_participants`→`document_members` rename + source/granted_by, дефолт роли VIEWER · V013 folder parent_id · V014 default admin (bcrypt) · V015 semver columns · V016 major/minor.
- **Резерв версии:** `newVersionNumber = major*10000 + minor` (`TemplateServiceImpl.java:198`).
- **JPA:** `ddl-auto: validate` — Flyway единственный источник правды схемы.
- **Источник:** `backend/src/main/resources/db/migration/`, explore backend report.

## Ключевые JPQL-запросы доступа

- `DocumentRegistryRepository.findAccessibleByUser` — owner OR direct member OR folder member (`DocumentRegistryRepository.java:16-29`).
- `FolderRepository.findAccessibleByUser` (`:15-24`).

<!-- Новые записи добавляй СВЕРХУ. -->