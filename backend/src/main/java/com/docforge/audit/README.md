# Модуль audit

Логирование действий пользователей.

## Ответственность

- Запись в `audit_log` всех значимых действий: CREATE, UPDATE, EXPORT, PUBLISH и т.д.
- Чтение истории действий по entity (тип + ID) или по пользователю

## Хранение

PostgreSQL, таблица `audit_log` (Flyway V004).

```
id          BIGSERIAL PK
user_id     UUID FK → users
entity_type VARCHAR(50)  — TEMPLATE | DOCUMENT
entity_id   UUID
action      VARCHAR(50)  — CREATE | UPDATE | DELETE | EXPORT | PUBLISH | GENERATE
details     JSONB
created_at  TIMESTAMPTZ
```

## Ключевые классы

| Класс | Описание |
|-------|---------|
| `AuditLog` | JPA Entity, таблица `audit_log` |
| `AuditLogRepository` | JPA-доступ с индексами по entity и user |
| `AuditService` | Интерфейс: `log()`, `findByEntity()`, `findByUser()` |
| `AuditServiceImpl` | Реализация |

## Зависимости

- `com.docforge.user.UserService` — получение текущего пользователя
