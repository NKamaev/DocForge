# План: расширенное форматирование абзацев и реактивная панель

> **Дата:** 2026-04-10
> **Агенты:** `/frontend`
> **Статус:** готово к реализации

---

## Цель

Добавить в редактор шаблонов:

1. **Интервалы перед / после абзаца** (`margin-top` / `margin-bottom` в pt)
2. **Отступы абзаца слева / справа** (`padding-left` / `padding-right` в мм, в дополнение к существующим ступенчатым indent-level)
3. **Отступ первой строки** (`text-indent` в мм)
4. **Стиль нумерации = стиль текста** — маркер номера в `NumberedClauseNode` наследует шрифт, размер и начертание первого текстового фрагмента строки
5. **Реактивная панель** — при установке курсора или выделении в toolbar корректно отображаются текущий шрифт, размер и межстрочный интервал (сейчас не работает)

---

## 1. Диагностика текущих проблем

### 1.1 Нереактивная toolbar

`EditorToolbar` получает `editor` как prop. Значения `currentFontFamily`, `currentFontSize`, `currentLineSpacing` вычисляются при рендере через `editor.getAttributes()`. Проблема: компонент не перерисовывается при смене выделения, т.к. ссылка на объект `editor` не меняется — React не знает, что нужен новый рендер.

**Корень:** нет подписки на `selectionUpdate` / `transaction` внутри `EditorToolbar`.

### 1.2 Маркер нумерации

`NumberedClauseView` рендерит `<span contentEditable={false}>` с жёстко заданным `fontWeight: 600, color: '#595959'`. Он не читает атрибуты `textStyle` из соседнего контента.

### 1.3 Отсутствующие атрибуты абзаца

Нет атрибутов `spaceBefore`, `spaceAfter`, `firstLineIndent`, `marginRight` на типах `paragraph` / `heading` / `numberedClause`.

---

## 2. Файлы для изменения / создания

| Файл | Действие |
|------|----------|
| `extensions/ParagraphSpacingExtension.ts` | **создать** — новые атрибуты + команды |
| `extensions/NumberedClauseNode.tsx` | **изменить** — наследование стиля маркера |
| `EditorToolbar.tsx` | **изменить** — реактивность + UI новых атрибутов |
| `TemplateDesigner.tsx` | **изменить** — подключить новое расширение |

Бэкенд и модели данных **не затрагиваются**: новые атрибуты хранятся в JSON-теле документа (MongoDB `body`) автоматически через TipTap-сериализацию.

---

## 3. Задачи для `/frontend`

### Задача 1 — `ParagraphSpacingExtension.ts`

Создать `frontend/src/components/template-designer/extensions/ParagraphSpacingExtension.ts`:

```
Атрибуты (на типах paragraph, heading, numberedClause):
  spaceBefore:      string | null  → margin-top: Xpt
  spaceAfter:       string | null  → margin-bottom: Xpt
  firstLineIndent:  string | null  → text-indent: Xmm
  marginRight:      string | null  → margin-right: Xmm
  marginLeft:       string | null  → margin-left: Xmm  (точное значение, дополняет IndentExtension)
```

Команды:
```typescript
setParagraphSpacing(opts: {
  spaceBefore?: string
  spaceAfter?: string
  firstLineIndent?: string
  marginLeft?: string
  marginRight?: string
}) => ReturnType
```

`renderHTML` применяет стили inline. `parseHTML` читает из `element.style`.

> **Важно:** `marginLeft` из `ParagraphSpacingExtension` и `indent` из `IndentExtension` — независимые атрибуты. В `renderHTML` их нужно объединить: итоговый `margin-left = indent * 2em + marginLeft`. Это решается в `IndentExtension.renderHTML` (читает `paragraphMarginLeft` из того же узла) **или** оба атрибута рендерятся независимо, а CSS суммирует через `calc()`.
>
> Проще: `ParagraphSpacingExtension.marginLeft` заменяет IndentExtension-шаги для точного позиционирования. Оба атрибута живут на одном узле — toolbar показывает их раздельно.

---

### Задача 2 — Реактивность `EditorToolbar`

Внутри компонента добавить:

```typescript
const [, forceUpdate] = useReducer(x => x + 1, 0)
useEffect(() => {
  editor.on('selectionUpdate', forceUpdate)
  editor.on('transaction', forceUpdate)
  return () => {
    editor.off('selectionUpdate', forceUpdate)
    editor.off('transaction', forceUpdate)
  }
}, [editor])
```

После этого `currentFontFamily`, `currentFontSize`, `currentLineSpacing` будут вычисляться при каждом изменении выделения.

Также расширить чтение `currentLineSpacing`, чтобы учитывать все три типа узлов (`paragraph`, `heading`, `numberedClause`) — это уже есть, но нужно убедиться, что читается и для cursor (не только selection range).

---

### Задача 3 — UI новых атрибутов в `EditorToolbar`

Добавить новую секцию «Абзац» после секции «Интерл.»:

```
[▲ Перед: ___pt] [▼ После: ___pt] | [← Слева: ___мм] [→ Справа: ___мм] | [⇥ 1-я строка: ___мм]
```

Реализация: Ant Design `InputNumber` с шириной 60-70px, `size="small"`. Значения читаются из `editor.getAttributes('paragraph')` (или `heading`/`numberedClause`) — тот же принцип, что и `currentLineSpacing`. Изменения применяются командой `setParagraphSpacing(...)`.

Примерные значения по умолчанию: `spaceBefore = 0`, `spaceAfter = 0`, `firstLineIndent = null`, `marginLeft/Right = null`.

---

### Задача 4 — Наследование стиля в `NumberedClauseNode`

В `NumberedClauseView` маркер (`<span contentEditable={false}>`) должен применять стиль первого текстового узла контента.

Способ: в `NumberedClauseView` читать `node.firstChild` → искать первый `text` node с marks → извлекать `textStyle` marks → применять к span:

```typescript
function getFirstTextStyle(node: PmNode): React.CSSProperties {
  let style: React.CSSProperties = {}
  node.descendants((child) => {
    if (child.isText && child.marks.length > 0) {
      const textStyleMark = child.marks.find(m => m.type.name === 'textStyle')
      if (textStyleMark) {
        if (textStyleMark.attrs.fontFamily) style.fontFamily = textStyleMark.attrs.fontFamily
        if (textStyleMark.attrs.fontSize)   style.fontSize   = textStyleMark.attrs.fontSize
      }
      return false // stop after first text
    }
  })
  return style
}
```

Применяется к `<span>`:
```tsx
<span contentEditable={false} style={{ ...markerBaseStyle, ...getFirstTextStyle(node) }}>
  {label}
</span>
```

Базовый стиль маркера: убрать жёсткий `fontWeight: 600, color: '#595959'` (или сохранить цвет, но убрать фиксированный размер). Жирность маркера должна соответствовать тексту.

---

## 4. Последовательность реализации

```
1. ParagraphSpacingExtension.ts           ← новый файл
2. TemplateDesigner.tsx                   ← добавить в buildExtensions()
3. EditorToolbar.tsx — реактивность       ← useEffect + forceUpdate
4. EditorToolbar.tsx — UI абзацных пар.   ← новые InputNumber-контролы
5. NumberedClauseNode.tsx — стиль маркера ← getFirstTextStyle()
```

Каждый шаг независим, можно делать последовательно. Шаг 3 разблокирует проверку шагов 1 и 4 (иначе значения не будут обновляться при навигации).

---

## 5. Проверка

| Сценарий | Ожидаемое поведение |
|----------|---------------------|
| Курсор в параграфе с Arial 12 | Toolbar показывает Arial, 12 |
| Выделить заголовок Times 16 | Toolbar показывает Times New Roman, 16 |
| Установить `spaceBefore = 6pt` | Параграф получает `margin-top: 6pt` в HTML |
| Установить `firstLineIndent = 10мм` | `text-indent: 10mm` на параграфе |
| Нумерованный пункт с жирным текстом | Маркер «1.» тоже жирный и того же шрифта |
| Нумерованный пункт с курсивом 10pt | Маркер «1.» — курсив, 10pt |

---

## 6. Ограничения / не входит в план

- Интервалы перед/после для `listItem` (маркированные/нумерованные списки TipTap) — отдельная задача
- Экспорт DOCX: новые атрибуты нужно будет отдельно прокинуть в DOCX-генератор (задача для `/backend`)
- Именованные стили абзацев («Обычный», «Заголовок 1» и т.д.) — вне MVP
