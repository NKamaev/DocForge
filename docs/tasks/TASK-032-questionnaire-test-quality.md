# TASK-032: Улучшение качества тестов QuestionnairePage

| Поле | Значение |
|---|---|
| **Дата** | 2026-04-19 |
| **Тип** | `refactor` |
| **Статус** | `TODO` |
| **Агенты** | `/frontend` |
| **Коммит** | — |
| **Зависит от** | BUG-006 |
| **GitHub Issue** | [#62](https://github.com/NKamaev/DocForge/issues/62) |

---

## Постановка задачи (от пользователя)

> Результаты код-ревью BUG-006: WARNING и SUGGESTION по тестам `QuestionnairePage.test.tsx`.

---

## Интерпретация

При ревью BUG-006 выявлены три проблемы качества тестов, которые могут привести к нестабильным (flaky) тестам или маскировке будущих ошибок:

1. **WARNING**: `documentsApi.updateBody` мокируется только в отдельных тестах, а не в `beforeEach`. Если другой тест вызовет экспорт без предварительной настройки мока — получит необработанный `undefined` и упадёт с непонятной ошибкой.

2. **SUGGESTION**: `vi.stubGlobal('URL', { createObjectURL, revokeObjectURL })` полностью заменяет глобальный `URL`, что может сломать другие вызовы (`new URL(...)`, `URL.canParse` и т.д.). Безопаснее использовать `vi.spyOn(URL, 'createObjectURL')` + `vi.spyOn(URL, 'revokeObjectURL')`.

3. **SUGGESTION**: `clickSpy.mockRestore()` вызывается в теле теста, а не в `afterEach`. Если тест упадёт до этой строки — шпион останется активным и испортит следующие тесты. Нужно перенести в `afterEach` или добавить `vi.restoreAllMocks()` в `afterEach`.

---

## Файлы для изменения

```
frontend/src/components/questionnaire/QuestionnairePage.test.tsx
  — добавить documentsApi.updateBody в beforeEach
  — заменить vi.stubGlobal('URL', ...) на vi.spyOn(URL, ...)
  — перенести clickSpy.mockRestore() в afterEach
```

---

## Поэтапное исполнение

1. В `beforeEach`: добавить `vi.mocked(documentsApi.updateBody).mockResolvedValue(undefined)`.
2. В тестах exportDocx/exportPdf: заменить `vi.stubGlobal('URL', ...)` на:
   ```ts
   vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:test-...')
   vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {})
   ```
3. Убрать `clickSpy.mockRestore()` из тела тестов. В `afterEach` заменить `vi.clearAllMocks()` на `vi.restoreAllMocks()` (или добавить оба вызова).
4. Запустить `npm test -- --run`, убедиться что все тесты проходят.

---

## Автоматические тесты

### Команды запуска

```bash
cd frontend && npm test -- --run
```

---

## Чеклист ручного тестирования

- [ ] `npm test -- --run` — все тесты зелёные
- [ ] Регрессия: остальные тест-файлы не затронуты

---

## История

| Дата | Событие |
|---|---|
| 2026-04-19 | Задача создана по результатам ревью BUG-006 |

---

## Завершение задачи

> **Обязательно выполнить перед отчётом о выполнении!**

- [ ] Автотесты запущены и проходят (`npm test`)
- [ ] Ручной чеклист выполнен полностью
- [ ] Код прошёл ревью (`/review`)
- [ ] Git-коммит создан: `test(questionnaire): fix mock stability in QuestionnairePage tests`
- [ ] Статус обновлён: DONE
- [ ] Реестр обновлён: docs/tasks/README.md
- [ ] GitHub Issue закрыт (state: closed) + комментарий с хешем коммита
