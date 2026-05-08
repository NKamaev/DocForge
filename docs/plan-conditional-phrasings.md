# План: условный блок, условный текст на пунктах, формулировки

---

## Задача 1 — Убрать «Условие» (ConditionalBlock)

### Анализ

`ConditionalBlockNode` — блочный нод (`group: 'block'`, `content: 'block+'`), визуально занимает много места (фиолетовая рамка, хедер с иконкой, кнопки). Дублирует `ConditionalInlineNode` по смыслу.

**Ключевое ограничение**: существующие шаблоны могут содержать `conditionalBlock` ноды в `body`. Удалять их нельзя — нарушим уже созданные шаблоны. Поэтому расширение остаётся **зарегистрированным** в редакторе (для парсинга/рендера старых данных), но **UI вставки убирается**.

### Фронтенд (4 файла)

| Файл | Действие |
|------|---------|
| `EditorToolbar.tsx` | Удалить кнопку «Условие» (`BlockOutlined`, `insertConditionalBlock`) |
| `FloatingInsertMenu.tsx` | Удалить пункт «Условный блок» если есть |
| `SlashCommandExtension.ts` | Удалить команду `/условие` / `/condition` если есть |
| `ConditionalBlockNode.tsx` | Убрать `insertConditionalBlock` из `addCommands()` (нод остаётся для рендера) |

**Бэкенд**: изменений нет. `DocumentGenerator.java` / `TipTapTreeWalker.java` продолжают обрабатывать `conditionalBlock` ноды из старых шаблонов.

---

## Задача 2 — Условный текст на нумерованных пунктах

### Анализ

`ConditionalInlineNode` — inline нод, работает внутри `<paragraph>` внутри `<numberedClause>`. Для выделения inline-текста внутри пункта работает корректно.

**Архитектурная задача**: пользователь хочет сделать **весь нумерованный пункт** условным. `ConditionalInline` для этого не подходит (он inline). `ConditionalBlock` убирается. Нужен другой механизм.

### Решение: атрибут `condition` на нумерованном пункте

Добавить атрибут `condition` в `NumberedClauseNode` и `WorkspaceNumberedClauseNode`. В дизайнере — контрол настройки условия в хедере пункта. В fill-странице — пункт скрывается если условие не выполнено.

#### Изменения атрибутов `numberedClause`

```
level: 1–5
numberingStyle: decimal | alpha-ru | alpha-en
listStart: bool
id: UUID
condition: { type: simple | python, expression: string } | null  ← НОВОЕ
```

### Фронтенд

| Файл | Действие |
|------|---------|
| `NumberedClauseNode.tsx` | Добавить атрибут `condition: null`. В `NumberedClauseView` — иконка условия в хедере. Клик → ConditionBuilder. |
| `WorkspaceNumberedClauseNode.tsx` | Читать `condition` из `node.attrs`; если `show = false` → скрыть нод (return null из рендера) |
| `previewRenderer.ts` | В `renderNode` для `numberedClause` — вычислять `show` по `node.attrs.condition`; если `false` → `return null` |
| `DocumentGenerator.java` / `TipTapTreeWalker.java` | Обработать `condition` у `numberedClause` при генерации документа |

---

## Задача 3 — «Формулировки» (Phrasings)

### Концепция

**Формулировка** — именованный список вариантов текста. Каждый вариант имеет условие показа и inline-содержимое (текст + параметры + ссылки на пункты). Вставляется в шаблон как нод-ссылка `phrasingsRef`. При рендере — показывается первый выполненный вариант.

```
Формулировка "Неустойка":
  Вариант 1: условие: has_penalty == true
             текст: "...уплачивает неустойку {penalty_percent}%..."
  Вариант 2: условие: (нет — дефолтный)
             текст: ""
```

### 3.1 Модель данных

#### Backend: новые Java классы в `com.docforge.template.model`

```java
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class Phrasing {
    private String id;
    private String name;
    private String description;
    private List<PhrasingVariant> variants;
}

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class PhrasingVariant {
    private String id;
    private Condition condition;    // null = дефолтный вариант
    // inline-контент: List<Map<String,Object>> — TipTap inline ноды
    private List<Map<String, Object>> content;
}
```

`Template.java` — добавить поле:
```java
private List<Phrasing> phrasings;  // null-совместимо со старыми шаблонами
```

`TemplateResponse` и `UpdateTemplateRequest` — добавить `List<Phrasing> phrasings`.

#### Frontend: новые типы в `types/templateModel.ts`

```typescript
export interface PhrasingVariant {
  id: string
  condition?: Condition | null   // null = дефолтный вариант
  content: JSONContent[]         // inline TipTap ноды
}

export interface Phrasing {
  id: string
  name: string
  description?: string | null
  variants: PhrasingVariant[]
}
```

### 3.2 TipTap нод: `PhrasingsRefNode` (дизайнер)

**Тип**: `inline` (вставляется в параграф, как `parameterField`)

**Атрибуты**:
```typescript
{
  phrasingsId: string        // UUID формулировки
  displayName: string        // кэшированное имя для отображения
}
```

**Рендер в дизайнере**: зелёный бейдж `[📝 Неустойка]` с кнопками «настроить» и «удалить».

**Рендер в fill-странице** (`WorkspacePhrasingsRefNode.tsx`):
- Читает `phrasingsId`, находит формулировку в `storage.phrasings`
- Вычисляет первый вариант с выполненным условием (или дефолт)
- Рендерит inline-содержимое варианта
- Подписывается на `answers-updated` для реактивного обновления

### 3.3 Frontend компоненты

```
frontend/src/components/template-designer/
├── PhrasingsPanel.tsx             ← список формулировок (таб в TemplateDesigner)
├── PhrasingEditorModal.tsx        ← создание/редактирование одной формулировки
├── PhrasingVariantEditor.tsx      ← редактор варианта (условие + мини-TipTap)
└── extensions/
    └── PhrasingsRefNode.tsx       ← TipTap нод для дизайнера

frontend/src/components/questionnaire/
└── WorkspacePhrasingsRefNode.tsx  ← TipTap нод для fill-страницы
```

**`PhrasingsPanel.tsx`** — таб рядом с «Параметры» и «Опросный лист»:
- Список карточек (имя + кол-во вариантов)
- «Добавить формулировку» → открывает `PhrasingEditorModal`
- «Редактировать» / «Удалить» на каждой карточке
- «Вставить в текст» → `editor.chain().focus().insertPhrasingsRef(...).run()`

**`PhrasingEditorModal.tsx`** — Modal Ant Design:
- Поле «Название»
- Список `PhrasingVariantEditor` (упорядоченный)
- Кнопка «Добавить вариант»
- Последний вариант без условия = дефолтный (UI подсказывает)

**`PhrasingVariantEditor.tsx`** — один вариант:
- `ConditionBuilder` для условия
- Мини-TipTap редактор (StarterKit + ParameterFieldNode + ClauseRefNode, `editable: true`)
- Кнопки «Удалить», «Переместить вверх/вниз»

### 3.4 EditorToolbar: кнопка «Формулировки»

Dropdown рядом с «Параметр»:
- Список формулировок из `DesignerContext.phrasings`
- Клик → `editor.chain().focus().insertPhrasingsRef({ phrasingsId, displayName }).run()`
- Если формулировок нет → «Нет формулировок (создайте в панели Формулировки)»

### 3.5 Рендер на fill-странице

**`renderPreview.ts`** — обработка `phrasingsRef`:
```typescript
if (node.type === 'phrasingsRef') {
  const phrasing = phrasings.find(p => p.id === node.attrs.phrasingsId)
  if (!phrasing) return null
  const variant = phrasing.variants.find(v =>
    !v.condition || evaluateSimple(v.condition.expression, answers)
  ) ?? phrasing.variants[phrasing.variants.length - 1]
  if (!variant?.content?.length) return null
  return {
    type: '__fragment__',
    content: variant.content.flatMap(n => {
      const r = renderNode(n, answers, parameters, clauseLabels, showAll)
      if (!r) return []
      if (r.type === '__fragment__') return (r as Fragment).content
      return [r as JSONContent]
    })
  }
}
```

`renderPreview` принимает дополнительный параметр `phrasings: Phrasing[]`.

`QuestionnairePage.tsx` — загружать и передавать `tmpl.phrasings` в `DocumentWorkspace`.

### 3.6 Бэкенд: генерация документа

**`TipTapTreeWalker.java`** — добавить обработку ноды `phrasingsRef`:
- Найти формулировку по `phrasingsId` в `template.getPhrasings()`
- Вычислить подходящий вариант через `ConditionEvaluator`
- Рекурсивно обработать `content` варианта как inline-ноды

---

## Последовательность реализации

```
Этап 1 — Быстрые правки (1–2 сессии):
  1.1  Убрать кнопку «Условие» из тулбара и slash-команд
  1.2  Добавить атрибут condition к NumberedClauseNode
  1.3  Условный рендер NumberedClause в fill-странице и previewRenderer

Этап 2 — Backend модель (1 сессия):
  2.1  Классы Phrasing + PhrasingVariant в Java
  2.2  Template.java + TemplateResponse + UpdateTemplateRequest
  2.3  TipTapTreeWalker.java — обработка phrasingsRef и condition на numberedClause

Этап 3 — Frontend нод и панель (2–3 сессии):
  3.1  PhrasingsRefNode.tsx (дизайнер)
  3.2  PhrasingsPanel.tsx + PhrasingEditorModal + PhrasingVariantEditor
  3.3  Подключение в TemplateDesigner (новый таб, сохранение)
  3.4  Кнопка в EditorToolbar

Этап 4 — Fill-страница (1 сессия):
  4.1  WorkspacePhrasingsRefNode.tsx
  4.2  renderPreview расширен для phrasingsRef + phrasings параметр
  4.3  QuestionnairePage + DocumentWorkspace — передача phrasings
```

---

## Граничные случаи

| Вопрос | Решение |
|--------|---------|
| Старые шаблоны с `conditionalBlock` | Нод остаётся зарегистрированным, рендерится; UI вставки убран |
| Вложенные `phrasingsRef` | Запрещены схемой мини-редактора вариантов |
| Формулировка удалена, нод остался в теле | В дизайнере — красный бейдж «не найдена»; в fill — `null` |
| Несколько выполненных условий в вариантах | Берётся первый подходящий (порядок вариантов важен) |
| Дефолтный вариант | Последний вариант с `condition === null` |
| Python-условия в вариантах | Вычисляются на бэкенде при генерации; на фронте — show by default |
| `condition` у пункта + `showAllConditionals` | `showAllConditionals` подавляет скрытие (как для остальных условий) |
