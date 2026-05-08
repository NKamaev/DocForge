# Plan: Автонумерация пунктов и улучшение вариативности

> Статус: готов к реализации  
> Архитектор: /architect  
> Дата: 2026-04-09

---

## Scope задачи

1. **Автонумерация** — нумерованные пункты с многоуровневой вложенностью (1 / 1.1 / 1.1.1 ...) в редакторе шаблонов
2. **Пересчёт нумерации** — в документе скрытые по вариативности пункты исключаются, номера пересчитываются автоматически
3. **Компактный условный блок** — `ConditionalBlockNode` в редакторе шаблонов получает коллапс-режим; визуально менее громоздкий
4. **Inline-вариативность** — новый нод для условного отображения произвольного куска текста (в том числе с параметрами) внутри абзаца

---

## Архитектурные решения

### A1. NumberedClauseNode — нумерованный пункт

**Нод**, не список (`bulletList`/`orderedList`). Причина: стандартный `orderedList`
не поддерживает произвольный уровень и пересчёт по условию.

```
group: 'block'
content: 'block+'
attrs: { level: 1..5 }
```

Номер пункта **не хранится в attrs** и **не сохраняется в JSON**. Он вычисляется
динамически ProseMirror-плагином и отображается через `Decoration.widget`.

Это означает:
- При сохранении в MongoDB JSON не содержит порядкового номера — только `level`.
- При рендере (редактор шаблонов, DocumentWorkspace) плагин обходит документ и
  присваивает декорации с правильными номерами.

### A2. AutoNumberingPlugin

ProseMirror-плагин (`addProseMirrorPlugins` в расширении `NumberedClauseNode`).

Алгоритм:
1. Обходит все ноды документа в порядке появления.
2. Для каждого `numberedClause` вычисляет счётчик своего уровня и сбрасывает
   счётчики дочерних уровней.
3. Формирует строку: `"1."`, `"1.1."`, `"1.1.1."` и т.д.
4. Добавляет `Decoration.widget` перед контентом нода.

В редакторе шаблонов: учитываются все нумерованные пункты, включая
находящиеся внутри `conditionalBlock`.

В DocumentWorkspace: плагин **дополнительно** принимает множество скрытых позиций
(из `editor.storage`). Пункты внутри скрытых `conditionalBlock`/`conditionalInline`
пропускаются при счёте.

### A3. ConditionalBlockNode — компактный режим

Добавляется атрибут `collapsed: boolean` (default: false).

- `collapsed = false` — текущее поведение, контент виден.
- `collapsed = true` — контент скрыт, шапка занимает одну строку (≈32px).
  Кнопка-стрелка для разворачивания/сворачивания.

Шапка переделывается: убирается `Typography.Text code` с выражением условия из
основного вида — вместо него используется `Tooltip` при наведении. Это сокращает
ширину шапки.

### A4. ConditionalInlineNode — inline-вариативность

Новый TipTap нод для условного отображения фрагмента текста внутри абзаца.

```
group: 'inline'
inline: true
content: 'inline*'        ← может содержать текст и parameter_field
atom: false
attrs: { condition, label }
```

Рендер: тонкая цветная подсветка фона + иконка условия в начале. При hover —
показывает условие в `Tooltip`. Кнопка удаления без разрушения контента
(unwrap → оставить внутренний текст).

В DocumentWorkspace: нод либо рендерит контент (условие истинно), либо рендерит
пустой span (условие ложно). Влияет на пересчёт нумерации.

---

## Затрагиваемые файлы

### Новые файлы
```
frontend/src/components/template-designer/extensions/
├── NumberedClauseNode.tsx          # нод + AutoNumberingPlugin
└── ConditionalInlineNode.tsx       # inline-вариативность

frontend/src/components/questionnaire/
└── WorkspaceNumberedClauseNode.tsx # workspace-версия нода (с пересчётом)
```

### Изменяемые файлы
```
frontend/src/components/template-designer/extensions/
└── ConditionalBlockNode.tsx        # добавить collapsed-режим

frontend/src/components/template-designer/
├── TemplateDesigner.tsx            # добавить новые extensions
└── EditorToolbar.tsx               # кнопки: нумерованный пункт (уровень 1-3), inline-условие

frontend/src/components/questionnaire/
├── DocumentWorkspace.tsx           # добавить WorkspaceNumberedClauseNode
└── WorkspaceParameterFieldNode.tsx # возможно, minor changes

frontend/src/utils/previewRenderer.ts  # учесть numberedClause и conditionalInline
```

### Без изменений
- Backend: модель не меняется — тело шаблона хранится как `Map<String, Object>`,
  новые ноды просто добавятся в JSON.
- PostgreSQL/MongoDB схемы: не меняются.
- Никаких Flyway-миграций не требуется.

---

## Этапы реализации

---

### Этап 1 — TipTap extensions (`/tiptap`)

**Кто:** агент `/tiptap`

**Задачи:**

1.1. Создать `NumberedClauseNode.tsx`:
   - Node с `group: 'block'`, `content: 'block+'`, `attrs: { level: 1..5 }`
   - `ReactNodeViewRenderer` — отображает `<NodeViewContent>` без номера (номер добавит плагин)
   - `addCommands()` — `insertNumberedClause(level)` и `setClauseLevel(level)`
   - `addProseMirrorPlugins()` — `AutoNumberingPlugin`:
     - ключ плагина принимает опциональную функцию-фильтр `isHidden(pos) => boolean`
     - строит `DecorationSet` с `Decoration.widget` для каждого видимого нода
   - Экспортировать плагин отдельно для переиспользования в workspace-версии

1.2. Создать `ConditionalInlineNode.tsx`:
   - Node с `group: 'inline'`, `inline: true`, `content: 'inline*'`, `atom: false`
   - Атрибуты `condition` и `label` — аналогично `ConditionalBlockNode`
   - `ReactNodeViewRenderer` — тонкая подсветка `background: #FFF7E6`, иконка `⚡`
   - Кнопки: настроить условие (тот же `ConditionBuilder`), unwrap, удалить с контентом
   - `addCommands()` — `wrapInConditionalInline(attrs)` (оборачивает selection),
     `unwrapConditionalInline()` (извлекает контент)

1.3. Обновить `ConditionalBlockNode.tsx`:
   - Добавить атрибут `collapsed: boolean` (default: false)
   - В `ConditionalBlockView` добавить кнопку сворачивания/разворачивания
   - При `collapsed=true` — скрыть `<NodeViewContent>`, показать краткую шапку
   - Перенести выражение условия из видимого текста в `Tooltip` на шапке

**Результат:** 3 файла (2 новых, 1 обновлённый)

---

### Этап 2 — Интеграция в редактор шаблонов (`/frontend`)

**Кто:** агент `/frontend`

**Задачи:**

2.1. `TemplateDesigner.tsx` — добавить в `EXTENSIONS`:
   - `NumberedClauseNode`
   - `ConditionalInlineNode`

2.2. `EditorToolbar.tsx` — добавить кнопки/выпадашки:
   - **Нумерованный пункт**: выпадающее меню `Пункт 1 / Пункт 1.1 / Пункт 1.1.1`
     → вызывает `insertNumberedClause(level)`
   - **Inline-условие**: активно только если есть выделение (selection непустой);
     вызывает `wrapInConditionalInline({ label: 'Условный текст', condition: DEFAULT })`

2.3. Стили (при необходимости в `TemplateDesigner.css`):
   - Отступы для `numberedClause` по уровню: `level * 20px`

**Результат:** обновлены 2 файла

---

### Этап 3 — Интеграция в DocumentWorkspace (`/frontend`)

**Кто:** агент `/frontend`

**Задачи:**

3.1. Создать `WorkspaceNumberedClauseNode.tsx`:
   - Аналог `NumberedClauseNode`, но использует `AutoNumberingPlugin` с фильтром
   - Фильтр: позиция скрыта, если нод находится внутри `conditionalBlock`/`conditionalInline`,
     условие которого ложно при текущих `answers`
   - Хранить скрытые позиции в `editor.storage.numberedClause.hiddenPositions: Set<number>`
   - Обновлять `hiddenPositions` при изменении `answers`

3.2. `DocumentWorkspace.tsx`:
   - Добавить `WorkspaceNumberedClauseNode` и `ConditionalInlineNode` в расширения
   - При изменении `answers` обновлять `storage.numberedClause.hiddenPositions`
     и диспатчить no-op транзакцию (аналогично WorkspaceParameterFieldNode)

3.3. `WorkspaceParameterFieldNode.tsx` — добавить в storage поле `conditionalInline`
   для рендера: при ложном условии inline-нод рендерит пустой span.

3.4. `previewRenderer.ts` — обновить `renderPreview`:
   - Учитывать `conditionalInline` ноды (аналогично `conditionalBlock`)
   - `numberedClause` ноды передавать как есть (номера добавит плагин динамически)

**Результат:** 1 новый файл, 3 обновлённых

---

### Этап 4 — Ревью (`/review`)

**Кто:** агент `/review`

Проверить все изменённые файлы этапов 1-3:
- TipTap-специфика: симметричность `parseHTML`/`renderHTML`, корректность schema
- Нет мутаций state ProseMirror
- Типизация без `any`
- Компоненты < 200 строк
- Нет прямых fetch вне `api/`

---

### Этап 5 — Сборка и проверка TypeScript (`/frontend`)

**Кто:** агент `/frontend`

Запустить TypeScript-проверку и сборку:

```bash
cd frontend && npx tsc --noEmit
```

Если есть ошибки — исправить в рамках этого же этапа, не переходя к следующему.

---

### Этап 6 — Исправление ошибок сборки и runtime (`/debug`)

**Кто:** агент `/debug`  
**Запускается:** только если этап 5 выявил проблемы, которые `/frontend` не смог исправить

Типичные проблемы для данной задачи:
- TipTap schema conflict: нод с одинаковым именем зарегистрирован дважды
- `content: 'inline*'` не принимает `parameterField` — проверить `group: 'inline'` у `ParameterFieldNode`
- `Decoration.widget` с некорректной позицией — выход за границы документа
- `editor.storage` не инициализирован в момент обращения

После исправлений — повторить этап 5.

---

### Этап 7 — Ручное тестирование сценариев (разработчик)

**Кто:** разработчик — выполняется **после этапа 9** (документация)  
**Запускается:** разработчик самостоятельно после завершения всех предыдущих этапов

#### 7.1 Автонумерация в редакторе шаблонов
- [ ] Вставить пункт уровня 1 → отображается `1.`
- [ ] Вставить второй пункт уровня 1 → отображается `2.`
- [ ] Вставить пункт уровня 2 после первого уровня 1 → отображается `1.1.`
- [ ] Вложенность 3 уровня → `1.1.1.`
- [ ] Удалить пункт из середины → нумерация пересчитывается
- [ ] Переместить пункт (drag/cut-paste) → нумерация пересчитывается

#### 7.2 Пересчёт нумерации в DocumentWorkspace
- [ ] Создать шаблон: пункт 1, условный блок с пунктом 2, пункт 3
- [ ] Заполнить документ с условием = true → пункты `1.`, `2.`, `3.`
- [ ] Переключить условие = false → пункты `1.`, `2.` (бывший 3 стал 2)

#### 7.3 Компактный условный блок
- [ ] Кнопка сворачивания скрывает контент, показывает компактную шапку
- [ ] Разворачивание восстанавливает контент
- [ ] Выражение условия видно в Tooltip при наведении на шапку
- [ ] Состояние collapsed не мешает редактированию контента при развёрнутом виде

#### 7.4 Inline-вариативность
- [ ] Выделить текст → кнопка в тулбаре активна
- [ ] Обернуть в условие → фрагмент подсвечен, отображается иконка
- [ ] Настроить условие через ConditionBuilder
- [ ] В DocumentWorkspace при ложном условии фрагмент не отображается
- [ ] Inline внутри условного блока (двойная вариативность) — работает корректно
- [ ] Unwrap — контент сохраняется, нод удаляется

#### 7.5 Сохранение и загрузка
- [ ] Сохранить шаблон → перезагрузить страницу → все ноды восстановились корректно
- [ ] `parseHTML`/`renderHTML` симметричны (нет потери attrs)

---

### Этап 8 — Исправление багов после ручного тестирования (`/debug` / `/tiptap` / `/frontend`)

**Кто:** зависит от типа бага  
**Запускается:** только если этап 7 выявил проблемы

- Поведение ProseMirror / декорации → `/tiptap`
- React-компонент / UI → `/frontend`
- Диагностика неочевидного бага → `/debug`

После каждого исправления разработчик повторяет соответствующий чеклист из этапа 7.

---

### Этап 9 — Обновление документации (`/architect`)

**Кто:** агент `/architect`

**Задачи:**

9.1. `docs/architecture.md`:
   - Раздел "Кастомные TipTap ноды шаблона" — добавить `numbered_clause`, `conditional_inline`
   - Описание алгоритма автонумерации (A1/A2)
   - Описание коллапс-режима `conditional_block`

9.2. `frontend/src/components/template-designer/extensions/` — добавить/обновить JSDoc
   на публичных функциях-командах каждого extension.

9.3. `docs/plan-autonumbering-variability.md` (этот файл) — обновить статус на
   `реализовано` после завершения всех этапов.

---

## Порядок вызова агентов

```
/tiptap   → Этап 1: три расширения
/frontend → Этап 2: EditorToolbar + TemplateDesigner
/frontend → Этап 3: DocumentWorkspace + previewRenderer
/review   → Этап 4: проверка кода
/frontend → Этап 5: сборка, TypeScript-проверка
/debug    → Этап 6: исправление ошибок сборки (если нужно)
/architect → Этап 9: документация
            Этап 7: ручное тестирование (разработчик)
/debug    → Этап 8: исправление багов (если нужно)
/tiptap   → Этап 8: исправление TipTap-багов (если нужно)
/frontend → Этап 8: исправление UI-багов (если нужно)
```

Этапы 2 и 3 можно выполнять последовательно одним вызовом `/frontend`,
передав оба задания.

Этапы 6 и 8 запускаются только при наличии проблем — не обязательные.

Этап 7 выполняется разработчиком после завершения этапа 9.

---

## Риски

| Риск | Митигация |
|------|-----------|
| `AutoNumberingPlugin` пересчитывает при каждой транзакции — возможен тормоз на больших документах | Мемоизировать DecorationSet, пересчитывать только при изменении структуры (`docChanged`) |
| Inline-нод `content: 'inline*'` может принять cursor внутрь при клике — странное UX | Протестировать, возможно добавить `selectable: false` или custom keyboard handler |
| `ConditionalInlineNode` вложен в `ConditionalBlockNode` — двойная вариативность | Поддерживается схемой ProseMirror, но нужно протестировать пересчёт нумерации |
| `previewRenderer.ts` не знает о новых нодах | Явно обрабатывать `numberedClause` и `conditionalInline` в switch-case |
