
# Plan: условия в вариантах формулировок + множественные формулировки через разделитель

## Контекст и диагностика

### Баг 1 — условия не работают

`PhrasingVariantEditor.tsx` содержит перевёрнутую логику кнопки:

```tsx
// Текущий код — кнопка никогда не появляется для варианта без условия
{conditionLabel == null && !isDefault && (     // isDefault = condition == null
  <Button>Добавить условие</Button>            // условие невыполнимо: null && null == false
)}
```

`isDefault` — `true` когда `condition == null`, тогда `!isDefault == false`, и кнопка не рендерится.
Нужно: показывать кнопку именно когда нет условия (т.е. убрать guard `!isDefault`).

### Фича 2 — множественные формулировки

Текущая нода `phrasingsRef` хранит одну ссылку (`phrasingsId: string`).
Нужно: выбирать несколько формулировок, соединять их через разделитель.

---

## Архитектурные решения

### Модель данных (фронт → бэк)

Добавляем в атрибуты TipTap-ноды `phrasingsRef` три новых поля, сохраняя старые для
обратной совместимости:

```
Было:                           Стало (расширение):
phrasingsId:  string            phrasingsIds:  string[]   (упорядоченный список)
displayName:  string            displayNames:  string[]   (для отображения тега)
                                separator:     string     (, | ; | пробел | \n | custom)

// legacy-поля остаются — ноды в старых шаблонах продолжают работать
phrasingsId:  string  →  fallback: если phrasingsIds пуст, читаем [phrasingsId]
displayName:  string  →  fallback: если displayNames пуст, читаем [displayName]
```

**Без изменения Java-модели** (`Phrasing`, `PhrasingVariant` не трогаем).
`DocumentGenerator` расширяется только в логике разворачивания `phrasingsRef`.

### Разделитель — предустановки

```
label          value (хранится как есть в separator attr)
─────────────────────────────────────────────────────────
запятая        ", "
точка с зап.   "; "
пробел         " "
тире           " — "
новая строка   "\n"     (рендерится как hardBreak-нода в TipTap JSON)
[другой...]    любая строка из Input
```

---

## Фаза 1 — Исправление условий (минимальная правка)

### 1.1 `PhrasingVariantEditor.tsx`

Убрать `&& !isDefault` из строки 105:

```tsx
// Было:
{conditionLabel == null && !isDefault && (
  <Button ... >Добавить условие</Button>
)}

// Стало:
{conditionLabel == null && (
  <Button ... >Добавить условие</Button>
)}
```

Семантика не меняется: вариант без условия остаётся "умолчанием" (тег «Умолчание» показывается).
Как только пользователь добавляет условие — тег исчезает, вариант становится условным.

---

## Фаза 2 — Множественные формулировки

### 2.1 Frontend — TipTap ноды

#### `PhrasingsRefNode.tsx` (дизайнер)

```typescript
// Новые атрибуты:
phrasingsIds:  { default: [], parseHTML: el => JSON.parse(el.getAttribute('data-phrasings-ids') || '[]'), renderHTML: attrs => ({ 'data-phrasings-ids': JSON.stringify(attrs.phrasingsIds) }) }
displayNames:  { default: [], parseHTML: ..., renderHTML: ... }
separator:     { default: ', ', parseHTML: el => el.getAttribute('data-separator') ?? ', ', renderHTML: attrs => ({ 'data-separator': attrs.separator }) }

// Legacy (без изменений — parseHTML/renderHTML уже есть):
phrasingsId:   { default: '' }
displayName:   { default: 'Формулировка' }
```

Вид тега в `PhrasingsRefView`:
```tsx
// Вычислить актуальный список (новый формат или fallback на legacy)
const ids   = phrasingsIds?.length ? phrasingsIds : (phrasingsId ? [phrasingsId] : [])
const names = displayNames?.length ? displayNames  : (displayName ? [displayName] : [])
// Показать: "📝 Формулировка А, Формулировка Б" или "📝 Формулировка А"
```

Команда `insertPhrasingsRef` принимает новый тип:
```typescript
insertPhrasingsRef: (attrs: {
  phrasingsIds: string[]
  displayNames: string[]
  separator: string
  // legacy compat:
  phrasingsId?: string
  displayName?: string
}) => ReturnType
```

#### `WorkspacePhrasingsRefNode.tsx` (заполнение)

Тот же legacy-fallback. Render:
```tsx
const allIds = phrasingsIds?.length ? phrasingsIds : (phrasingsId ? [phrasingsId] : [])

const parts: string[] = []
for (const id of allIds) {
  const phrasing = phrasings.find(p => p.id === id)
  const variant  = phrasing?.variants.find(v => conditionMatches(v, answers))
            ?? phrasing?.variants[phrasing.variants.length - 1]
  if (variant?.content?.length) {
    parts.push(renderInlineContent(variant.content, answers, parameters))
  }
}

const actualSep = separator === '\\n' ? '\n' : separator
const text = parts.join(actualSep)
```

Для `\n` в JSX рендерим `parts.join('\n')` через `white-space: pre-line` или разбиваем на `<>text<br/>text</>`.

### 2.2 Frontend — UI компоненты

#### Новый компонент `PhrasingsRefInsertModal.tsx`

```
┌─ Вставить формулировки ─────────────────────────────────────────┐
│  □ Формулировка А        (2 варианта)                            │
│  ☑ Формулировка Б        (3 варианта)                            │
│  ☑ Формулировка В        (1 вариант)                             │
│                                                                   │
│  Разделитель:  [Запятая ▼]  Preview: "Форм. Б, Форм. В"         │
│                ○ , ○ ; ○ пробел ○ тире ○ абзац ● Другой: [____] │
│                                                                   │
│                              [ Отмена ]  [ Вставить ]            │
└───────────────────────────────────────────────────────────────────┘
```

Props: `open`, `phrasings`, `onInsert(phrasingsIds, displayNames, separator)`, `onClose`

#### `PhrasingsPanel.tsx`

Кнопка "Вставить" теперь открывает `PhrasingsRefInsertModal` с
pre-selected только этой формулировкой (быстрый путь для одиночной вставки).

#### `EditorToolbar.tsx`

Dropdown "Формулировки" — каждый пункт открывает `PhrasingsRefInsertModal`
с pre-selected одной формулировкой (пользователь может доснять/добавить другие).
Либо: вставлять одиночную сразу с `separator=", "`, без модала.

> **Рекомендация:** через toolbar вставлять без модала (быстрый путь), через
> PhrasingsPanel — с модалом (полный контроль). Это наименее раздражающий UX.

### 2.3 Backend — `DocumentGenerator.java`

В блоке `case "phrasingsRef"`:

```java
// Новый метод: читает phrasingsIds или legacy phrasingsId
List<String> ids = getPhrasingsIdsList(attrs);   // new helper
String separator = (String) attrs.getOrDefault("separator", ", ");

List<Map<String, Object>> result = new ArrayList<>();
boolean first = true;
for (String phrasingsId : ids) {
    Phrasing phrasing = phrasings.stream()
        .filter(p -> phrasingsId.equals(p.getId()))
        .findFirst().orElse(null);
    if (phrasing == null || phrasing.getVariants() == null) continue;

    PhrasingVariant variant = phrasing.getVariants().stream()
        .filter(v -> evalPhrasingCondition(v.getCondition(), answers))
        .findFirst().orElse(null);
    if (variant == null || variant.getContent() == null || variant.getContent().isEmpty()) continue;

    if (!first) {
        result.add(makeSeparatorNode(separator));  // text или hardBreak
    }
    result.addAll(variant.getContent());
    first = false;
}
return result;
```

```java
// Новый helper: getPhrasingsIdsList
@SuppressWarnings("unchecked")
private List<String> getPhrasingsIdsList(Map<String, Object> attrs) {
    Object ids = attrs.get("phrasingsIds");
    if (ids instanceof List<?> list && !list.isEmpty()) {
        return list.stream().map(Object::toString).collect(Collectors.toList());
    }
    // Legacy fallback
    String single = (String) attrs.get("phrasingsId");
    return single != null ? List.of(single) : List.of();
}

// Разделитель → нода
private Map<String, Object> makeSeparatorNode(String separator) {
    if ("\\n".equals(separator) || "\n".equals(separator)) {
        return Map.of("type", "hardBreak");
    }
    return textNode(separator);
}
```

### 2.4 Backend — `DocxExportServiceImpl.java`

В `renderInline()` case `phrasingsRef`:
- Читать `phrasingsIds` или legacy `phrasingsId`
- Показывать `[Форм. А, Форм. Б]` вместо `[displayName]`

### 2.5 `previewRenderer.ts`

В case `phrasingsRef`:
```typescript
const allIds = (node.attrs?.phrasingsIds as string[] | undefined)?.length
  ? node.attrs!.phrasingsIds as string[]
  : node.attrs?.phrasingsId ? [node.attrs.phrasingsId as string] : []

const sep = (node.attrs?.separator as string | undefined) ?? ', '

const parts: JSONContent[][] = []
for (const phrasingsId of allIds) {
  const phrasing = phrasings?.find(p => p.id === phrasingsId)
  if (!phrasing) continue
  const variant = phrasing.variants.find(v =>
    !v.condition || (v.condition.type === 'SIMPLE'
      ? evaluateSimple(v.condition.expression ?? '', answers)
      : true)
  ) ?? phrasing.variants[phrasing.variants.length - 1]
  if (!variant?.content?.length) continue
  parts.push(flatMap(variant.content as JSONContent[], child =>
    renderNode(child, answers, parameters, clauseLabels, showAll, phrasings)))
}

// Соединить через разделитель
const sepNode: JSONContent = (sep === '\n' || sep === '\\n')
  ? { type: 'hardBreak' }
  : { type: 'text', text: sep }

const merged: JSONContent[] = []
parts.forEach((part, i) => {
  if (i > 0) merged.push(sepNode)
  merged.push(...part)
})
return { type: '__fragment__', content: merged }
```

---

## Затронутые файлы

| Файл | Изменение |
|------|-----------|
| `PhrasingVariantEditor.tsx` | убрать `!isDefault` (1 строка) |
| `extensions/PhrasingsRefNode.tsx` | добавить attrs + обновить команду + view |
| `WorkspacePhrasingsRefNode.tsx` | итерировать phrasingsIds + legacy fallback |
| `previewRenderer.ts` | итерировать phrasingsIds + legacy + разделитель |
| `PhrasingsPanel.tsx` | кнопка "Вставить" открывает modal |
| `EditorToolbar.tsx` | toolbar insert без модала (без изменений) |
| `PhrasingsRefInsertModal.tsx` | **новый файл** |
| `DocumentGenerator.java` | getPhrasingsIdsList + makeSeparatorNode |
| `DocxExportServiceImpl.java` | читать phrasingsIds в preview-placeholder |

---

## Порядок реализации

1. **Фаза 1** — исправить условия (1 строка, минимальный риск)
2. **Фаза 2a** — расширить TipTap ноды (`PhrasingsRefNode`, `WorkspacePhrasingsRefNode`)
3. **Фаза 2b** — `previewRenderer.ts`
4. **Фаза 2c** — `PhrasingsRefInsertModal.tsx` + обновить `PhrasingsPanel`
5. **Фаза 2d** — `DocumentGenerator.java` + `DocxExportServiceImpl.java`

Каждая фаза независима и тестируема отдельно.
