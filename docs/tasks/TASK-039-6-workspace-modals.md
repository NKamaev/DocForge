# TASK-039-6: Workspace: новый layout + модальные окна и drawer-панели

| Поле | Значение |
|---|---|
| **Дата** | 2026-04-25 |
| **Тип** | `feature` |
| **Статус** | `TODO` |
| **Агенты** | `/frontend` |
| **Коммит** | — |
| **Зависит от** | [TASK-039-2](TASK-039-2-shell-layout.md) |
| **Родитель** | [TASK-039](TASK-039-new-design-handoff.md) |

---

## Постановка задачи

Переработать layout рабочего пространства документа и все модальные окна / drawer-панели по дизайну из `workspace.jsx` и `modals.jsx`.

---

## Файлы для изменения

```
frontend/src/pages/              — DocumentWorkspacePage или аналог
frontend/src/components/questionnaire/
frontend/src/components/document-list/  — модальные окна
```

## Файлы для изучения

```
/tmp/docforge-handoff/docforge/project/workspace.jsx
/tmp/docforge-handoff/docforge/project/modals.jsx
```

---

## Поэтапное исполнение

### 1. Workspace Layout (`.workspace`)
```css
.workspace { display: grid; grid-template-columns: 380px 1fr; height: calc(100vh - var(--topbar-h)); }
```

### 2. Левая панель `.q-panel`
- `.q-head`:
  - Строка: «← Документы» (ghost btn) + счётчик X/Y
  - `<h3>` название документа
  - Шаблон (12px muted)
  - `.q-progress` — прогресс-бар (ширина = progress%)
  - `.steps` — шаги: `ШАГ N` + название, active/done состояния
- `.q-body`: список `QField` для текущего шага
- `.q-foot`: кнопки Назад / «Страница N из M» / Далее или Готово

### 3. QField (`.q-card`)
- Номер «Вопрос N» + help-текст + иконка check (если заполнен)
- Метка вопроса (`.q-label`)
- Инпут по типу:
  - `text/number/money` → `<input class="input">`
  - `date` → `.input-group` с иконкой calendar
  - `bool` → `.btn-group` с кнопками Да/Нет (active подсвечена)
  - `select` → `<select class="select">`
- `.pc-meta`: код параметра + «привязан к чипу»

### 4. ParticipantsPanel
- `.avatar-stack`: аватары с зелёной точкой (`right: -1; bottom: -1`)
- «N онлайн» (12px muted)

### 5. Правая панель `.editor-pane`
- Заголовок: подсказка + ParticipantsPanel + История / Комментарии / DOCX / PDF
- `.editor-canvas` с документом (существующий TipTap-редактор в readonly)
- **Синхронизация чипов**: клик на `.chip` в документе → `setActiveQ(qid)` слева; фокус на вопросе → `activeChip` подсвечивает чип

### 6. Модальные окна (`.modal`)

**CreateTemplateModal**:
- Поля: Название* / Категория (select) / Описание (textarea)
- «Начать с»: 2 карточки-опции (Пустого / Импорт DOCX) с выбором по клику

**CreateDocumentModal**:
- Поля: Название* + прокручиваемый список опубликованных шаблонов
- Выбранный шаблон: border brand + shadow

**ExportModal** (440px):
- 2 карточки: DOCX (выбрана, border brand) / PDF
- Поле «Имя файла» + чекбокс «Водяной знак»

### 7. Drawer-панели (`.drawer`)

**PageSettingsDrawer**:
- Размер: btn-group A4/A3/Letter
- Ориентация: btn-group Книжная/Альбомная
- Поля мм: сетка 2×2 (Верх/Низ/Лево/Право)
- Шрифт: select + Размер: select + Межстрочный: select

**RevisionsDrawer** (История изменений):
- Timeline: `paddingLeft: 22`, `borderLeft: dashed var(--line)` (кроме последнего)
- Точка: `position: absolute; left: -5` — синяя для первой, белая для остальных
- Дата·автор + заголовок изменения + кнопка «Восстановить»

---

## Чеклист ручного тестирования

- [ ] Workspace: опросный лист и документ отображаются рядом
- [ ] Клик на чип в документе → активирует соответствующий вопрос
- [ ] Фокус на вопрос → подсвечивает чип в документе
- [ ] Шаги: Далее/Назад переключают страницы вопросов
- [ ] CreateTemplateModal: выбор «Начать с» переключается
- [ ] CreateDocumentModal: выбор шаблона из списка
- [ ] ExportModal: переключение DOCX/PDF
- [ ] PageSettingsDrawer: overlay, закрывается кнопкой × и кликом вне
- [ ] RevisionsDrawer: timeline отображается корректно

---

## История

| Дата | Событие |
|---|---|
| 2026-04-25 | Задача создана |

---

## Завершение задачи

- [ ] Автотесты запущены и проходят
- [ ] Ручной чеклист выполнен
- [ ] Git-коммит создан: `feat(ui): redesign workspace and modals/drawers`
- [ ] Статус обновлён: DONE
- [ ] Реестр обновлён: docs/tasks/README.md
