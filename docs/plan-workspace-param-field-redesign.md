# План: Redesign WorkspaceParameterFieldNode

## Цель

Изменить отображение параметра в рабочем пространстве документа:
- **Убрать** видимую подпись (название параметра) с чипа
- **Показывать** название параметра и страницу ОЛ во **всплывающей подсказке** (tooltip при наведении)
- **Растягивать** чип под ширину значения (убрать фиксированную ширину `<input>`)
- **Поддерживать многострочность**: если значение содержит переносы строк или не помещается, чип переносится по строкам

---

## Затронутые файлы

| Файл | Изменение |
|---|---|
| `frontend/src/components/questionnaire/WorkspaceParameterFieldNode.tsx` | Основные правки компонента |
| `frontend/src/components/questionnaire/DocumentWorkspace.tsx` | Передать `questionnaire` в `Props` → storage |
| `frontend/src/components/questionnaire/QuestionnairePage.tsx` | Передать `questionnaire` в `DocumentWorkspace` |

---

## Шаги

### Шаг 1 — Расширить `WorkspaceParamStorage`

В `WorkspaceParameterFieldNode.tsx` добавить поле `questionnaire` в интерфейс:

```ts
import type { Questionnaire } from '../../types/template'

export interface WorkspaceParamStorage {
  answers: Record<string, unknown>
  parameters: Parameter[]
  questionnaire: Questionnaire | null          // ← добавить
  onAnswerChange: ((paramCode: string, value: unknown) => void) | null
  onParamFocus: ((paramCode: string) => void) | null
  highlightedParam: string | null
}
```

В `addStorage()` добавить дефолт `questionnaire: null`.

---

### Шаг 2 — Вычислять страницу параметра внутри компонента

В `WorkspaceParameterFieldView` добавить вспомогательную функцию (или inline-логику):

```ts
function findParamPage(
  questionnaire: Questionnaire | null,
  paramCode: string
): { pageIndex: number; pageTitle: string } | null {
  if (!questionnaire) return null
  for (let i = 0; i < questionnaire.pages.length; i++) {
    const page = questionnaire.pages[i]
    if (page.questions.some(q => q.parameterCode === paramCode)) {
      return { pageIndex: i + 1, pageTitle: page.title }
    }
  }
  return null
}
```

---

### Шаг 3 — Переработать рендер `WorkspaceParameterFieldView`

**Что убрать:**
- Span с `display || parameterCode` (видимая подпись)

**Что добавить:**
- Ant Design `<Tooltip>` (из `antd`) вокруг всего чипа:
  ```tsx
  import { Tooltip } from 'antd'

  const pageInfo = findParamPage(storage?.questionnaire ?? null, parameterCode)
  const tooltipTitle = (
    <div>
      <div><strong>{param?.name || display || parameterCode}</strong></div>
      {pageInfo && <div style={{ color: '#aaa', fontSize: 11 }}>
        Стр. {pageInfo.pageIndex}: {pageInfo.pageTitle}
      </div>}
    </div>
  )
  ```

**Что изменить в отображении значения:**

Заменить `<input>` на `<span>` с текстом значения:
```tsx
<span
  style={{
    whiteSpace: 'pre-wrap',
    wordBreak: 'break-word',
    color: isEmpty ? '#bfbfbf' : 'inherit',
    cursor: 'text',
  }}
  onClick={handleLabelClick}   // клик → переход к вопросу в ОЛ
  onMouseDown={stopProp}
>
  {isEmpty
    ? `[${display || parameterCode}]`
    : inputVal
  }
</span>
```

> **Почему убираем `<input>`:**  
> `<input>` является однострочным элементом и не поддерживает `pre-wrap`. Переход на `<textarea>` в inline-контексте TipTap создаёт сложности с вёрсткой. Редактирование через ОЛ является основным и достаточным способом — чип остаётся кликабельным для навигации к вопросу.

**Обёртка чипа:**
```tsx
<NodeViewWrapper as="span" style={{ display: 'inline' }}>
  <Tooltip title={tooltipTitle} mouseEnterDelay={0.4}>
    <span
      contentEditable={false}
      style={{
        display: 'inline',
        background: bg,
        border: `1.5px solid ${borderColor}`,
        borderRadius: 4,
        padding: '1px 5px',
        fontSize: 'inherit',
        fontFamily: 'inherit',
        lineHeight: 1.5,
        transition: 'all 0.2s',
        boxShadow: isHighlighted ? '0 0 0 2px rgba(22,119,255,0.2)' : undefined,
        verticalAlign: 'baseline',
        whiteSpace: 'pre-wrap',
        wordBreak: 'break-word',
        cursor: 'pointer',
      }}
      onClick={handleLabelClick}
      onMouseDown={stopProp}
    >
      {isEmpty ? `[${display || parameterCode}]` : inputVal}
    </span>
  </Tooltip>
</NodeViewWrapper>
```

> Используем `display: inline` (не `inline-flex`) чтобы текст переносился вместе с окружающим текстом документа.

---

### Шаг 4 — Передать `questionnaire` в `DocumentWorkspace`

**`DocumentWorkspace.tsx`:**

1. Добавить в `Props`:
   ```ts
   questionnaire: Questionnaire | null
   ```
2. Добавить в `storage(editor)` при инициализации:
   ```ts
   s.questionnaire = questionnaire
   ```
3. Добавить `useEffect` для синхронизации (по аналогии с `answers`):
   ```ts
   useEffect(() => {
     if (!editor) return
     storage(editor).questionnaire = questionnaire
   }, [editor, questionnaire])
   ```

---

### Шаг 5 — Передать `questionnaire` из `QuestionnairePage`

В `QuestionnairePage.tsx` в JSX-рендере передать:
```tsx
<DocumentWorkspace
  ...
  questionnaire={questionnaire?.questionnaire ?? null}
/>
```

> `questionnaire` state имеет тип `Questionnaire` (из `document`), который уже содержит `.pages`.

---

### Шаг 6 — Убрать неиспользуемые обработчики

После удаления `<input>` из компонента:
- Удалить `handleInputChange`
- Удалить `parseAnswerValue`
- Убрать `inputVal` / `setInputVal` из useState — заменить на прямое вычисление отображаемого значения через `formatValue`

---

## Git

```
feat(workspace): param field chip — tooltip with name/page, auto-expand, multiline
```

**Что коммитим:**
- `WorkspaceParameterFieldNode.tsx` — переработка компонента
- `DocumentWorkspace.tsx` — prop + storage sync
- `QuestionnairePage.tsx` — передача questionnaire
