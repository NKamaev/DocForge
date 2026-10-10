# Frontend — split-panel рабочее пространство (/fill)

> Тег: `frontend:workspace` · Обновлено: `2026-10-10`

## [2026-10-10 10:00] QuestionnairePage и DocumentWorkspace

- **Маршрут:** `/documents/:id/fill` — `components/questionnaire/QuestionnairePage.tsx` (639 строк). Левая панель 420px (вопросы/steps/прогресс), правая — редактируемый документ + тулбар.
- **Константы:** `DEBOUNCE_MS=500` (ответы), `BODY_AUTOSAVE_MS=3000`, `BODY_REVISION_IDLE_MS=60000` (:24-26).
- **Двойной автосейв:** ответы — debounce 500ms + `saveRevision` (:123-136); тело — 3s + редакция при 60s простоя (:138-163); `saveOnExit()` — `keepalive:true` fetch на unmount/beforeunload (:178-207).
- **Бидирекция:** `handleAnswerChange`/`handleMultiAnswerChange` → `answersRef` + `yAnswers.set()` + scheduleSave (:214-228). Удалённый Yjs-observer мержит в локальный state + пере-фетчит ОЛ (:230-248).
- **Клик параметра → вопрос:** `handleParamFocus` — scrollIntoView + переключение страницы + синяя подсветка 2500ms (:311-329).
- **Ошибки загрузки:** `loadError` = `access_denied` (403) / `not_found` (404) / `unknown` (Promise.all из loadQ/loadT) (:342-356).
- **Restore редакции:** очистка Yjs fragment + `yAnswers.clear()`, reload REST, `restoreSeedKey` реюз (:264-309). Прогресс = заполненные видимые ответы / всего видимых (:367-369).
- **Источник:** `frontend/src/components/questionnaire/QuestionnairePage.tsx`.

## DocumentWorkspace (289 строк)

- Редактируемый TipTap: `StarterKit` (undoRedo/link/underline off) + `Collaboration.configure({document: ydoc})` + таблицы + форматирование + `Workspace*Node` варианты + `ClauseRefNode` (:82-109).
- **Seeding guard:** ждёт 400ms WS-синхронизацию; Yjs-контент побеждает REST (`seededRef` :163-190). `renderPreview(templateBody, answers, params, {preserveConditionalBlocks:true, phrasings})`.
- **Гвард рендера:** `if (!templateBody && initialBody == null)` (:192-198) — редактор показывается при наличии `currentBody`.
- Хранилище: callback refs (:74-79), storage + questionnaire sync (:123-135), answers→storage + `updateHiddenPositions` + meta `'answers-updated'` (:138-150), `highlightedParam` (:153-157).
- `formattingToCssVars()` — зеркалит дизайнер: `--page-width/-height`, поля, шрифты, A4/A3/Letter, portrait/landscape (:216-238).
- `updateHiddenPositions()`: скрытие numberedClause из двух источников (скорsever-hidden обёрток + собственный `condition`) через `evaluateSimple` (:251-289).
- **Источник:** `frontend/src/components/questionnaire/DocumentWorkspace.tsx`.

## WorkspaceParameterFieldNode (193 строки)

- Storage-ориентирован: `answers, parameters, questionnaire, onAnswerChange, onParamFocus, highlightedParam` (`WorkspaceParamStorage` :17-24); пере-рендер по transaction meta `'answers-updated'` (:63-73); `[метка]`-чип с бейджингом, лейбл кликабелен → `onParamFocus`; `findParamPage` (:26-38).
- **Источник:** `frontend/src/components/questionnaire/WorkspaceParameterFieldNode.tsx`.

## previewRenderer.ts (424 строки)

- Статическое зеркало нумерации: `RU_ALPHA`/`EN_ALPHA` (:11-12), `formatCounter` (:14-18), `buildStaticLabels` (:20-47, clamp уровня 1-5, `listStart` сброс).
- `evaluateSimple` (:81-113): токен-парсер `paramCode op value [AND|OR]...`; ops `== != > < >= <= in not_in is_empty is_not_empty`.
- `renderNode` (:233-360): numberedClause/phrasingsRef/clauseRef/parameterField/conditionalInline/conditionalBlock; sentinel `__fragment__` для мульти-нода, flatMap (:362-377); `preserveConditionalBlocks` для seeding workspace.
- Публичное API: `renderPreview()` (:407-424) с `showAllConditionals`, `preserveConditionalBlocks`, `phrasings`.
- **Источник:** `frontend/src/utils/previewRenderer.ts`.

## Известные gap-ы & статус TASK-006/059

- TASK-006 (fill save body/load current_body/Yjs) — **реализовано** в коде.
- TASK-059 (ShareDialog «Доступ» на /fill) — **реализовано** (`QuestionnairePage.tsx:481-483, 612-619`).
- Свободные правки тела сохраняются (`updateBody` + revision), Yjs активен.

<!-- Новые записи добавляй СВЕРХУ. -->