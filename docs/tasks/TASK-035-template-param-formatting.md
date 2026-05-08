# TASK-035: Форматирование значений в templateParam через ParameterSubstitutor

| Поле | Значение |
|---|---|
| **Дата** | 2026-04-23 |
| **Тип** | `feature` |
| **Статус** | `TODO` |
| **Агенты** | `/backend` |
| **Коммит** | — |
| **Зависит от** | [TASK-029-1](TASK-029-1-parameters-backend.md) |
| **GitHub Issue** | [#70](https://github.com/NKamaev/DocForge/issues/70) |
| **Эпик** | [TASK-029](TASK-029-parameters-and-clause-refs.md) |

---

## Постановка задачи (от пользователя)

```
Код-ревью: templateParam не форматирует значения — даты и числа выводятся сырой строкой
```

---

## Интерпретация

`NodeResolver.resolveTemplateParam()` берёт значение из `answers[fieldId]` и возвращает его строковым представлением без форматирования. Это означает:

- Поле типа `DATE` со значением `"2026-04-23"` → попадёт в DOCX как `"2026-04-23"` вместо `"23 апреля 2026 г."`.
- Поле типа `NUMBER` со значением `1000000` → попадёт как `"1000000"` вместо `"1 000 000"`.
- Поле типа `FIO` → без склонения/сокращения.

Существующий `parameterField` использует `ParameterSubstitutor.format(value, param, formatOverride)` для корректного форматирования. `templateParam` должен делать то же самое при наличии параметра с соответствующим `fieldId`.

---

## Файлы для изучения перед исполнением

```
backend/src/main/java/com/docforge/export/NodeResolver.java        — текущая реализация resolveTemplateParam
backend/src/main/java/com/docforge/document/generator/ParameterSubstitutor.java — логика форматирования
backend/src/main/java/com/docforge/document/DocumentController.java — applyAnswers(), контекст вызова
backend/src/main/java/com/docforge/template/model/Parameter.java    — поля: type, format, options
```

---

## Файлы для изменения

```
backend/src/main/java/com/docforge/export/NodeResolver.java                    — добавить форматирование
backend/src/main/java/com/docforge/document/DocumentController.java            — передать paramIndex в NodeResolver
backend/src/test/java/com/docforge/export/NodeResolverTest.java                — новые тест-сценарии
```

---

## Поэтапное исполнение

1. **Обновить** сигнатуру `NodeResolver.resolveTemplateParam()`:
   ```java
   public Map<String, Object> resolveTemplateParam(
       Map<String, Object> node,
       Map<String, Object> answers,
       Map<String, Parameter> paramIndex,  // ← добавить
       ParameterSubstitutor substitutor    // ← инжектировать или передавать
   )
   ```
2. **Внедрить** `ParameterSubstitutor` в `NodeResolver` как Spring-зависимость (`@Autowired` через конструктор).
3. **Реализовать** форматирование: `fieldId` → `paramIndex.get(fieldId)` → `substitutor.format(value, param)`.
4. **Обновить** вызов в `DocumentController.applyAnswers()`: передать `paramIndex`.
5. **Написать** тесты: `templateParam` с `DATE`-полем → дата в русском формате; `NUMBER` → с разделителями.

---

## Автоматические тесты

### Что покрыть

| Сценарий | Тип теста | Файл |
|---|---|---|
| `templateParam` с `fieldId` типа `DATE` → форматированная дата | unit | `NodeResolverTest.java` |
| `templateParam` с `fieldId` типа `NUMBER` → форматированное число | unit | `NodeResolverTest.java` |
| `templateParam` без соответствующего Parameter → сырое значение (fallback) | unit | `NodeResolverTest.java` |

### Команды запуска

```bash
cd backend && ./gradlew test --tests "com.docforge.export.NodeResolverTest"
cd backend && ./gradlew test
```

---

## Чеклист ручного тестирования

- [ ] Создать шаблон с полем типа `DATE` (например, «Дата договора»).
- [ ] Создать документ, ввести ответ `2026-04-23`.
- [ ] Вставить в тело `templateParam` с `fieldId = "contractDate"`.
- [ ] Экспортировать DOCX → в тексте должно быть «23 апреля 2026 г.», а не `"2026-04-23"`.
- [ ] Аналогично проверить `NUMBER` (например, `1000000` → `"1 000 000"`).
- [ ] Убедиться что обычный `parameterField` по-прежнему работает корректно.

---

## История

| Дата | Событие |
|---|---|
| 2026-04-23 | Задача создана по результатам ревью коммита 16f5208 |

---

## Завершение задачи

> **Обязательно выполнить перед отчётом о выполнении!**

- [ ] Автотесты запущены и проходят (`./gradlew test`)
- [ ] Ручной чеклист выполнен полностью
- [ ] Код прошёл ревью (`/review`)
- [ ] Git-коммит создан: `feat(export): format templateParam values via ParameterSubstitutor`
- [ ] Статус обновлён в этом файле: `DONE`
- [ ] Реестр обновлён: [docs/tasks/README.md](README.md)
- [ ] GitHub Issue закрыт (state: closed) + комментарий с хешем коммита
