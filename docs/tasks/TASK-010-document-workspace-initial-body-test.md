# TASK-010: Unit-тест DocumentWorkspace — инициализация из initialBody

| Поле | Значение |
|---|---|
| **Дата** | 2026-04-17 |
| **Тип** | `refactor` |
| **Статус** | `TODO` |
| **Агенты** | `/frontend` |
| **Коммит** | — |
| **Зависит от** | TASK-006-1 |
| **GitHub Issue** | [#10](https://github.com/NKamaev/DocForge/issues/10) |

---

## Постановка задачи (от пользователя)

> Ревью TASK-006-1: WARNING — отсутствует unit-тест для `DocumentWorkspace` с `initialBody`. В чеклисте задачи указан тест, но файл `DocumentWorkspace.test.tsx` не создан.

---

## Интерпретация

TASK-006-1 добавила логику `initialBody` в `DocumentWorkspace`, но тест для неё не написан. Нужно покрыть:
1. Когда `initialBody` задан — `setContent` вызывается с ним, а не с `renderPreview`
2. Когда `initialBody` равен `null` — `renderPreview` вызывается и `setContent` получает его результат

---

## Файлы для изучения перед исполнением

```
frontend/src/components/questionnaire/DocumentWorkspace.tsx  — логика инициализации (строки 140–152)
frontend/src/components/questionnaire/QuestionnairePage.test.tsx  — образец стиля тестов
```

---

## Файлы для изменения

```
frontend/src/components/questionnaire/DocumentWorkspace.test.tsx  — создать новый файл
```

---

## Поэтапное исполнение

1. Создать `DocumentWorkspace.test.tsx` рядом с компонентом.
2. Замокировать TipTap (`@tiptap/react`) — `useEditor` возвращает объект с `commands.setContent`, `view`, `storage` и т.д.
3. Замокировать `renderPreview` из `../../utils/previewRenderer`.
4. Написать тест: `initialBody` задан → `setContent(initialBody)` вызван, `renderPreview` не вызван.
5. Написать тест: `initialBody` равен `null`, `templateBody` задан → `renderPreview` вызван, `setContent` получил результат `renderPreview`.

---

## Автоматические тесты

| Сценарий | Тип теста | Файл |
|---|---|---|
| DocumentWorkspace использует `initialBody` если задан | unit | `DocumentWorkspace.test.tsx` |
| DocumentWorkspace вызывает `renderPreview` если `initialBody` не задан | unit | `DocumentWorkspace.test.tsx` |

```bash
cd frontend && npm test -- --run
```

---

## Чеклист ручного тестирования

- [ ] Тесты запущены, зелёные: `npm test -- --run`

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
- [ ] Git-коммит создан с сообщением: `test(fill): add DocumentWorkspace unit tests for initialBody initialization`
- [ ] Статус обновлён в этом файле: `DONE`
- [ ] Реестр обновлён: [docs/tasks/README.md](README.md)
