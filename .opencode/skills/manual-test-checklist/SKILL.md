---
name: manual-test-checklist
description: Creating, editing, and verifying manual test checklists via task-tracker MCP
---

## Сущность

Чеклист — это JSON-массив объектов, каждый вида:

```json
{ "id": "uuid", "text": "описание шага", "checked": false }
```

Хранится в поле `checklist_json` задачи. Отдельного API для чеклистов нет.

## Через MCP (task_update)

Используй стандартный MCP-инструмент `task_update` с параметром `checklist_json`:

```
task_update {
  "id": "TASK-007",
  "checklistJson": "[{\"id\":\"a1\",\"text\":\"Проверить авторизацию\",\"checked\":false},{\"id\":\"a2\",\"text\":\"Проверить валидацию форм\",\"checked\":false}]"
}
```

### Добавление одного пункта к существующему чеклисту

1. Прочитай задачу через `task_get { "id": "TASK-007" }`
2. Распарсь `checklistJson` (если null — пустой массив)
3. Добавь элемент с новым `id` (используй UUID/crypto.randomUUID())
4. Сохрани через `task_update` с обновлённым `checklist_json`

### Отметить пункт выполненным

1. Прочитай задачу, получи `checklistJson`
2. Найди элемент по `id`, поставь `"checked": true`
3. Сохрани через `task_update`

### Удалить пункт

1. Прочитай задачу, получи `checklistJson`
2. Отфильтруй массив, убрав элемент с нужным `id`
3. Сохрани через `task_update`

## Через REST API (напрямую)

```bash
curl -s -X PATCH http://localhost:8765/api/v1/tasks/TASK-007 \
  -H "X-Actor: mcp:claude-code" \
  -H "Content-Type: application/json" \
  -d '{
    "checklistJson": "[{\"id\":\"a1\",\"text\":\"Проверить X\",\"checked\":false}]"
  }'
```

## Quality gate: проверка перед DONE

Перед переводом задачи в `DONE` необходимо:

1. Прочитать задачу через `task_get { "id": "TASK-NNN" }`
2. Получить `checklistJson`
3. **Проверить, что все пункты имеют `checked: true`**
4. Если есть непроверенные пункты — **запретить переход в DONE** и вывести сообщение:
   ```
   ⛔ Невозможно перевести TASK-NNN в DONE: не все пункты чеклиста выполнены.
   Осталось: N пунктов
   ```
5. Если `checklistJson` отсутствует или пустой — разрешить переход (не все задачи требуют ручного тестирования)

## Связанные поля

- `tester_report` (string) — свободный отчёт тестировщика, передаётся аналогично через `task_update { "testerReport": "..." }`
- Чеклист + отчёт видны во вкладке **Testing** в UI (TaskDrawer)

## Типы данных

```typescript
interface ChecklistItem {
  id: string    // UUID
  text: string  // описание шага
  checked: boolean // true/false
}
```
