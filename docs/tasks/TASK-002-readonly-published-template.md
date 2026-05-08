# TASK-002: Блокировка редактирования опубликованного шаблона

<!-- Скопируй этот файл, переименуй: TASK-NNN-slug.md или BUG-NNN-slug.md -->
<!-- Обнови реестр: docs/tasks/README.md -->

| Поле | Значение |
|---|---|
| **Дата** | 2026-04-16 |
| **Тип** | `feature` |
| **Статус** | `DONE` |
| **Агенты** | `/backend`, `/frontend` |
| **Коммит** | `57539c4` (backend), `f5f731a` (frontend) |
| **Зависит от** | — |
| **GitHub Issue** | [#52](https://github.com/NKamaev/DocForge/issues/52) |

---

## Постановка задачи (от пользователя)

> Verbatim — точная формулировка пользователя.

```
Необходимо отключить возможность вносить правки в опубликованный шаблон.
(в текст, в параметры, в опросный лист, формулировки и т.д.)
```

---

## Интерпретация

Шаблон в статусе `PUBLISHED` должен стать полностью read-only:

- **Backend**: все мутирующие эндпоинты шаблона (`PUT /api/templates/{id}`, `PUT /api/templates/{id}/questionnaire`, обновление параметров, секций и т.д.) должны возвращать `403 Forbidden` (или `409 Conflict`) если шаблон опубликован.
- **Frontend**: редактор шаблона должен переходить в режим просмотра — все поля, кнопки сохранения и редактирования скрыты/задизейблены; отображается баннер «Шаблон опубликован — редактирование недоступно».
- Блокировка распространяется на: текст/содержимое, параметры (variables), опросный лист (questionnaire), формулировки секций, мета-данные (название, описание).
- Единственное допустимое действие над опубликованным шаблоном — создание новой версии (draft) на основе него (если такая функция будет реализована в будущем).

---

## Файлы для изучения перед исполнением

```
backend/src/main/java/com/docforge/template/
  ├── TemplateController.java          — все эндпоинты шаблона
  ├── TemplateService.java / *ServiceImpl.java
  ├── model/Template.java / TemplateStatus.java
  └── questionnaire/                   — контроллер/сервис опросника

frontend/src/
  ├── pages/TemplateEditor/            — редактор шаблона
  ├── components/template/             — компоненты шаблона
  ├── api/templateApi.ts               — API-вызовы
  └── types/template.ts                — тип TemplateStatus

docs/architecture.md                  — модель данных, статусная машина шаблона
```

---

## Файлы для изменения

```
backend/src/main/java/com/docforge/template/
  ├── TemplateService*.java            — добавить проверку статуса перед сохранением
  ├── TemplateController.java          — опционально: guard на уровне контроллера
  └── exception/TemplatePublishedException.java  — новое кастомное исключение

frontend/src/
  ├── pages/TemplateEditor/            — read-only режим (disable inputs, hide buttons)
  └── components/template/PublishedBanner.tsx    — баннер «шаблон опубликован»
```

---

## Поэтапное исполнение

1. **Backend — исключение** — создать `TemplatePublishedException` (`409 Conflict`, сообщение «Template is published and cannot be modified»); зарегистрировать в `GlobalExceptionHandler`.
2. **Backend — защита сервиса** — в каждом методе сервиса, изменяющем шаблон, добавить проверку: `if (template.getStatus() == PUBLISHED) throw new TemplatePublishedException(id)`.
3. **Backend — тесты** — unit-тесты на попытку изменить опубликованный шаблон → ожидаем исключение; integration-тест через HTTP → ожидаем `409`.
4. **Frontend — read-only режим** — при загрузке шаблона проверять `status === 'PUBLISHED'`; если да — все `Input`, `TextArea`, `Select`, кнопки «Сохранить» переводить в `disabled`/скрывать; редактор TipTap — `editable: false`.
5. **Frontend — баннер** — показывать `Alert` / баннер в верхней части редактора с текстом «Шаблон опубликован — редактирование недоступно».
6. **Frontend — тесты** — проверить, что в published-режиме поля задизейблены.

---

## Автоматические тесты

### Что покрыть

| Сценарий | Тип теста | Файл |
|---|---|---|
| Попытка обновить текст опубликованного шаблона → 409 | unit | `TemplateServiceTest.java` |
| Попытка обновить questionnaire опубликованного шаблона → 409 | unit | `TemplateServiceTest.java` |
| PUT /api/templates/{id} для published → HTTP 409 | integration | `TemplateControllerIT.java` |
| Редактор в published-режиме: поля задисейблены | unit (React) | `TemplateEditor.test.tsx` |

### Команды запуска

```bash
# Backend unit
cd backend && ./gradlew test --tests "com.docforge.template.TemplateServiceTest"

# Backend integration
cd backend && ./gradlew test --tests "com.docforge.template.TemplateControllerIT"

# Frontend
cd frontend && npm test -- --run src/pages/TemplateEditor
```

---

## Чеклист ручного тестирования

- [x] Открыть опубликованный шаблон → отображается баннер «Шаблон опубликован — редактирование недоступно»
- [x] Все текстовые поля и редактор TipTap недоступны для ввода
- [x] Кнопки «Сохранить», «Добавить параметр», «Добавить секцию» скрыты или задизейблены
- [x] Вкладка «Опросный лист»: поля вопросов недоступны для редактирования
- [x] Вкладка «Параметры»: значения переменных недоступны для изменения
- [x] Попытка прямого PUT-запроса к API → `409 Conflict`
- [x] Черновик (DRAFT) шаблона — редактирование по-прежнему работает (регрессия)
- [x] Создание документа на основе опубликованного шаблона — не сломано (регрессия)

---

## История

| Дата | Событие |
|---|---|
| 2026-04-16 | Задача создана |
| 2026-04-16 | Backend выполнен: `TemplatePublishedException` + guard в `update()` + тесты, коммит `57539c4` |
| 2026-04-16 | Frontend выполнен: `PublishedBanner`, readOnly-пропсы в панелях, editor.setEditable, тесты, коммит `f5f731a` |
| 2026-04-16 | Ревью пройдено: 0 critical, 1 warning, 4 suggestion → TASK-003 (backend), TASK-004 (frontend) |

---

## Завершение задачи

> **Обязательно выполнить перед отчётом о выполнении!**

- [x] Автотесты запущены и проходят (`./gradlew test` / `npm test`)
- [x] Ручной чеклист выполнен полностью
- [x] Код прошёл ревью (`/review`) — замечания перенесены в TASK-003, TASK-004
- [x] Git-коммиты созданы: `57539c4` (backend), `f5f731a` (frontend)
- [x] Статус обновлён в этом файле: `DONE`
- [x] Реестр обновлён: [docs/tasks/README.md](README.md)
