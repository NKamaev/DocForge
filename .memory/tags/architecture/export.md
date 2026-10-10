# Экспорт DOCX / PDF

> Тег: `architecture:export` · Обновлено: `2026-10-10`

## [2026-10-10 10:00] Подход и ограничения

- **DOCX** (`DocxExportServiceImpl`, Apache POI XWPF): `MM_TO_TWIPS=56.6929`, `PT_TO_HALF_PT=2` (:20); page setup + `renderNode` с `RenderContext`. Внутренний конвертер `DocxConverter`: `DEFAULT_SIZE_HALF=28`, `DEFAULT_FONT="Times New Roman"`; поддерживает doc/paragraph/heading/text (+marks bold/italic/underline/strike/color/fontSize/fontFamily)/bulletList/orderedList/listItem/table(+row/header/cell)/hardBreak; поля A4 20/30/15mm.
- **PDF** (`PdfExportServiceImpl`, openhtmltopdf `PdfRendererBuilder`): `FONT_FAMILY="Noto Serif"` с алиасами Times New Roman/Times/serif; регистрирует `/fonts/NotoSerif-*.ttf` (:19-44). Стратегия: TipTap JSON → HTML → PDF.
- **`NodeResolver`:** `templateParam` (`attrs.fieldId` → raw answer, placeholder `—`) и `clauseRef` (`attrs.clauseId` → «п. X.Y» через `buildClauseNumberingMap` по `numberedClause`).
- **Экспорт шаблона:** `POST /api/templates/{id}/export/docx|pdf` (`ExportController`). Экспорт документа — в `DocumentController`.
- **Ограничения:** parameterField → text только при экспорте (интерактива нет); PDF без фолбэка кириллического шрифта вне Noto Serif + алиасов; числа с дробью в `WORDS`-форматах падают на цифры (`ParameterSubstitutor.java:186-198`).
- **Источник:** `backend/src/main/java/com/docforge/export/`, explore backend report.

## Типовой инцидент (урок)

- **BUG-019:** скрытые по условию `conditionalBlock`/`conditionalInline` попадали в DOCX/PDF — экспортные `renderNode` рендерили `conditionalBlock` безусловно. Урок: экспорт должен применять ту же фильтрацию условий, что `DocumentController.applyAnswers`. Связанный тег: `pitfalls:conditional-rendering`.

<!-- Новые записи добавляй СВЕРХУ. -->