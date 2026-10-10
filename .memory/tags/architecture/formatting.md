# Форматирование параметров (даты, числа, ФИО)

> Тег: `architecture:formatting` · Обновлено: `2026-10-10`

## [2026-10-10 10:00] Enum-ы и система фолбэков

- **Перечисления** (`common/format/`):
  - `DateFormat`: DOTS, LONG_RU (дефолт), LONG_RU_GENITIVE, SHORT, YEAR_ONLY, MONTH_YEAR, ISO.
  - `NumberDisplayFormat`: DEFAULT, WORDS, WORDS_WITH_NUMBER, WITH_UNIT, WORDS_WITH_UNIT, WORDS_WITH_NUMBER_AND_UNIT, PERCENTAGE.
  - `FioDisplayFormat`: FULL (дефолт), INITIALS, FIRST_LAST, LAST_ONLY, FIRST_ONLY.
  - `GrammaticalCase`: NOMINATIVE…PREPOSITIONAL.
- **`ParameterFormat`** встроен в `Parameter` (dateFormat, numberFormat, unit, fioFormat, grammaticalCase). Per-node переопределение — `parameterField.attrs.format` (`DocumentGenerator.parseFormat:353-371`).
- **Порядок резолва** (`ParameterSubstitutor.format` :99-103): override ноды > формат параметра > системный дефолт.
- **Системные дефолты:** DATE→LONG_RU «20 марта 2026 г.» (MONTHS_GENITIVE :45-48); NUMBER→RU grouping; BOOLEAN→«Да»/«Нет»; FIO→FULL/NOMINATIVE; null→«—» (:38).
- **`NumberToWordsService`:** целые 0…999 999 999 999, RU; склонение единиц «штука|штуки|штук» (`ParameterSubstitutor.java:218-231`).
- **`FioFormatterService`:** определение пола по отчеству; склонение по 6 падежам.
- **Frontend-зеркало:** `utils/previewRenderer.ts` — `formatValue` (:176-223), genitive-месяцы :117-148, `Intl ru-RU` + `pluralizeUnit` :155-166. Backend-only форматы со фолбэком на DEFAULT.
- **Источник:** `backend/src/main/java/com/docforge/common/format/`, explore backend/frontend reports.

<!-- Новые записи добавляй СВЕРХУ. -->