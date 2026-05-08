# План доработок: расширенное редактирование таблиц

> **Статус:** Планирование  
> **Создан:** 2026-04-10  
> **Стек:** TipTap 3 (extension-table) + ProseMirror + React 18 + Ant Design 5

---

## Текущее состояние

### Что уже работает
- `Table.configure({ resizable: true })` — изменение ширины колонок перетаскиванием разделителя (встроено в TipTap через `prosemirror-tables`)
- `TableBubbleMenu` — панель управления: добавить/удалить строку, колонку, объединить/разбить ячейки, удалить таблицу
- `TableCell` attrs: `colspan`, `rowspan`, `colwidth`, `align`

### Исправлено (2026-04-10): видимость resize-handle

**Проблема:** Перетаскивание разделителя колонок работало, но пользователь не видел ни изменения курсора, ни самого разделителя.

**Причина:** `prosemirror-tables` при наведении на границу колонки:
1. Добавляет класс `resize-cursor` на `.ProseMirror` — но CSS для него не был задан
2. Вставляет `div.column-resize-handle` (абсолютно позиционированный) — но `td`/`th` не имели `position: relative`
3. Добавляет класс `column-resize-dragging` во время перетаскивания — тоже без стилей

**Решение** (применено в `TemplateDesigner.css`):
```css
/* position: relative на ячейках — контейнер для handle */
.ProseMirror th, .ProseMirror td { position: relative; }

/* Синий вертикальный разделитель */
.column-resize-handle { position: absolute; right: -2px; width: 4px; background: #1677ff; }

/* Курсор col-resize при наведении */
.ProseMirror.resize-cursor, .ProseMirror.resize-cursor * { cursor: col-resize !important; }

/* Подсветка ячейки при перетаскивании */
.column-resize-dragging { background: #bae0ff !important; }
```

### Чего не хватает (запрос пользователя + отраслевой стандарт)

| Функция | Приоритет | Сложность |
|---------|-----------|-----------|
| Явное задание ширины колонки / высоты строки | Высокий | Средняя |
| Вертикальное выравнивание текста (сверху / центр / снизу) | Высокий | Низкая |
| Ориентация текста (0° / 90° / 180° / 270°) | Высокий | Средняя |
| Цвет фона ячейки | Высокий | Низкая |
| Управление границами (цвет, стиль, видимость) | Высокий | Высокая |
| Цвет текста в ячейке (уже через TextStyle) | Низкий | — |
| Выравнивание по горизонтали (уже через align attr) | Низкий | — |

---

## Анализ TipTap API

### setCellAttribute — основной механизм

Команда `editor.chain().focus().setCellAttribute(attr, value)` применяет атрибут ко всем ячейкам в текущем выделении. Любой новый атрибут нужно зарегистрировать в `TableCell.extend()` и `TableHeader.extend()`.

### Что поддерживает TipTap из коробки
- `setCellAttribute('align', 'left'|'center'|'right'|'justify')` — горизонтальное выравнивание
- `setCellAttribute('colwidth', [120])` — ширина колонки (массив чисел)
- Для всего остального нужны **кастомные атрибуты** + рендер через `renderHTML`

### Ограничения
- `rowspan`/`colspan` управляются через `mergeCells()`/`splitCell()`, не через `setCellAttribute`
- Высота строки нет встроенного механизма — реализуется через `style="height:Xpx"` на ячейке
- Ориентация текста через CSS `writing-mode` — работает в браузере, нужна отдельная обработка при DOCX-экспорте

---

## Этапы реализации

---

## Этап 1. Расширение модели ячейки

**Файл:** `frontend/src/components/template-designer/extensions/TableCellExtension.ts` (новый)

Создать расширенные версии `TableCell` и `TableHeader` с дополнительными атрибутами:

```typescript
// Атрибуты кастомного TableCell
interface CellAttrs {
  // Стандартные (уже есть)
  colspan: number
  rowspan: number
  colwidth: number[] | null
  align: 'left' | 'center' | 'right' | 'justify' | null

  // Новые
  verticalAlign: 'top' | 'middle' | 'bottom' | null
  backgroundColor: string | null         // hex: '#ffffff'
  textOrientation: 'horizontal' | 'rotate-90' | 'rotate-180' | 'rotate-270' | null
  borderTop: string | null               // shorthand: '1px solid #000'
  borderRight: string | null
  borderBottom: string | null
  borderLeft: string | null
  minHeight: number | null               // px
}
```

**Маппинг CSS:**

| Атрибут | CSS-свойство |
|---------|-------------|
| `verticalAlign: 'top'` | `vertical-align: top` |
| `verticalAlign: 'middle'` | `vertical-align: middle` |
| `verticalAlign: 'bottom'` | `vertical-align: bottom` |
| `backgroundColor` | `background-color: #hex` |
| `textOrientation: 'horizontal'` | `writing-mode: horizontal-tb` |
| `textOrientation: 'rotate-90'` | `writing-mode: vertical-rl` |
| `textOrientation: 'rotate-270'` | `writing-mode: vertical-lr` |
| `textOrientation: 'rotate-180'` | `writing-mode: horizontal-tb; transform: rotate(180deg)` |
| `borderTop/Right/Bottom/Left` | `border-top/right/bottom/left: значение` |
| `minHeight` | `min-height: Npx` (на `td`) |

**Замена в TemplateDesigner.tsx:**
```typescript
// Было:
import { TableCell } from '@tiptap/extension-table-cell'
import { TableHeader } from '@tiptap/extension-table-header'

// Станет:
import { ExtendedTableCell, ExtendedTableHeader } from './extensions/TableCellExtension'
```

---

## Этап 2. Расширение TableBubbleMenu

**Файл:** `frontend/src/components/template-designer/TableBubbleMenu.tsx` (изменить)

### 2а. Новые группы кнопок

Панель разбить на вкладки или секции:

```
[Строки/Колонки] [Ячейка] [Границы]
```

#### Секция «Ячейка» — новые контролы

**Вертикальное выравнивание (3 кнопки-иконки):**
```
[↑ Сверху] [↕ По центру] [↓ Снизу]
```
- `VerticalAlignTopOutlined`, `VerticalAlignMiddleOutlined`, `VerticalAlignBottomOutlined`
- `editor.chain().focus().setCellAttribute('verticalAlign', 'top').run()`

**Ориентация текста (4 кнопки):**
```
[→ Горизонтально] [↓ 90°] [← 180°] [↑ 270°]
```
Иконки: `AlignLeftOutlined` + текст `A` с поворотом через CSS `transform: rotate(Ndeg)` или кастомные SVG.
- `editor.chain().focus().setCellAttribute('textOrientation', 'horizontal').run()`
- `editor.chain().focus().setCellAttribute('textOrientation', 'rotate-90').run()`
- …

**Цвет фона:**
```
[ColorPicker] Фон ячейки
```
- Ant Design `ColorPicker` → `setCellAttribute('backgroundColor', hex)`

#### Секция «Границы»

**Пресеты (выпадающий список или группа кнопок):**

| Пресет | Действие |
|--------|---------|
| Все границы | border: 1px solid #000 на всех сторонах |
| Без границ | Снять все borders |
| Только внешние | border только по периметру таблицы |
| Только внутренние | borders только между ячейками |
| Жирные границы | border: 2px solid #000 |

**Настройка вручную:**
- `Select` стиль линии: Сплошная / Пунктир / Точки / Двойная / Нет
- `InputNumber` толщина: 1–4 px
- `ColorPicker` цвет границы
- Кнопки выбора стороны: ☐ Все / Верхняя / Правая / Нижняя / Левая

#### Секция «Размер»

**Ширина колонки:**
```
Ширина: [___] px  [Применить]
```
- `InputNumber` → `setCellAttribute('colwidth', [value])`
- Сброс: `setCellAttribute('colwidth', null)` — авто

**Минимальная высота строки:**
```
Высота: [___] px  [Применить]
```
- `InputNumber` → `setCellAttribute('minHeight', value)`

### 2б. Улучшение UX панели

Текущая панель — узкая полоска над редактором. Предложение:

**Вариант А (рекомендован):** Ant Design `Tabs` внутри панели

```
[Структура] [Форматирование] [Границы]
```

**Вариант Б:** Выпадающий Popover при клике правой кнопкой (расширить контекстное меню для таблицы)

---

## Этап 3. Стили в редакторе

**Файл:** `frontend/src/components/template-designer/TemplateDesigner.css`

```css
/* --- Resize-handle CSS уже применён (исправление 2026-04-10) ---
   Находится в TemplateDesigner.css:
   - td/th { position: relative }
   - .column-resize-handle — синяя полоска-разделитель
   - .ProseMirror.resize-cursor * { cursor: col-resize }
   - .column-resize-dragging { background: #bae0ff }
   ------------------------------------------------------------ */

/* Ориентация текста (добавить вместе с Этапом 1) */
.tiptap-page table td[data-text-orientation="rotate-90"],
.tiptap-page table th[data-text-orientation="rotate-90"] {
  writing-mode: vertical-rl;
}

.tiptap-page table td[data-text-orientation="rotate-270"],
.tiptap-page table th[data-text-orientation="rotate-270"] {
  writing-mode: vertical-lr;
}

.tiptap-page table td[data-text-orientation="rotate-180"],
.tiptap-page table th[data-text-orientation="rotate-180"] {
  writing-mode: horizontal-tb;
  transform: rotate(180deg);
}
```

> **Примечание:** `verticalAlign`, `backgroundColor`, `borderTop/Right/Bottom/Left`, `minHeight`
> применяются через inline-стили в `renderHTML` расширенного TableCell — отдельный CSS не нужен.

---

## Этап 4. Экспорт DOCX

**Файл:** `backend/src/main/java/com/docforge/export/DocxExportServiceImpl.java`

Расширить метод `renderTable()` для поддержки новых атрибутов ячейки:

### 4а. Вертикальное выравнивание

```java
// OOXML: CTTcPr → CTVerticalJc
STVerticalJc.Enum vAlign = switch (verticalAlign) {
    case "top"    -> STVerticalJc.TOP;
    case "bottom" -> STVerticalJc.BOTTOM;
    default       -> STVerticalJc.CENTER;
};
tcPr.addNewVAlign().setVal(vAlign);
```

### 4б. Цвет фона ячейки

```java
// CTTcPr → CTShd
CTShd shd = tcPr.addNewShd();
shd.setVal(STShd.CLEAR);
shd.setColor("auto");
shd.setFill(backgroundColor.replace("#", "").toUpperCase());
```

### 4в. Ориентация текста

```java
// CTTcPr → CTTextDirection
STTextDirection.Enum dir = switch (textOrientation) {
    case "rotate-90"  -> STTextDirection.BT_LR;  // снизу вверх
    case "rotate-270" -> STTextDirection.TB_RL;  // сверху вниз
    case "rotate-180" -> STTextDirection.LR_TB;  // горизонтально перевёрнутое
    default           -> STTextDirection.LR_TB;
};
tcPr.addNewTextDirection().setVal(dir);
```

> **Примечание:** DOCX поддерживает `tbRl` (top→bottom, right→left) и `btLr` (bottom→top, left→right). Поворот на 180° в DOCX нативно не поддерживается — заменяется обычным горизонтальным при экспорте.

### 4г. Границы ячеек

```java
// CTTcPr → CTTcBorders
CTTcBorders borders = tcPr.addNewTcBorders();
applyCellBorder(borders.addNewTop(), borderTop);
applyCellBorder(borders.addNewRight(), borderRight);
applyCellBorder(borders.addNewBottom(), borderBottom);
applyCellBorder(borders.addNewLeft(), borderLeft);

private void applyCellBorder(CTBorder border, String css) {
    if (css == null || css.equals("none")) {
        border.setVal(STBorder.NIL);
        return;
    }
    // Парсинг "1px solid #000"
    border.setVal(STBorder.SINGLE);
    border.setSz(BigInteger.valueOf(parsePxToEighthPt(css)));
    border.setColor(parseColor(css));
}
```

### 4д. Минимальная высота строки

```java
// XWPFTableRow → CTRow → CTTrPr → CTHeight
CTTrPr trPr = row.getCTRow().addNewTrPr();
CTHeight height = trPr.addNewTrHeight();
height.setVal(BigInteger.valueOf(minHeight * 15L)); // twips: px * 15 ≈ pt
height.setHRule(STHeightRule.AT_LEAST);
```

---

## Этап 5. Экспорт PDF

**Файл:** `backend/src/main/java/com/docforge/export/PdfExportServiceImpl.java`

Все новые атрибуты передаются через inline CSS `style=...` на `<td>`/`<th>`:

```java
// В методе renderTable() при генерации ячейки:
String style = buildCellStyle(cellAttrs);
sb.append("<td style=\"").append(style).append("\">");

private String buildCellStyle(Map<String, Object> attrs) {
    StringBuilder s = new StringBuilder();
    // Вертикальное выравнивание
    if (attrs.get("verticalAlign") != null)
        s.append("vertical-align:").append(attrs.get("verticalAlign")).append(";");
    // Фон
    if (attrs.get("backgroundColor") != null)
        s.append("background-color:").append(attrs.get("backgroundColor")).append(";");
    // Ориентация текста
    String orient = (String) attrs.get("textOrientation");
    if ("rotate-90".equals(orient))  s.append("writing-mode:vertical-rl;");
    if ("rotate-270".equals(orient)) s.append("writing-mode:vertical-lr;");
    if ("rotate-180".equals(orient)) s.append("writing-mode:horizontal-tb;transform:rotate(180deg);");
    // Границы
    if (attrs.get("borderTop")    != null) s.append("border-top:").append(attrs.get("borderTop")).append(";");
    if (attrs.get("borderRight")  != null) s.append("border-right:").append(attrs.get("borderRight")).append(";");
    if (attrs.get("borderBottom") != null) s.append("border-bottom:").append(attrs.get("borderBottom")).append(";");
    if (attrs.get("borderLeft")   != null) s.append("border-left:").append(attrs.get("borderLeft")).append(";");
    // Высота
    if (attrs.get("minHeight") != null)
        s.append("min-height:").append(attrs.get("minHeight")).append("px;");
    return s.toString();
}
```

---

## Сводная таблица файлов

| Файл | Действие | Этап |
|------|---------|------|
| `extensions/TableCellExtension.ts` | Создать — расширенный TableCell и TableHeader | 1 |
| `TemplateDesigner.tsx` | Заменить TableCell/TableHeader на расширенные | 1 |
| `PreviewModal.tsx` | Заменить TableCell/TableHeader | 1 |
| `TableBubbleMenu.tsx` | Расширить UI: вертикальное выравнивание, ориентация, фон, границы, размер | 2 |
| `TemplateDesigner.css` | Стили для writing-mode, column-resize-handle | 3 |
| `DocxExportServiceImpl.java` | Поддержка новых атрибутов при DOCX-генерации | 4 |
| `PdfExportServiceImpl.java` | Inline CSS для новых атрибутов | 5 |

---

## Ограничения и решения

| Ограничение | Решение |
|-------------|---------|
| Поворот 180° в DOCX не поддерживается | При экспорте заменяется горизонтальным текстом, в editor работает через CSS `transform` |
| `setCellAttribute` применяет attr ко всем выделенным ячейкам | Ожидаемое поведение — стандарт (как в Word) |
| `writing-mode` сужает ячейку при коротком тексте | Задать `min-height` / `min-width` на ячейке |
| Пресеты границ «только внешние / только внутренние» | Требуют обхода всех ячеек таблицы — реализовать как отдельную команду ProseMirror |
| Высота строки: TipTap не имеет rowHeight attr | Хранится как `minHeight` на каждой ячейке строки |

---

## Порядок выполнения

```
Этап 1: TableCellExtension (модель) — основа для всего остального
    └── Этап 2: TableBubbleMenu UI (читает/пишет новые attrs)
        └── Этап 3: CSS-стили (визуальная часть в редакторе)

Параллельно с 1–3:
    Этап 4: DOCX-экспорт (backend)
    Этап 5: PDF-экспорт (backend)
```

**Агент:** `/frontend` — этапы 1, 2, 3  
**Агент:** `/backend` — этапы 4, 5

---

## Пример итогового UX панели TableBubbleMenu

```
┌─────────────────────────────────────────────────────────────────────────┐
│ [↑строка][↓строка][✕строка] │ [←кол][→кол][✕кол] │ [⊞слить][⊟разбить] │
├──────────────────────────────────────────────────────────────────────────┤
│ Верт.: [↑][↕][↓]  Ориентация: [→][↓][←][↑]  Фон: [■ColorPicker]        │
├──────────────────────────────────────────────────────────────────────────┤
│ Границы: [Все][Нет][Внешн.][Внутр.]  Стиль: [══▾]  px:[1▾]  цвет:[■]  │
├──────────────────────────────────────────────────────────────────────────┤
│ Ширина кол.: [____] px    Высота стр.: [____] px           [✕ таблица]  │
└─────────────────────────────────────────────────────────────────────────┘
```
