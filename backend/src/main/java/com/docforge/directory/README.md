# Модуль directory

Пользовательские справочники (реестры) с настраиваемой схемой полей.

## Ответственность

- CRUD справочников (название, схема полей)
- CRUD записей внутри справочника
- Тип параметра `DIRECTORY` в шаблонах ссылается на справочник

## Хранение

| Хранилище | Сущность | Назначение |
|-----------|---------|------------|
| PostgreSQL | `DirectoryRegistry` | Метаданные справочника: name, owner |
| PostgreSQL | `DirectorySchemaField` | Схема полей: field_key, label, display_order |
| MongoDB | `directories` | Записи справочника (embedded `entries[{id, values}]`) |

## API

| Метод | Путь | Описание |
|-------|------|---------|
| GET | `/api/directories` | Список справочников |
| POST | `/api/directories` | Создать справочник |
| GET | `/api/directories/{id}` | Справочник + поля + записи |
| PUT | `/api/directories/{id}` | Обновить название/схему |
| DELETE | `/api/directories/{id}` | Удалить |
| POST | `/api/directories/{id}/entries` | Добавить запись |
| PUT | `/api/directories/{id}/entries/{eid}` | Обновить запись |
| DELETE | `/api/directories/{id}/entries/{eid}` | Удалить запись |

## Ключевые классы

| Класс | Описание |
|-------|---------|
| `DirectoryController` | REST-контроллер |
| `DirectoryService` / `DirectoryServiceImpl` | Бизнес-логика |
| `DirectoryRegistryRepository` | JPA (PostgreSQL) |
| `DirectorySchemaFieldRepository` | JPA (PostgreSQL) |
| `DirectoryDocumentRepository` | MongoDB |

## Связь с шаблонами

Параметр типа `DIRECTORY` в шаблоне содержит:
- `directoryId` — UUID справочника
- `directoryFields` — список `field_key`, доступных для подстановки в теле шаблона

При выборе записи в ОЛ автоматически заполняются `paramCode__fieldKey` параметры.
