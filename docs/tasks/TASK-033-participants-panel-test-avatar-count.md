# TASK-033: ParticipantsPanel — добавить проверку количества аватаров в тест

| Поле | Значение |
|---|---|
| **Дата** | 2026-04-19 |
| **Тип** | `refactor` |
| **Статус** | `TODO` |
| **Агенты** | `/frontend` |
| **Коммит** | — |
| **Зависит от** | BUG-008 |
| **GitHub Issue** | [#66](https://github.com/NKamaev/DocForge/issues/66) |

---

## Постановка задачи (от пользователя)

```
По результатам ревью BUG-008: тест «renders exactly 3 avatars and +2 badge
for 5 remote users» проверяет только наличие плашки +2, но не верифицирует,
что отрендерилось ровно 3 аватара. Регрессию (убрать slice(0,3)) тест не поймает.
```

---

## Интерпретация

В `ParticipantsPanel.test.tsx` тест с 5 remote users проверяет только overflow-плашку `+2`.
Если кто-то уберёт `slice(0, 3)` из компонента, тест всё равно пройдёт, потому что `+2` останется в DOM.
Нужно добавить проверку, что видимых аватаров ровно 3 (не считая текущего пользователя).

---

## Файлы для изучения перед исполнением

```
frontend/src/components/document-editor/ParticipantsPanel.tsx   — логика slice(0,3)
frontend/src/components/document-editor/ParticipantsPanel.test.tsx — тест для исправления
```

---

## Файлы для изменения

```
frontend/src/components/document-editor/ParticipantsPanel.test.tsx
  — в тесте «renders exactly 3 avatars and +2 badge for 5 remote users»
    добавить проверку количества отрендеренных аватаров remote users (ровно 3)
```

---

## Поэтапное исполнение

1. **Добавить `data-testid="participant-avatar"` в `ParticipantsPanel.tsx`** — на каждый remote Avatar-контейнер (не на overflow-плашку и не на аватар текущего пользователя).
2. **Обновить тест** — `expect(screen.getAllByTestId('participant-avatar')).toHaveLength(3)` для сценария с 5 пользователями.
3. Убедиться, что сценарий с 2 пользователями также проверяет `toHaveLength(2)`.

---

## Автоматические тесты

### Что покрыть

| Сценарий | Тип теста | Файл |
|---|---|---|
| 5 remote users → ровно 3 аватара + плашка `+2` | unit | `frontend/src/components/document-editor/ParticipantsPanel.test.tsx` |
| 2 remote users → ровно 2 аватара, без плашки | unit | `frontend/src/components/document-editor/ParticipantsPanel.test.tsx` |

### Команды запуска

```bash
cd frontend && npm test -- --run
```

---

## Чеклист ручного тестирования

- [ ] Открыть документ в 5 вкладках → видны 3 аватара + плашка «+2»
- [ ] Регрессия: тест падает если убрать `slice(0, 3)` из компонента

---

## История

| Дата | Событие |
|---|---|
| 2026-04-19 | Задача создана по результатам ревью BUG-008 |

---

## Завершение задачи

> **Обязательно выполнить перед отчётом о выполнении!**

- [ ] Автотесты запущены и проходят (`npm test`)
- [ ] Ручной чеклист выполнен полностью
- [ ] Код прошёл ревью (`/review`)
- [ ] Git-коммит создан с сообщением: `test(collab): verify avatar count in ParticipantsPanel tests`
- [ ] Статус обновлён в этом файле: `DONE`
- [ ] Реестр обновлён: [docs/tasks/README.md](README.md)
