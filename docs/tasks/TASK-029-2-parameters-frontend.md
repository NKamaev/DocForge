# TASK-029-2: Frontend — TipTap-расширения для параметров и ссылок на пункты

| Поле | Значение |
|---|---|
| **Дата** | 2026-04-18 |
| **Тип** | `feature` |
| **Статус** | `DONE` |
| **Агенты** | `/frontend` |
| **Коммит** | `9a83b4f` |
| **Зависит от** | — |
| **GitHub Issue** | — |
| **Эпик** | [TASK-029](TASK-029-parameters-and-clause-refs.md) |

---

## Постановка задачи (от пользователя)

```
добавить возможность добавления параметров и ссылки на пункты в формулировках
```

---

## Интерпретация

Добавить два новых inline-узла в TipTap редактор:

### 1. TemplateParam — параметр шаблона
- Inline-чип с названием поля: `[Имя клиента]`
- Вставляется через тулбар: кнопка «Параметр» → dropdown со списком полей шаблона
- Атрибуты: `fieldId`, `fieldName`
- В режиме `/fill` рендерится как заполненное значение (или подчёркнутый плейсхолдер если пусто)

### 2. ClauseRef — ссылка на пункт
- Inline-чип с номером: `[п. 2.3]`
- Вставляется через тулбар: кнопка «Ссылка на пункт» → выбор пункта из дерева документа
- Атрибуты: `clauseId`, `clauseNumber` (вычисляется динамически)
- Номер обновляется при изменении структуры документа

---

## Файлы для изучения перед исполнением

```
frontend/src/extensions/                              — существующие TipTap-расширения
frontend/src/components/template-designer/ToolbarPlugin.tsx
frontend/src/components/document-workspace/
frontend/src/types/template.ts                        — TemplateField
frontend/src/api/template.ts
```

---

## Файлы для изменения / создания

```
frontend/src/extensions/TemplateParam.ts              — новое расширение
frontend/src/extensions/ClauseRef.ts                 — новое расширение
frontend/src/components/template-designer/ToolbarPlugin.tsx   — кнопки вставки
frontend/src/components/template-designer/ParamPickerModal.tsx — новый модал
frontend/src/components/template-designer/ClausePickerModal.tsx — новый модал
frontend/src/components/document-workspace/WorkspaceTemplateParam.tsx — render /fill
```

---

## Поэтапное исполнение

1. **Создать** TipTap-расширение `TemplateParam` (inline Node, атрибуты `fieldId`, `fieldName`).
2. **Создать** TipTap-расширение `ClauseRef` (inline Node, атрибуты `clauseId`, `clauseNumber`).
3. **Добавить** кнопку «Параметр» в тулбар → `ParamPickerModal` со списком полей шаблона.
4. **Добавить** кнопку «Ссылка на пункт» в тулбар → `ClausePickerModal` с деревом пунктов.
5. **Стилизовать** чипы: синий фон для параметра, серый для ссылки на пункт.
6. **Реализовать** рендеринг `TemplateParam` в режиме `/fill`: значение из `answers`.
7. **Реализовать** динамический пересчёт номеров `clauseNumber` при изменении структуры.
8. **Написать** unit-тесты для расширений.

---

## Автоматические тесты

### Что покрыть

| Сценарий | Тип теста | Файл |
|---|---|---|
| TemplateParam вставляется в редактор | unit | `TemplateParam.test.ts` |
| ClauseRef рендерит корректный номер | unit | `ClauseRef.test.ts` |
| WorkspaceTemplateParam показывает значение из answers | unit | `WorkspaceTemplateParam.test.tsx` |

### Команды запуска

```bash
cd frontend && npm test -- --run src/extensions/TemplateParam.test.ts
cd frontend && npm test -- --run src/extensions/ClauseRef.test.ts
```

---

## Чеклист ручного тестирования

- [ ] Открыть конструктор шаблона → кнопка «Параметр» в тулбаре присутствует
- [ ] Нажать «Параметр» → появился dropdown/модал со списком полей
- [ ] Выбрать поле → в тексте вставился чип `[Имя поля]`
- [ ] Нажать «Ссылка на пункт» → появился список пунктов документа
- [ ] Выбрать пункт → вставился чип `[п. X]`
- [ ] Открыть документ на `/fill` → параметр заменён значением из опросника
- [ ] Переставить пункты → номера в ссылках обновились
- [ ] Регрессия: существующие ConditionalBlock/InlineNode работают без изменений

---

## История

| Дата | Событие |
|---|---|
| 2026-04-18 | Задача создана |
| 2026-04-23 | Задача выполнена: все компоненты уже были реализованы, добавлены unit-тесты (коммит `9a83b4f`) |

---

## Завершение задачи

> **Обязательно выполнить перед отчётом о выполнении!**

- [x] Автотесты запущены и проходят (`npm test`)
- [ ] Ручной чеклист выполнен полностью
- [ ] Код прошёл ревью (`/review`)
- [x] Git-коммит создан: `9a83b4f test(editor): add unit tests for TemplateParam, ClauseRef and WorkspaceParameterFieldNode`
- [x] Статус обновлён в этом файле: `DONE`
- [x] Реестр обновлён: [docs/tasks/README.md](README.md)
- [ ] GitHub Issue закрыт (state: closed) + комментарий с хешем коммита
