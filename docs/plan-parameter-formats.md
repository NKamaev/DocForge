# Plan: Parameter Representations (Форматы отображения параметров)

> **Статус:** Draft  
> **Дата:** 2026-04-15  
> **Тип задачи:** Feature — форматирование параметров при подстановке в документ

---

## 1. Суть задачи

Один и тот же параметр (например, `contract_date`) может потребоваться в разных местах документа в разных форматах:

- В шапке: `20.03.2026`
- В теле: `20 марта 2026 г.`
- В преамбуле: `двадцатого марта двадцать двадцать шестого года`

Аналогично для чисел (цифрами / прописью / с единицей измерения) и ФИО (полностью / с инициалами / в разных падежах).

**Текущее поведение:** формат жёстко прошит в `ParameterSubstitutor` (DATE → «20 марта 2026 г.», NUMBER → `1 000`, FIO → строка as-is).

---

## 2. Архитектурное решение

### 2.1 Две точки хранения формата

| Точка | Где хранится | Приоритет |
|-------|-------------|-----------|
| **Формат по умолчанию** | `Parameter.format` (MongoDB, внутри шаблона) | низкий |
| **Формат на конкретном поле** | `parameterField.attrs.format` (TipTap body JSON) | высокий (override) |

Правило применения: если на ноде `parameterField` задан `format` — используется он. Иначе — `Parameter.format`. Иначе — системный default (обратная совместимость).

### 2.2 Почему не «один глобальный формат на параметр»

Параметр `contract_date` может встречаться в документе несколько раз (заголовок, преамбула, раздел оплаты) и требовать разного вида — это стандартная практика для юридических документов.

---

## 3. Модель данных

### 3.1 Новые enums (backend)

#### `DateFormat`
```java
public enum DateFormat {
    DOTS,            // 20.03.2026  (новый default для UI)
    LONG_RU,         // 20 марта 2026 г.  (текущий system default)
    LONG_RU_GENITIVE,// двадцатого марта двадцать двадцать шестого года
    SHORT,           // 20.03.26
    YEAR_ONLY,       // 2026
    MONTH_YEAR,      // март 2026
    ISO              // 2026-03-20
}
```

#### `NumberDisplayFormat`
```java
public enum NumberDisplayFormat {
    DEFAULT,                    // 1 000 000  (текущий default)
    WORDS,                      // один миллион
    WORDS_WITH_NUMBER,          // 1 000 000 (один миллион)
    WITH_UNIT,                  // 1 000 000 штук
    WORDS_WITH_UNIT,            // один миллион рублей
    WORDS_WITH_NUMBER_AND_UNIT, // 1 000 000 (один миллион) рублей
    PERCENTAGE                  // 50%
}
```

#### `FioDisplayFormat`
```java
public enum FioDisplayFormat {
    FULL,        // Иванов Иван Иванович
    INITIALS,    // Иванов И.И.
    FIRST_LAST,  // Иван Иванов
    LAST_ONLY,   // Иванов
    FIRST_ONLY   // Иван
}
```

#### `GrammaticalCase`
```java
public enum GrammaticalCase {
    NOMINATIVE,    // именительный: Иванов Иван Иванович
    GENITIVE,      // родительный:  Иванова Ивана Ивановича
    DATIVE,        // дательный:    Иванову Ивану Ивановичу
    ACCUSATIVE,    // винительный:  Иванова Ивана Ивановича
    INSTRUMENTAL,  // творительный: Ивановым Иваном Ивановичем
    PREPOSITIONAL  // предложный:   Иванове Иване Ивановиче
}
```

### 3.2 Новый класс `ParameterFormat` (embedded MongoDB)

```
ParameterFormat {
    dateFormat:       DateFormat         // null → LONG_RU (system default)
    numberFormat:     NumberDisplayFormat// null → DEFAULT
    unit:             String             // единица измерения: "рублей", "штук", "%" и т.д.
    fioFormat:        FioDisplayFormat   // null → FULL
    grammaticalCase:  GrammaticalCase    // null → NOMINATIVE
}
```

Хранится как вложенный объект в MongoDB (`embedded`), поле `format` в `Parameter`.

### 3.3 Изменение `Parameter`

```java
// Добавить поле:
private ParameterFormat format;   // null = используются system defaults
```

### 3.4 Изменение `parameterField` TipTap node attrs

```jsonc
{
  "type": "parameterField",
  "attrs": {
    "parameterCode": "contract_date",
    "display": "дата договора",
    "format": {               // ← НОВОЕ (nullable, override поверх Parameter.format)
      "dateFormat": "DOTS"
    }
  }
}
```

---

## 4. Backend — сервисный слой

### 4.1 Новый сервис `NumberToWordsService`

**Пакет:** `com.docforge.common.format`

```
NumberToWordsService
  + toWords(long number, String unit): String
    // "один миллион рублей", "двадцать пять штук"
  + toWordsWithNumber(long number, String unit): String
    // "1 000 000 (один миллион) рублей"
```

Реализация: собственная Java-реализация для целых чисел до 999 999 999 999 (достаточно для юридических документов). Дробные числа — поддерживать `50,75` → «пятьдесят 75/100».

Зависимость: **не нужна внешняя библиотека** — стандартная реализация для русского языка несложна (~200 строк).

### 4.2 Новый сервис `FioFormatterService`

**Пакет:** `com.docforge.common.format`

```
FioFormatterService
  + format(String fullFio, FioDisplayFormat displayFormat, GrammaticalCase grammaticalCase): String
```

Формат входа: «Иванов Иван Иванович» (Фамилия Имя Отчество).

Для склонения — встроенная реализация на правилах русского языка (покрывает наиболее
распространённые окончания: -ов/-ев, -ин/-ын, -ский/-цкий, прилагательные, слова на -а/-я/-й/-ь).
Внешние зависимости не требуются. Несклоняемые и иностранные имена возвращаются без изменений.

Для инициалов (INITIALS) — строковое преобразование, без склонения.

### 4.3 Обновление `ParameterSubstitutor`

```java
// Текущая сигнатура:
public String format(Object value, Parameter parameter)

// Новая сигнатура:
public String format(Object value, Parameter parameter, @Nullable ParameterFormat formatOverride)
```

Логика выбора формата:
```
ParameterFormat effectiveFormat = 
    formatOverride != null        ? formatOverride 
    : parameter.getFormat() != null ? parameter.getFormat()
    : SYSTEM_DEFAULTS
```

Метод без `formatOverride` оставить как делегирующий обёртку (обратная совместимость).

### 4.4 Обновление `DocumentGenerator`

В Step 4 (substitute parameter fields) — читать `format` из attrs ноды:

```java
// Внутри лямбды treeWalker для "parameterField":
Map<String, Object> formatAttrs = (Map<String, Object>) attrs.get("format");
ParameterFormat formatOverride = parseFormat(formatAttrs); // null если не задан
String text = substitutor.format(value, param, formatOverride);
```

---

## 5. Frontend

### 5.1 Новые типы (`types/templateModel.ts`)

```typescript
export type DateFormat =
  | 'DOTS'               // 20.03.2026
  | 'LONG_RU'            // 20 марта 2026 г.
  | 'LONG_RU_GENITIVE'   // двадцатого марта ...
  | 'SHORT'              // 20.03.26
  | 'YEAR_ONLY'          // 2026
  | 'MONTH_YEAR'         // март 2026
  | 'ISO'                // 2026-03-20

export type NumberDisplayFormat =
  | 'DEFAULT'
  | 'WORDS'
  | 'WORDS_WITH_NUMBER'
  | 'WITH_UNIT'
  | 'WORDS_WITH_UNIT'
  | 'WORDS_WITH_NUMBER_AND_UNIT'
  | 'PERCENTAGE'

export type FioDisplayFormat =
  | 'FULL' | 'INITIALS' | 'FIRST_LAST' | 'LAST_ONLY' | 'FIRST_ONLY'

export type GrammaticalCase =
  | 'NOMINATIVE' | 'GENITIVE' | 'DATIVE'
  | 'ACCUSATIVE' | 'INSTRUMENTAL' | 'PREPOSITIONAL'

export interface ParameterFormat {
  dateFormat?: DateFormat | null
  numberFormat?: NumberDisplayFormat | null
  unit?: string | null             // для WITH_UNIT вариантов
  fioFormat?: FioDisplayFormat | null
  grammaticalCase?: GrammaticalCase | null
}

// Обновить Parameter:
export interface Parameter {
  // ... existing fields ...
  format?: ParameterFormat | null  // ← НОВОЕ
}
```

### 5.2 Обновление `previewRenderer.ts` — `formatValue`

Расширить `formatValue` чтобы принимать `ParameterFormat | null` override и применять его.

```typescript
function formatValue(
  paramCode: string,
  answer: unknown,
  parameters: Parameter[],
  formatOverride?: ParameterFormat | null,
): string
```

Клиент реализует форматы кроме WORDS/LONG_RU_GENITIVE (число прописью и склонение) — они рендерятся через backend при генерации документа. В превью для этих форматов показывать значение as-is с пометкой (или через API-вызов).

**Реализуемые на клиенте форматы:**

| Тип | Формат | Клиент |
|-----|--------|--------|
| DATE | DOTS, SHORT, YEAR_ONLY, MONTH_YEAR, ISO, LONG_RU | ✅ |
| DATE | LONG_RU_GENITIVE | ❌ (backend) |
| NUMBER | DEFAULT, WITH_UNIT, PERCENTAGE | ✅ |
| NUMBER | WORDS, WORDS_WITH_NUMBER, WORDS_WITH_UNIT, WORDS_WITH_NUMBER_AND_UNIT | ❌ (backend) |
| FIO | FULL, INITIALS, FIRST_LAST, LAST_ONLY, FIRST_ONLY (NOMINATIVE) | ✅ |
| FIO | Любой падеж кроме NOMINATIVE | ❌ (backend) |

### 5.3 Обновление TipTap `parameterField` node

В `ParameterFieldNode.tsx` (designer) и `WorkspaceParameterFieldNode.tsx` (workspace):
- Attrs расширить полем `format?: ParameterFormat`
- В `renderHTML` и `parseHTML` обрабатывать `format`
- В `NodeView` отображать метку формата под кодом параметра: `[Иванов И.И.]` вместо `[ФИО клиента]`

### 5.4 Новый компонент `ParameterFormatSelector`

**Файл:** `frontend/src/components/template-designer/ParameterFormatSelector.tsx`

UI для выбора формата — `<Select>` с группами опций:
- Для параметра в боковой панели дизайнера (редактирует `Parameter.format`)
- Для конкретного ноды через контекстное меню / тулбар (редактирует `parameterField.attrs.format`)

---

## 6. Структура новых файлов

```
backend/src/main/java/com/docforge/
├── common/
│   └── format/                         ← НОВЫЙ ПАКЕТ
│       ├── DateFormat.java             ← enum
│       ├── NumberDisplayFormat.java    ← enum
│       ├── FioDisplayFormat.java       ← enum
│       ├── GrammaticalCase.java        ← enum
│       ├── ParameterFormat.java        ← embedded class
│       ├── NumberToWordsService.java   ← сервис
│       └── FioFormatterService.java    ← сервис
└── template/
    └── model/
        └── Parameter.java              ← добавить поле format: ParameterFormat

frontend/src/
├── types/
│   └── templateModel.ts               ← добавить новые типы и обновить Parameter
├── utils/
│   └── previewRenderer.ts             ← обновить formatValue
├── components/
│   └── template-designer/
│       ├── ParameterFormatSelector.tsx ← НОВЫЙ компонент
│       ├── extensions/
│       │   └── ParameterFieldNode.tsx  ← добавить attrs.format
│       └── WorkspaceParameterFieldNode.tsx ← обновить рендер
```

---

## 7. API — изменения не нужны

Формат хранится в существующих полях:
- `Parameter.format` → в теле шаблона MongoDB
- `parameterField.attrs.format` → в TipTap body JSON

Существующие `GET/PUT /api/templates/{id}` подхватят новые поля автоматически.

---

## 8. Миграции

**Flyway-миграция не нужна** — новые поля добавляются в MongoDB (schema-free). PostgreSQL не затрагивается.

---

## 9. Порядок реализации

```
Шаг 1 — Модель данных (backend)
  1.1  Создать enums: DateFormat, NumberDisplayFormat, FioDisplayFormat, GrammaticalCase
  1.2  Создать ParameterFormat (embedded class)
  1.3  Добавить поле format: ParameterFormat в Parameter.java

Шаг 2 — Сервисы форматирования (backend)
  2.1  Реализовать NumberToWordsService (целые числа до 10^12)
  2.2  Добавить petrovich4j в build.gradle.kts
  2.3  Реализовать FioFormatterService
  2.4  Обновить ParameterSubstitutor (добавить formatOverride перегрузку)

Шаг 3 — DocumentGenerator (backend)
  3.1  Читать attrs.format из parameterField ноды
  3.2  Передавать formatOverride в substitutor

Шаг 4 — Frontend типы и утилиты
  4.1  Добавить новые типы в templateModel.ts
  4.2  Обновить formatValue в previewRenderer.ts

Шаг 5 — TipTap nodes
  5.1  Добавить attrs.format в ParameterFieldNode
  5.2  Обновить WorkspaceParameterFieldNode

Шаг 6 — Дизайнер UI
  6.1  Создать ParameterFormatSelector
  6.2  Подключить к боковой панели параметра (Parameter.format)
  6.3  Подключить к inline-меню конкретного поля (attrs.format override)
```

---

## 10. Спорные моменты / Решения

| Вопрос | Решение |
|--------|---------|
| Склонение ФИО: библиотека или свой код? | Petrovich4j — проверенная, Apache 2.0, ~150кБ |
| Число прописью: библиотека или свой код? | Своя реализация — простой случай, без зависимостей |
| Форматы на клиенте vs сервере | Простые форматы на клиенте (превью), все — на сервере (генерация) |
| Обратная совместимость | `format = null` → старое поведение. Шаблоны без format работают без изменений |
| Единица измерения где хранится? | В `ParameterFormat.unit` (на параметре) и/или `parameterField.attrs.format.unit` (на ноде) |
