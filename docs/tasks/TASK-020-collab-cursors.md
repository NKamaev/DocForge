# TASK-020: Каретки соавторов в редакторе документа

| Поле | Значение |
|---|---|
| **Дата** | 2026-04-18 |
| **Тип** | `feature` |
| **Статус** | `TODO` |
| **Агенты** | `/frontend` |
| **Коммит** | — |
| **Зависит от** | — |
| **GitHub Issue** | [#29](https://github.com/NKamaev/DocForge/issues/29) |

---

## Постановка задачи (от пользователя)

```
Добавить возможность видеть каретки в тексте документа одновременно работающих пользователей
```

---

## Интерпретация

При одновременной работе нескольких пользователей над документом каждый должен видеть позицию курсора (каретки) и выделение других участников прямо в тексте. Каждый курсор подписан именем пользователя и окрашен в уникальный цвет.

**Что уже есть:**
- Backend (`YjsWebSocketHandler`) уже ретранслирует `MSG_AWARENESS` (тип 1) между клиентами — cursor relay работает.
- `useCollaboration` уже устанавливает `awareness.setLocalStateField('user', { name, color })`.
- `provider` (WebsocketProvider) доступен в `QuestionnairePage`, но не передаётся в `DocumentWorkspace`.
- В `DocumentWorkspace` уже подключено расширение `Collaboration` (для Yjs-синхронизации), но нет `CollaborationCursor`.

**Что нужно сделать:**
1. Установить npm-пакет `@tiptap/extension-collaboration-cursor`.
2. Передать `provider` пропом из `QuestionnairePage` в `DocumentWorkspace`.
3. Добавить расширение `CollaborationCursor.configure({ provider })` в список extensions TipTap.
4. Опционально: добавить базовые CSS-стили каретки (label с именем).

---

## Файлы для изучения перед исполнением

```
frontend/src/hooks/useCollaboration.ts               — awareness setup, loginToColor
frontend/src/components/questionnaire/QuestionnairePage.tsx — где живёт provider
frontend/src/components/questionnaire/DocumentWorkspace.tsx — где живёт useEditor + extensions
frontend/package.json                                — проверить наличие пакета
```

---

## Файлы для изменения

```
frontend/package.json                                — добавить @tiptap/extension-collaboration-cursor
frontend/src/components/questionnaire/DocumentWorkspace.tsx — Props.provider + CollaborationCursor extension
frontend/src/components/questionnaire/QuestionnairePage.tsx — передать provider в DocumentWorkspace
frontend/src/components/questionnaire/QuestionnairePage.test.tsx — обновить mock если нужно
```

---

## Поэтапное исполнение

1. **Установить пакет** — `cd frontend && npm install @tiptap/extension-collaboration-cursor`
2. **Обновить Props** в `DocumentWorkspace` — добавить `provider: WebsocketProvider | null`
3. **Добавить extension** — в массив `extensions` добавить `CollaborationCursor.configure({ provider })` (только если `provider !== null`)
4. **Передать provider** из `QuestionnairePage` — деструктурировать `provider` из `useCollaboration`, пробросить в `<DocumentWorkspace provider={provider} />`
5. **CSS каретки** — добавить минимальные стили для `.collaboration-cursor__caret` и `.collaboration-cursor__label` в `TemplateDesigner.css` или отдельный файл
6. **Проверить тест** — если `QuestionnairePage.test.tsx` рендерит DocumentWorkspace с провайдером, обновить

---

## Автоматические тесты

### Что покрыть

| Сценарий | Тип теста | Файл |
|---|---|---|
| DocumentWorkspace рендерится без ошибок при `provider=null` | unit | `DocumentWorkspace.test.tsx` (новый или существующий) |
| DocumentWorkspace рендерится без ошибок при `provider=mock` | unit | `DocumentWorkspace.test.tsx` |

### Команды запуска

```bash
# Frontend unit
cd frontend && npm test -- --run

# Конкретный файл
cd frontend && npm test -- --run DocumentWorkspace
```

---

## Чеклист ручного тестирования

- [ ] Открыть один документ в двух разных браузерах (или вкладках с разными аккаунтами)
- [ ] Нажать в тексте в первом браузере → во втором должна появиться цветная каретка с именем пользователя
- [ ] Выделить текст → у второго пользователя должно отображаться выделение
- [ ] Цвет каретки соответствует цвету в `ParticipantsPanel`
- [ ] При закрытии вкладки каретка исчезает у второго пользователя
- [ ] Регрессия: редактирование текста по-прежнему работает, синхронизация контента не нарушена

---

## История

| Дата | Событие |
|---|---|
| 2026-04-18 | Задача создана |

---

## Завершение задачи

> **Обязательно выполнить перед отчётом о выполнении!**

- [ ] Автотесты запущены и проходят (`npm test -- --run`)
- [ ] Ручной чеклист выполнен полностью
- [ ] Код прошёл ревью (`/review`)
- [ ] Git-коммит создан с сообщением: `feat(collab): show remote cursors in document editor`
- [ ] Статус обновлён в этом файле: `DONE`
- [ ] Реестр обновлён: [docs/tasks/README.md](README.md)
