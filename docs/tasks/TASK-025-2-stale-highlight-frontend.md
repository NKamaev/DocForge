# TASK-025-2: Frontend — подсветка устаревших полей справочника + применение обновлений

| Поле | Значение |
|---|---|
| **Дата** | 2026-04-18 |
| **Тип** | `feature` |
| **Статус** | `TODO` |
| **Агенты** | `/frontend` |
| **Коммит** | — |
| **Зависит от** | TASK-025-1 |
| **GitHub Issue** | [#43](https://github.com/NKamaev/DocForge/issues/43) |

---

## Постановка задачи (от пользователя)

> Часть TASK-025: frontend-половина иммутабельности справочников.

---

## Интерпретация

После того как backend умеет определять устаревшие поля (TASK-025-1), frontend должен:
1. При загрузке документа запросить `GET /api/documents/{id}/staleness`.
2. В TipTap-редакторе подсвечивать `WorkspaceParameterFieldNode` оранжевой обводкой, если поле устарело.
3. В опросном листе (`DirectoryField.tsx`) показывать badge «Обновлено» и кнопку «Применить изменения», которая обновляет `answers` и тело документа.

---

## Файлы для изучения перед исполнением

```
frontend/src/api/documents.ts
frontend/src/components/document-workspace/
frontend/src/components/template-designer/nodes/WorkspaceParameterFieldNode.tsx
frontend/src/components/questionnaire/field-types/DirectoryField.tsx
frontend/src/store/documentStore.ts  (или аналог)
```

---

## Файлы для изменения

```
frontend/src/api/documents.ts
  — добавить fetchStaleness(documentId): Promise<Record<string, StalenessInfo>>

frontend/src/store/documentStore.ts (или useDocument hook)
  — хранить stalenessMap, вызывать fetchStaleness при загрузке документа

frontend/src/components/template-designer/nodes/WorkspaceParameterFieldNode.tsx
  — читать stalenessMap из store; если isStale — добавлять CSS-класс с оранжевой обводкой + Tooltip

frontend/src/components/questionnaire/field-types/DirectoryField.tsx
  — если выбранная запись isStale — показывать Badge "Обновлено"
  — кнопка «Применить изменения» → вызвать onApplyUpdate(paramCode) → обновить answers + patch документ

frontend/src/types/document.ts (или types/staleness.ts новый)
  — type StalenessInfo = { isStale: boolean; currentValues: Record<string,unknown>; snapshotValues: Record<string,unknown> }
```

---

## Поэтапное исполнение

1. **Добавить тип `StalenessInfo`** в `frontend/src/types/`.
2. **Добавить `fetchStaleness()`** в `frontend/src/api/documents.ts`.
3. **Расширить store/hook** — вызывать `fetchStaleness` при открытии документа, сохранять `stalenessMap`.
4. **`WorkspaceParameterFieldNode`** — подписаться на `stalenessMap`; если `stalenessMap[paramCode]?.isStale`:
   - добавить класс `stale-field` (оранжевая обводка)
   - Tooltip: «Данные справочника изменились. Нажмите для обновления»
   - клик на поле — переводить фокус/скролл к соответствующему вопросу в опросном листе (через событие или ref из store)
5. **`DirectoryField`** — если `stalenessMap[paramCode]?.isStale`:
   - показать `<Badge count="Обновлено" color="orange">` рядом с select
   - показать кнопку «Применить изменения»
   - при клике: взять `currentValues` из `stalenessMap`:
     - обновить `answers[paramCode__fieldKey]` и `answers._directory_snapshots[paramCode]`
     - пересчитать `current_body` — применить новые значения полей справочника в TipTap-документе (через editor.commands или повторную подстановку)
     - вызвать `PATCH /api/documents/{id}` с обновлёнными `answers` и `current_body`
   - после успеха: обновить `stalenessMap[paramCode].isStale = false`
6. **CSS**: добавить класс `.stale-field` с `outline: 2px solid #fa8c16; border-radius: 2px;`.

---

## Автоматические тесты

### Что покрыть

| Сценарий | Тип теста | Файл |
|---|---|---|
| WorkspaceParameterFieldNode рендерит класс stale-field при isStale=true | unit | `WorkspaceParameterFieldNode.test.tsx` |
| DirectoryField показывает Badge и кнопку при isStale=true | unit | `DirectoryField.test.tsx` |
| Клик «Применить» обновляет answers и снимает isStale | unit | `DirectoryField.test.tsx` |

### Команды запуска

```bash
cd frontend && npm test -- --run src/components/template-designer/nodes/WorkspaceParameterFieldNode.test.tsx
cd frontend && npm test -- --run src/components/questionnaire/field-types/DirectoryField.test.tsx
```

---

## Чеклист ручного тестирования

- [ ] Открыть документ с DIRECTORY-параметром → изменить запись справочника → обновить страницу → убедиться что поле подсвечено оранжевым
- [ ] Tooltip при наведении на подсвеченное поле показывает «Данные справочника изменились. Нажмите для обновления»
- [ ] Клик на подсвеченное поле в редакторе → фокус переходит к нужному вопросу в опросном листе
- [ ] В опросном листе Badge «Обновлено» видна рядом с select
- [ ] Клик «Применить изменения» → badge исчезает, подсветка снимается, значения в `answers` и `current_body` обновились
- [ ] Если справочник не изменился — никакой подсветки нет
- [ ] Регрессия: параметры не-DIRECTORY типов не подсвечиваются

---

## История

| Дата | Событие |
|---|---|
| 2026-04-18 | Задача создана как подзадача TASK-025 |

---

## Завершение задачи

> **Обязательно выполнить перед отчётом о выполнении!**

- [ ] Автотесты запущены и проходят (`npm test`)
- [ ] Ручной чеклист выполнен полностью
- [ ] Код прошёл ревью (`/review`)
- [ ] Git-коммит создан с сообщением: `feat(document): stale directory field highlighting + apply updates`
- [ ] Статус обновлён в этом файле: `DONE`
- [ ] Реестр обновлён: [docs/tasks/README.md](README.md)
