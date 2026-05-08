# Plan: Расширенная нумерация пунктов

> Статус: реализовано (2026-04-09)
> Архитектор: /architect
> Дата: 2026-04-09
> Зависимость: план `plan-autonumbering-variability.md` выполнен (базовая нумерация работает)

---

## Scope задачи

1. **Нумерация литерами** — поддержка стилей: `decimal` (1 2 3), `alpha-ru` (а б в), `alpha-en` (a b c)
2. **Дополнительные уровни** — расширить тулбар до 5+ уровней (плагин уже держит 5, нужно UI)
3. **Начать новый список** — атрибут `listStart` сбрасывает счётчики всех уровней с этого пункта
4. **Ссылки на пункты** — inline-нод `ClauseRefNode`, который динамически отображает номер referenced пункта; при перенумерации ссылка обновляется автоматически

---

## Архитектурные решения

### B1. Расширенные атрибуты `numberedClause`

К существующему `level: 1..5` добавляются три новых атрибута:

```
attrs: {
  level:          1..5   (существующий)
  numberingStyle: 'decimal' | 'alpha-ru' | 'alpha-en'   (новый, default: 'decimal')
  listStart:      boolean    (новый, default: false)
  id:             string     (новый, UUID генерируется при создании, нужен для ссылок)
}
```

`id` генерируется один раз при вставке нода (`insertNumberedClause`) через `crypto.randomUUID()`.
При copy-paste TipTap создаёт новый нод → нужно переопределить `onCreate` / `parseHTML` так,
чтобы при отсутствии `data-clause-id` в HTML генерировался новый UUID.

### B2. AutoNumberingPlugin — расширенный алгоритм

Текущий state плагина: `Map<number, string>` (pos → label).

Расширенный state:
```ts
interface AutoNumberingState {
  byPos: Map<number, string>   // pos → label (существующий)
  byId:  Map<string, string>   // id → label (новый, для ClauseRefNode)
}
```

Алгоритм `buildLabels`:
1. Отслеживать `counters[level]` и `styles[level]` (style последнего нода на этом уровне, default `'decimal'`).
2. При встрече нода с `listStart: true` — сбросить `counters[0..4]` в ноль.
3. Инкрементировать и сбросить дочерние счётчики, как раньше.
4. Записать `styles[level] = node.attrs.numberingStyle`.
5. Формировать label: сегменты `counters[0..level-1]` форматируются через `formatCounter(n, styles[i])`.

```ts
function formatCounter(n: number, style: 'decimal' | 'alpha-ru' | 'alpha-en'): string {
  if (style === 'alpha-ru') return 'абвгдежзийклмнопрстуфхцчшщэюя'[n - 1] ?? String(n)
  if (style === 'alpha-en') return 'abcdefghijklmnopqrstuvwxyz'[n - 1] ?? String(n)
  return String(n)
}
```

Label: `formatCounter(c1, s1) + '.' + formatCounter(c2, s2) + ... + '.'`

Пример: level-2 нод со `numberingStyle: 'alpha-ru'` после level-1 нода со `numberingStyle: 'decimal'`:
→ label = `"1.а."`

Пример: `listStart: true` level-1 нод — сбрасывает всё, счётчик начинается с 1:
→ label = `"1."`

### B3. ClauseRefNode — inline ссылка на пункт

```
group: 'inline'
inline: true
atom: true                   ← нод непрозрачен, нельзя войти внутрь
attrs: { refId: string }     ← совпадает с id нода numberedClause
```

React view:
- Читает `autoNumberingPluginKey.getState(editor.view.state).byId.get(refId)`.
- Если label найден: рендерит `<span>п. {label}</span>` со светло-синим фоном.
- Если label не найден (пункт удалён): рендерит `<span style="color:red">???</span>` + Tooltip "Пункт удалён".
- Кнопка-иконка "перейти к пункту" (scroll into view через `editor.commands.scrollIntoView`).

Команда `insertClauseRef(refId)` — вставляет нод в текущую позицию.

Toolbar "Ссылка на пункт":
- Dropdown, список строится из `autoNumberingPluginKey.getState(editor.view.state).byId`.
- Каждый пункт: `{label} {первые 40 символов текста пункта}`.
- Клик → `editor.chain().focus().insertClauseRef(id).run()`.

### B4. Увеличение количества уровней в тулбаре

Плагин уже ограничивает до 5 (`Math.min(..., 5)`). Тулбар сейчас показывает 1–3.
Расширить тулбар до 5 уровней. Изменить ограничение в плагине с 5 до 9 если понадобится
(решение принять на этапе 1 после оценки типичных документов).

---

## Затрагиваемые файлы

### Изменяемые файлы
```
frontend/src/components/template-designer/extensions/NumberedClauseNode.tsx
  — расширить attrs, обновить AutoNumberingPlugin (state type + buildLabels)
  — экспортировать AutoNumberingState type

frontend/src/components/template-designer/
  EditorToolbar.tsx
  — расширить dropdown "Пункт": уровни 1–5, кнопки стиля нумерации, "Начать новый список"
  — добавить dropdown "Ссылка на пункт"

  TemplateDesigner.tsx
  — добавить ClauseRefNode в extensions

frontend/src/components/questionnaire/
  WorkspaceNumberedClauseNode.tsx
  — обновить под новый AutoNumberingState (byPos вместо plain Map)
  — поддержать новые attrs в parseHTML/renderHTML

  DocumentWorkspace.tsx
  — добавить ClauseRefNode в extensions

frontend/src/utils/previewRenderer.ts
  — обработать clause_ref ноды (подставить label по id из статического обхода)
```

### Новые файлы
```
frontend/src/components/template-designer/extensions/ClauseRefNode.tsx
```

### Без изменений
- Backend: тело шаблона хранится как `Map<String, Object>`, новые attrs прозрачны.
- PostgreSQL/MongoDB схемы, Flyway-миграции: не нужны.
- `ConditionalBlockNode.tsx`, `ConditionalInlineNode.tsx`: не затрагиваются.

---

## Этапы реализации

---

### Этап 1 — Расширение TipTap extensions (`/tiptap`)

**Кто:** агент `/tiptap`

**Контекст:** прочитать `frontend/src/components/template-designer/extensions/NumberedClauseNode.tsx`
и `frontend/src/components/questionnaire/WorkspaceNumberedClauseNode.tsx` перед работой.

**Задачи:**

1.1. Обновить `NumberedClauseNode.tsx`:

- Добавить атрибуты `numberingStyle`, `listStart`, `id`:
  ```ts
  numberingStyle: {
    default: 'decimal',
    parseHTML: el => el.getAttribute('data-numbering-style') ?? 'decimal',
    renderHTML: attrs => ({ 'data-numbering-style': attrs.numberingStyle }),
  },
  listStart: {
    default: false,
    parseHTML: el => el.hasAttribute('data-list-start'),
    renderHTML: attrs => attrs.listStart ? { 'data-list-start': '' } : {},
  },
  id: {
    default: null,
    parseHTML: el => el.getAttribute('data-clause-id') ?? null,
    renderHTML: attrs => ({ 'data-clause-id': attrs.id }),
  },
  ```
- При `insertNumberedClause(level, style?)`: генерировать `id: crypto.randomUUID()`.
- Добавить команды:
  - `setClauseNumberingStyle(style: 'decimal' | 'alpha-ru' | 'alpha-en')` — updateAttributes
  - `toggleClauseListStart()` — toggle `listStart`
- Расширить `AutoNumberingState`:
  ```ts
  export interface AutoNumberingState {
    byPos: Map<number, string>
    byId:  Map<string, string>
  }
  ```
- Обновить `autoNumberingPluginKey` тип и `buildLabels` согласно алгоритму B2.
- Обновить `NumberedClauseView` — читать из `state.byPos.get(pos)` вместо `state.get(pos)`.
- Ограничение уровней: оставить 5 (можно расширить до 9 если потребуется).

1.2. Создать `ClauseRefNode.tsx`:

- Node: `group: 'inline'`, `inline: true`, `atom: true`, attrs: `{ refId: string }`.
- parseHTML: `span[data-type="clause-ref"]`, renderHTML: `span` с `data-type` и `data-ref-id`.
- React view:
  - Читает `autoNumberingPluginKey.getState(editor.view.state)?.byId.get(refId)`.
  - Если найден: `<span style="background:#E6F4FF; padding: 0 4px; borderRadius: 2px; cursor:pointer">п. {label}</span>`.
  - Если не найден: `<span style="color:red">???</span>` + Tooltip "Пункт удалён или не найден".
  - Кнопка перехода (только в режиме редактирования): при клике — `editor.view.dispatch(...)` чтобы поставить курсор к пункту.
  - Перерисовка: подписаться на `editor.on('transaction', ...)` аналогично `NumberedClauseView`.
- Команда `insertClauseRef(refId: string)`.
- Декларация в `declare module '@tiptap/core'`.

1.3. Обновить `WorkspaceNumberedClauseNode.tsx`:

- `autoNumberingPluginKey.getState(...)` теперь возвращает `AutoNumberingState | undefined`.
- Изменить: `labels?.byPos.get(pos)` вместо `labels?.get(pos)`.
- Добавить новые attrs в `addAttributes()` (аналогично NumberedClauseNode) — для корректного parseHTML.

**Результат:** 1 новый файл (`ClauseRefNode.tsx`), 2 обновлённых.

---

### Этап 2 — Интеграция в редактор шаблонов (`/frontend`)

**Кто:** агент `/frontend`

**Контекст:** прочитать `EditorToolbar.tsx` и `TemplateDesigner.tsx` перед работой.

**Задачи:**

2.1. `TemplateDesigner.tsx` — добавить `ClauseRefNode` в массив `EXTENSIONS`.

2.2. `EditorToolbar.tsx` — переработать секцию "Пункт":

- Dropdown "Пункт" → SubMenu структура:
  ```
  ▸ Вставить пункт
      Уровень 1 (1.)
      Уровень 2 (1.1.)
      Уровень 3 (1.1.1.)
      Уровень 4
      Уровень 5
  ─────────────────
  ▸ Стиль нумерации (применяется к текущему пункту)
      1 2 3 (decimal)     ← setClauseNumberingStyle('decimal')
      а б в (alpha-ru)    ← setClauseNumberingStyle('alpha-ru')
      a b c (alpha-en)    ← setClauseNumberingStyle('alpha-en')
  ─────────────────
  ✦ Начать новый список   ← toggleClauseListStart()
                            (чекмарк если editor.isActive('numberedClause', {listStart: true}))
  ```
- Dropdown "Ссылка на пункт":
  - Иконка (LinkOutlined или подобная).
  - Список строится из `autoNumberingPluginKey.getState(editor.view.state)?.byId`.
  - Каждая строка: `{label} — {первые 50 символов текста пункта}`.
  - Текст пункта получить из `editor.state.doc.descendants(...)` по позиции из `byPos`.
  - Если нет ни одного пункта с id — пункт "Нет нумерованных пунктов" (disabled).
  - Активируется всегда (не только при выделении).
  - Клик → `editor.chain().focus().insertClauseRef(id).run()`.

**Результат:** обновлены 2 файла.

---

### Этап 3 — Интеграция в DocumentWorkspace (`/frontend`)

**Кто:** агент `/frontend`

**Задачи:**

3.1. `DocumentWorkspace.tsx` — добавить `ClauseRefNode` в расширения редактора.
(Workspace ClauseRefNode переиспользует тот же нод из `template-designer/extensions/ClauseRefNode.tsx` —
workspace-версия не нужна, autoNumberingPlugin уже зарегистрирован через WorkspaceNumberedClauseNode.)

3.2. `previewRenderer.ts` — добавить обработку `clause_ref`:
- При статическом рендере нет editor state → нужно сначала обойти документ и собрать `Map<id, label>`
  аналогично `buildLabels`, затем подставить в `clause_ref` ноды.
- Рендерить как `<span>п. {label}</span>` или `<span>???</span>`.

**Результат:** обновлены 2 файла.

---

### Этап 4 — Ревью (`/review`)

**Кто:** агент `/review`

Проверить все изменённые файлы этапов 1–3:

- `ClauseRefNode`: атом-нод корректно определён (`atom: true`, отсутствует `content`), симметричность parseHTML/renderHTML.
- `autoNumberingPluginKey` — тип state обновлён везде где используется (`NumberedClauseNode`, `WorkspaceNumberedClauseNode`, `ClauseRefNode`).
- Нет `any` без `// eslint-disable-next-line`.
- `buildLabels` — `listStart` действительно сбрасывает счётчики перед инкрементом (не после).
- Toolbar: список пунктов для dropdown "Ссылка на пункт" вычисляется при каждом открытии (не кешируется в useState без invalidation).
- `previewRenderer.ts` — статический `buildLabels` не использует editor state.
- Компоненты < 200 строк.

---

### Этап 5 — TypeScript-проверка (`/frontend`)

**Кто:** агент `/frontend`

```bash
cd frontend && npx tsc --noEmit
```

Исправить все ошибки в рамках этого этапа.

Типичные проблемы:
- `autoNumberingPluginKey.getState(state)` теперь возвращает `AutoNumberingState | undefined` —
  все обращения к `.get(pos)` нужно заменить на `.byPos.get(pos)`.
- `ClauseRefNode` объявляет `insertClauseRef` в `Commands<ReturnType>` — убедиться, что декларация добавлена.
- `crypto.randomUUID()` может потребовать `lib: ["ES2021"]` или `dom` в tsconfig.

---

### Этап 6 — Исправление ошибок сборки (`/debug`)

**Кто:** агент `/debug`
**Запускается:** только если этап 5 выявил проблемы, не решённые `/frontend`.

После исправлений — повторить этап 5.

---

### Этап 7 — Ручное тестирование (разработчик)

**Кто:** разработчик — выполняется **после** всех предыдущих этапов.

#### 7.1 Нумерация литерами
- [ ] Вставить пункт уровня 1, применить "а б в" → label = `"а."`
- [ ] Вставить второй пункт уровня 1 с тем же стилем → label = `"б."`
- [ ] Пункт уровня 2 со стилем "a b c" после "а." → label = `"а.a."`
- [ ] Смешанные стили в одном документе не конфликтуют
- [ ] Смена стиля на существующем пункте пересчитывает все последующие

#### 7.2 Начать новый список
- [ ] Пункты 1., 2., 3. → поставить курсор на пункт 3. → "Начать новый список"
- [ ] Пункт 3. получает label `"1."`, следующий пункт = `"2."`
- [ ] Снять "Начать новый список" → нумерация возвращается к сквозной
- [ ] `listStart` сохраняется при сохранении/загрузке шаблона

#### 7.3 Ссылки на пункты
- [ ] Dropdown "Ссылка на пункт" показывает все пункты с id
- [ ] Вставленная ссылка отображает корректный номер
- [ ] При вставке нового пункта перед referenced → ссылка обновляет номер
- [ ] При удалении referenced пункта → ссылка показывает `???` с красным цветом
- [ ] Кнопка "перейти к пункту" в ссылке скроллит редактор к нужному месту
- [ ] В DocumentWorkspace ссылки также корректно обновляются при смене answers
- [ ] После сохранения/перезагрузки ссылки сохраняют `refId` и разрешаются корректно

#### 7.4 Уровни в тулбаре
- [ ] Доступны пункты уровней 1–5
- [ ] Отступы отображаются корректно для всех 5 уровней

#### 7.5 Сохранение / загрузка
- [ ] Все новые атрибуты (`numberingStyle`, `listStart`, `id`) сохраняются в JSON
- [ ] После перезагрузки страницы attrs восстанавливаются из HTML (parseHTML симметричен renderHTML)

---

### Этап 8 — Исправление багов после ручного тестирования

**Кто:** зависит от типа бага.
**Запускается:** только при наличии проблем после этапа 7.

- Плагин / ProseMirror / декорации → `/tiptap`
- React-компонент / UI / toolbar → `/frontend`
- Сложная диагностика → `/debug`

После каждого исправления — повторить соответствующий чеклист.

---

### Этап 9 — Обновление документации (`/architect`)

**Кто:** агент `/architect`

**Задачи:**

9.1. `docs/architecture.md`:
- В разделе "Кастомные TipTap ноды шаблона" обновить описание `numbered_clause`:
  атрибуты `numberingStyle`, `listStart`, `id`; алгоритм `buildLabels` с поддержкой литер.
- Добавить описание нода `clause_ref`.

9.2. JSDoc на публичных командах в `NumberedClauseNode.tsx` и `ClauseRefNode.tsx`.

9.3. Обновить статус этого файла на `реализовано`.

---

## Порядок вызова агентов

```
/tiptap   → Этап 1: NumberedClauseNode + ClauseRefNode + WorkspaceNumberedClauseNode
/frontend → Этап 2: EditorToolbar + TemplateDesigner
/frontend → Этап 3: DocumentWorkspace + previewRenderer
/review   → Этап 4: ревью изменённых файлов
/frontend → Этап 5: TypeScript-проверка
/debug    → Этап 6: исправление ошибок сборки (если нужно)
            Этап 7: ручное тестирование (разработчик)
/debug    → Этап 8: исправление багов (если нужно)
/architect → Этап 9: документация
```

Этапы 2 и 3 можно выполнить одним вызовом `/frontend`.
Этапы 6 и 8 — опциональные.
Этап 7 — выполняет разработчик, агенты не нужны.

---

## Риски

| Риск | Митигация |
|------|-----------|
| `id` дублируется при copy-paste | В `parseHTML` для `id`: всегда генерировать новый UUID если `data-clause-id` уже есть в документе — проверять `editor.state.doc.descendants` |
| Dropdown "Ссылка на пункт" использует stale state при открытии | Список пунктов вычислять каждый раз при рендере Dropdown items, не кешировать в useState |
| `ClauseRefNode` в Workspace: autoNumberingPlugin ещё не инициализирован при первом рендере | В React view обработать `undefined` из getState, показать пустую строку |
| `previewRenderer.ts` статический: нет plugin state → нужен дополнительный проход по документу | Реализовать отдельную функцию `buildStaticLabels(doc)` для previewRenderer |
| Большой документ с сотнями ссылок: перерисовка всех ClauseRefNode при каждой транзакции | Сравнивать только `.byId` Map перед setState в React view — пересчитывать только при изменении конкретного refId |
