# TASK-009: Подавить onUpdate при инициализации initialBody в DocumentWorkspace

| Поле | Значение |
|---|---|
| **Дата** | 2026-04-17 |
| **Тип** | `refactor` |
| **Статус** | `TODO` |
| **Агенты** | `/frontend` |
| **Коммит** | — |
| **Зависит от** | TASK-006-1 |
| **GitHub Issue** | [#9](https://github.com/NKamaev/DocForge/issues/9) |

---

## Постановка задачи (от пользователя)

> Ревью TASK-006-1: WARNING — `setContent(initialBody)` эмитирует `onUpdate` → `handleBodyChange` → debounce 1500мс → `updateBody` при каждой загрузке страницы.

---

## Интерпретация

При открытии документа с `current_body` выполняется холостая запись в MongoDB: `setContent(initialBody)` по умолчанию эмитирует `onUpdate`, который запускает `handleBodyChange` → debounce → `updateBody` с теми же данными что только что загружены с сервера.

При 100 открытиях документа в день — 100 лишних write-операций без изменения данных.

---

## Причина ошибки

**Корень проблемы:**  
TipTap `editor.commands.setContent(content)` по умолчанию вызывает `onUpdate` (второй параметр `emitUpdate` по умолчанию `true`). В ветке `initialBody` это нежелательно — данные уже актуальны.

**Цепочка сбоя:**  
1. Страница загружается, `doc.currentBody != null`
2. `DocumentWorkspace` инициализирует редактор: `setContent(initialBody as JSONContent)` — эмитирует `onUpdate`
3. `onUpdate` → `onBodyChangeRef.current(e.getJSON())` → `handleBodyChange`
4. `handleBodyChange` устанавливает `bodyTimerRef` на 1500мс
5. Через 1500мс: `documentsApi.updateBody(id, body)` — запись тех же данных обратно в MongoDB

---

## Файлы для изменения

```
frontend/src/components/questionnaire/DocumentWorkspace.tsx
  — строка 146: setContent(initialBody as JSONContent)
    → setContent(initialBody as JSONContent, false)  // подавить onUpdate
```

---

## Поэтапное исполнение

1. В `DocumentWorkspace.tsx` в ветке `if (initialBody != null)` изменить:
   ```ts
   editor.commands.setContent(initialBody as JSONContent)
   ```
   на:
   ```ts
   editor.commands.setContent(initialBody as JSONContent, false)
   ```
2. Убедиться что `renderPreview`-ветка (`else if (templateBody)`) остаётся без изменений — там `emitUpdate=true` нужен, чтобы `editorBodyRef.current` установился сразу после первого рендера нового документа.

---

## Автоматические тесты

| Сценарий | Тип теста | Файл |
|---|---|---|
| `updateBody` НЕ вызывается при загрузке страницы с `initialBody` | unit | `QuestionnairePage.test.tsx` |
| `updateBody` вызывается при редактировании после загрузки | unit | `QuestionnairePage.test.tsx` |

```bash
cd frontend && npm test -- --run
```

---

## Чеклист ручного тестирования

- [ ] Открыть документ с `current_body` → DevTools Network → PUT `/body` НЕ должен стрелять в первые 2 секунды без действий пользователя
- [ ] Отредактировать текст → через 1.5с PUT `/body` стреляет
- [ ] Регрессия: новый документ (без `current_body`) → после первого рендера `editorBodyRef.current` устанавливается корректно

---

## История

| Дата | Событие |
|---|---|
| 2026-04-17 | Задача создана по результатам ревью TASK-006-1 |

---

## Завершение задачи

> **Обязательно выполнить перед отчётом о выполнении!**

- [ ] Автотесты запущены и проходят (`npm test`)
- [ ] Ручной чеклист выполнен полностью
- [ ] Код прошёл ревью (`/review`)
- [ ] Git-коммит создан с сообщением: `refactor(fill): suppress onUpdate when initializing editor from current_body`
- [ ] Статус обновлён в этом файле: `DONE`
- [ ] Реестр обновлён: [docs/tasks/README.md](README.md)
