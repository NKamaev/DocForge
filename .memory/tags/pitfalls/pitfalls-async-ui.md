# Pitfalls — async UI и утечки

> Тег: `pitfalls:async-ui` · Обновлено: `2026-10-10`

## Класс проблем: promise leak, stale state, silent catch

### TASK-030: ConditionBuilder — утечка промиса, stale state, silent catch
- **Симптомы:**
  1. `Promise.all` продолжает выполнение после закрытия модала и зовёт `setDirFields` на мёртвом компоненте.
  2. При повторном открытии модала `dirFields` не сбрасывается — остаются данные от другого набора параметров.
  3. `catch(() => {})` молча глотает ошибки сети — DIRECTORY-параметр без подполей и без объяснения.
  4. Непоследовательность дефолта: init-`rows` брал `parameters[0]?.code`, а `addRow` — `expandedParams[0]?.code`.
- **Уроки:** защищайся от setState после unmount / после смены входных данных; при модальных загрузках сбрасывай локальные данные на open; никогда не молчи в catch — показывай пользователю причину.
- **Источник:** `docs/archive/tasks/tasks/TASK-030-condition-builder-promise-leak-fixes.md`.

### Холостой setState / dangling debounce в workspace
- Автосейвы `/fill`: answers debounce 500ms + `saveRevision`; body 3s + revision at idle 60s; `saveOnExit()` через `keepalive:true` fetch. Любой новый таймер — очистка в clean-up эффекта, иначе утечки при размонтировании.

<!-- Новые записи добавляй СВЕРХУ. -->