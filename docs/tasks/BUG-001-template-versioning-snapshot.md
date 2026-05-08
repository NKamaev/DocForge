# BUG-001: Версионность шаблонов не работала — снимки не создавались, документы использовали живой шаблон

| Поле | Значение |
|---|---|
| **Дата** | 2026-04-16 |
| **Тип** | `bug` |
| **Статус** | `DONE` |
| **Агенты** | `/debug`, `/backend`, `/frontend` |
| **Коммит** | `d87735d` |
| **Зависит от** | — |
| **GitHub Issue** | [#50](https://github.com/NKamaev/DocForge/issues/50) |

---

## Постановка задачи (от пользователя)

```
Задача 1. Версионность шаблонов отсутствует.
Задача 2. Документы не привязываются к версии шаблона.
Вношу правки в шаблон, аналогичные правки вносятся в текст документа.
```

```
При попытке опубликовать шаблон выдаётся ошибка
«Ошибка при изменении статуса публикации».
```

---

## Интерпретация

Реализация версионирования (задачи 1–2 из backlog) была выполнена на бэкенде, но не работала из-за двух независимых багов. В результате:
- При публикации шаблона снимки не создавались → `template_version` оставалась пустой
- Новые документы создавались с `templateSnapshotId = null`
- Страница `/fill` грузила **живой** шаблон вместо зафиксированного снимка → правки шаблона немедленно попадали в документы

---

## Причина ошибки

**Корень проблемы №1 — MongoDB не умеет сериализовать `OffsetDateTime`:**

`TemplateSnapshot.createdAt` был объявлен как `java.time.OffsetDateTime`. MongoDB Spring Data не регистрирует кодек для `OffsetDateTime` (в отличие от `Instant`). При попытке сохранить снимок в коллекцию `template_snapshots` бросалось:
```
CodecConfigurationException: Can't find a codec for OffsetDateTime
```
Транзакция откатывалась — ни снимок, ни запись `template_version` не сохранялись.

**Корень проблемы №2 — `AuditLog.details` имеет тип `jsonb`, но Hibernate вставлял `varchar`:**

```
ERROR: column "details" is of type jsonb but expression is of type character varying
```
Не блокировал publish (async), но засорял логи ошибками.

**Корень проблемы №3 — Frontend грузил живой шаблон:**

`QuestionnairePage.tsx` вызывал `templatesApi.getTemplate(doc.templateId)` вместо загрузки снимка. Поля `doc.templateVersionNumber` и `doc.templateSnapshotId` не были добавлены в TypeScript-тип `Document`.

**Дополнительная проблема — `TemplateServiceImpl.update()` инкрементировал `version`:**

Строка `registry.setVersion(registry.getVersion() + 1)` при каждом сохранении черновика делала счётчик версий бессмысленным (49, 31, 26). По спецификации задачи 1 `version` должен отражать только опубликованные версии.

---

## Файлы для изучения перед исполнением

```
backend/src/main/java/com/docforge/template/model/TemplateSnapshot.java
backend/src/main/java/com/docforge/template/TemplateServiceImpl.java
backend/src/main/java/com/docforge/audit/AuditLog.java
frontend/src/components/questionnaire/QuestionnairePage.tsx
frontend/src/types/document.ts
frontend/src/types/template.ts
frontend/src/api/templatesApi.ts
```

---

## Файлы для изменения

```
backend/src/main/java/com/docforge/template/model/TemplateSnapshot.java
  — OffsetDateTime createdAt → Instant createdAt

backend/src/main/java/com/docforge/template/TemplateServiceImpl.java
  — .createdAt(version.getCreatedAt().toInstant())
  — update(): убрать registry.setVersion(registry.getVersion() + 1)

backend/src/main/java/com/docforge/audit/AuditLog.java
  — @JdbcTypeCode(SqlTypes.JSON) для поля details

frontend/src/types/document.ts
  — добавить templateSnapshotId, templateVersionNumber в Document

frontend/src/types/template.ts
  — добавить интерфейс TemplateSnapshot

frontend/src/api/templatesApi.ts
  — добавить метод getSnapshot(templateId, versionNumber)

frontend/src/components/questionnaire/QuestionnairePage.tsx
  — загружать snapshot вместо живого шаблона
```

---

## Поэтапное исполнение

1. **Backend** — исправить `TemplateSnapshot.createdAt`: `OffsetDateTime` → `Instant`
2. **Backend** — обновить вызов `.createdAt()` в `TemplateServiceImpl`: добавить `.toInstant()`
3. **Backend** — исправить `AuditLog.details`: добавить `@JdbcTypeCode(SqlTypes.JSON)`
4. **Backend** — убрать инкремент `version` из `update()` в `TemplateServiceImpl`
5. **Frontend** — добавить `templateSnapshotId`, `templateVersionNumber` в `Document` тип
6. **Frontend** — добавить интерфейс `TemplateSnapshot` в `types/template.ts`
7. **Frontend** — добавить метод `getSnapshot` в `templatesApi.ts`
8. **Frontend** — исправить `QuestionnairePage.tsx`: загружать снимок вместо живого шаблона
9. **Перезапуск бэкенда** и проверка публикации шаблона

---

## Автоматические тесты

### Что покрыть

| Сценарий | Тип теста | Файл |
|---|---|---|
| `publish()` создаёт `TemplateVersion` + `TemplateSnapshot` | integration | `TemplateServiceImplIT.java` |
| `create()` документа получает `templateSnapshotId` из снимка | integration | `DocumentServiceImplIT.java` |
| `AuditLog.details` сохраняется в jsonb без ошибки | integration | `AuditServiceImplIT.java` |

### Команды запуска

```bash
cd backend && ./gradlew test --tests "com.docforge.template.TemplateServiceImplIT"
cd backend && ./gradlew test --tests "com.docforge.document.DocumentServiceImplIT"
cd frontend && npm test -- --run
```

---

## Чеклист ручного тестирования

- [x] Открыть шаблон → нажать «Снять публикацию» → нажать «Опубликовать»
- [x] Проверить: в БД появилась запись в `template_version` (`SELECT * FROM template_version`)
- [x] Создать новый документ на основе этого шаблона
- [x] Проверить: документ имеет `template_version_number` (не NULL) в `document_registry`
- [x] Внести правку в шаблон и сохранить
- [x] Открыть документ на странице `/fill` → убедиться, что правка шаблона НЕ отразилась
- [-] Регрессия: экспорт документа в DOCX работает. Экспорт не работает и не работал ранее. Игнорировать. Будет создана отдельная задача.

---

## История

| Дата | Событие |
|---|---|
| 2026-04-16 | Ошибка обнаружена пользователем |
| 2026-04-16 | Диагностика: найдены 4 независимых причины |
| 2026-04-16 | Исправления внесены в 7 файлов, бэкенд перезапущен |
| 2026-04-16 | Написаны и запущены интеграционные тесты (все зелёные) |
| 2026-04-16 | Коммит создан, задача закрыта |

---

## Завершение задачи

> **Обязательно выполнить перед отчётом о выполнении!**

- [x] Автотесты запущены и проходят (TemplateServiceImplIT, DocumentServiceImplIT, AuditServiceImplIT)
- [x] Ручной чеклист выполнен полностью
- [x] Git-коммит создан: `fix(template): snapshot on publish, frontend uses snapshot instead of live template`
- [x] Статус обновлён в этом файле: `DONE`
- [x] Реестр обновлён: [docs/tasks/README.md](README.md)
