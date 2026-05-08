# BUG-017: PdfExportServiceImpl — пустой numberedClause рендерит пустой <p>

| Поле | Значение |
|---|---|
| **Дата** | 2026-04-19 |
| **Тип** | `bug` |
| **Статус** | `TODO` |
| **Агенты** | `/backend` |
| **Коммит** | — |
| **Зависит от** | BUG-006 |
| **GitHub Issue** | [#63](https://github.com/NKamaev/DocForge/issues/63) |

---

## Постановка задачи (от пользователя)

> Результаты код-ревью BUG-006: SUGGESTION — `numberedClause` при пустом `children` рендерит пустой `<p></p>`, что создаёт неотображаемый абзац в PDF.

---

## Причина ошибки

**Корень проблемы:** В `PdfExportServiceImpl.renderNode()` кейс `numberedClause` при отсутствии дочерних узлов (`children == null`) не вставляет плейсхолдер:

```java
case "numberedClause" -> {
    sb.append("<p").append(paragraphStyle(node)).append(">"); 
    if (children != null) children.forEach(c -> renderInline(sb, c));
    // ← нет else-ветки: <p></p> — пустой тег
    sb.append("</p>");
}
```

Это не вызывает `SAXParseException` (пустой тег валиден в XML), но создаёт невидимый абзац в итоговом PDF, что нарушает нумерацию пунктов.

**Цепочка сбоя:**
1. Документ содержит `numberedClause` без текста (пустой нумерованный пункт).
2. `renderNode()` рендерит `<p style="..."></p>` — пустой тег.
3. PDF содержит невидимый, но занимающий место абзац; нумерация пунктов сбивается визуально.

**Исправление:** добавить `else sb.append("&#160;");` — аналогично уже сделанному в `paragraph` (BUG-006).

---

## Файлы для изменения

```
backend/src/main/java/com/docforge/export/PdfExportServiceImpl.java
  — строки 133–137: добавить else sb.append("&#160;"); к кейсу numberedClause
```

---

## Поэтапное исполнение

1. В `PdfExportServiceImpl.renderNode()` найти кейс `numberedClause` (строка ~133).
2. Добавить `else sb.append("&#160;");` после `if (children != null) ...`.
3. Запустить backend unit-тесты.
4. Вручную проверить: создать документ с пустым нумерованным пунктом → экспорт PDF.

---

## Автоматические тесты

### Что покрыть

| Сценарий | Тип теста | Файл |
|---|---|---|
| PDF-экспорт документа с пустым numberedClause не падает | unit | `PdfExportServiceImplTest.java` (новый) |

### Команды запуска

```bash
cd backend && ./gradlew test --tests "com.docforge.export.PdfExportServiceImplTest"
```

---

## Чеклист ручного тестирования

- [ ] Создать шаблон с пустым нумерованным пунктом → создать документ → экспорт PDF → файл скачивается без ошибки
- [ ] Регрессия: обычный экспорт (непустые пункты) не затронут

---

## История

| Дата | Событие |
|---|---|
| 2026-04-19 | Задача создана по результатам ревью BUG-006 (SUGGESTION от /review) |

---

## Завершение задачи

> **Обязательно выполнить перед отчётом о выполнении!**

- [ ] Автотесты запущены и проходят (`./gradlew test`)
- [ ] Ручной чеклист выполнен полностью
- [ ] Код прошёл ревью (`/review`)
- [ ] Git-коммит создан: `fix(export): add &#160; placeholder for empty numberedClause in PDF`
- [ ] Статус обновлён: DONE
- [ ] Реестр обновлён: docs/tasks/README.md
- [ ] GitHub Issue закрыт (state: closed) + комментарий с хешем коммита
