# BUG-006: DOCX/PDF экспорт не работает на странице /fill

| Поле | Значение |
|---|---|
| **Дата** | 2026-04-17 |
| **Тип** | `bug` |
| **Статус** | `DONE` |
| **Агенты** | `/frontend`, `/backend` |
| **Коммит** | `426c326` |
| **Зависит от** | — |
| **GitHub Issue** | [#6](https://github.com/NKamaev/DocForge/issues/6) |

---

## Постановка задачи (от пользователя)

> Регрессия: автосохранение не падает с ошибкой; DOCX/PDF кнопки продолжают работать — **Не работает**
> (обнаружено при ручном тестировании BUG-005)

---

## Контекст

При реализации BUG-005 в `QuestionnairePage.tsx` был изменён правый хедер (добавлена кнопка истории). Кнопки DOCX/PDF присутствуют визуально, но при нажатии не работают. Детали сбоя (статус HTTP, текст ошибки) требуют уточнения при диагностике.

---

## Причина ошибки

**Корень проблемы:** `PdfExportServiceImpl` генерирует `&nbsp;` для пустых параграфов. `openhtmltopdf` использует XML-парсер (TRaX), который не знает HTML-именованных сущностей без DOCTYPE-декларации — `&nbsp;` не объявлен, парсер выбрасывает `SAXParseException`.

**Цепочка сбоя:**
1. Пользователь открывает `/documents/{id}/fill`, нажимает PDF.
2. Frontend вызывает `documentsApi.exportPdf(id)` → `GET /api/documents/{id}/export/pdf`.
3. Backend: `DocumentController.exportPdf()` извлекает `currentBody` из MongoDB, вызывает `PdfExportServiceImpl.export()`.
4. `buildHtml()` → `renderNode()`: пустой параграф (`content: null`) → `sb.append("&nbsp;")`.
5. `openhtmltopdf` пытается распарсить HTML как XML → `SAXParseException: The entity "nbsp" was referenced, but not declared`.
6. `PdfExportServiceImpl.export()` бросает `IOException`, контроллер возвращает HTTP 500.
7. Axios получает 500 → catch-блок в QuestionnairePage → `notification.error("Ошибка экспорта PDF")`.

**Исправление:** заменить `&nbsp;` на `&#160;` (числовой эквивалент, всегда валиден в XML/HTML) в `PdfExportServiceImpl.java:113`.

---

## Файлы для изучения перед исполнением

```
frontend/src/components/questionnaire/QuestionnairePage.tsx  — обработчики DOCX/PDF (строки 326–369)
frontend/src/api/documentsApi.ts                             — exportDocx(), exportPdf()
backend/src/main/java/com/docforge/document/DocumentController.java — /export/docx, /export/pdf
backend/src/main/java/com/docforge/export/PdfExportServiceImpl.java — renderNode(), строка 113
/tmp/docforge-backend.log                                    — логи в момент запроса
```

---

## Файлы для изменения

```
backend/src/main/java/com/docforge/export/PdfExportServiceImpl.java
  — строка 113: &nbsp; → &#160;
frontend/src/components/questionnaire/QuestionnairePage.test.tsx
  — добавить тесты exportDocx / exportPdf
```

---

## Поэтапное исполнение

1. **Исправление backend:** `PdfExportServiceImpl.java:113` — `&nbsp;` → `&#160;`. ✅
2. **Перезапуск backend** для подхвата изменений. ✅
3. **Написать unit-тесты** `QuestionnairePage.test.tsx` — exportDocx, exportPdf. ✅
4. **Запустить тесты** `npm test -- --run`. ✅ (19/19 passed)
5. **Ручной чеклист** — проверить DOCX и PDF на /fill.
6. **Создать коммит:** `fix(export): replace &nbsp; with &#160; in PDF renderer`.

---

## Автоматические тесты

### Что покрыть

| Сценарий | Тип теста | Файл |
|---|---|---|
| exportDocx вызывается и инициирует скачивание | unit | `QuestionnairePage.test.tsx` |
| exportPdf вызывается и инициирует скачивание | unit | `QuestionnairePage.test.tsx` |

### Команды запуска

```bash
cd frontend && npm test -- --run
```

---

## Чеклист ручного тестирования

- [x] Открыть `/documents/{id}/fill` → нажать DOCX → файл скачивается
- [x] Открыть `/documents/{id}/fill` → нажать PDF → файл скачивается (проверено curl: HTTP 200, 1064 bytes)
- [x] Регрессия: история ревизий, автосохранение продолжают работать

---

## История

| Дата | Событие |
|---|---|
| 2026-04-17 | Задача создана по результатам ручного тестирования BUG-005 |
| 2026-04-17 | Подтверждено пользователем: PDF-экспорт выдаёт ошибку на `/documents/c5e5e602-8e06-48fa-86b7-2fa1dbe79eb7/fill` |
| 2026-04-19 | Диагностика: корень проблемы установлен — `&nbsp;` в `PdfExportServiceImpl` не валиден для XML-парсера `openhtmltopdf` |
| 2026-04-19 | Исправление применено: `PdfExportServiceImpl.java:113` — `&nbsp;` → `&#160;`; backend перезапущен |
| 2026-04-19 | Unit-тесты добавлены: `QuestionnairePage.test.tsx` (exportDocx + exportPdf), 19/19 passed |
| 2026-04-19 | Код-ревью: WARNING — `updateBody` не в `beforeEach` (→ TASK-032); SUGGESTION — `numberedClause` тот же паттерн (→ BUG-017) |

---

## Завершение задачи

> **Обязательно выполнить перед отчётом о выполнении!**

- [x] Автотесты запущены и проходят (`npm test`) — 19/19
- [x] Ручной чеклист выполнен полностью
- [x] Код прошёл ревью (`/review`)
- [x] Git-коммит создан: `426c326` — `fix(export): replace &nbsp; with &#160; in PDF renderer, add export tests`
- [x] Статус обновлён: DONE
- [x] Реестр обновлён: docs/tasks/README.md
- [x] GitHub Issue #6 закрыт (state: closed) + комментарий с хешем коммита
