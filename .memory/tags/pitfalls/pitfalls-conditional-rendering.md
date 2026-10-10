# Pitfalls — условные блоки (conditional rendering)

> Тег: `pitfalls:conditional-rendering` · Обновлено: `2026-10-10`

## Класс проблем: видимость условных нод на /fill и в экспорте

### BUG-012: все conditional_block видны на /fill
- **Симптом:** одновременно отображаются блоки для «ЮЛ/ИП/ФЛ» независимо от ответа на «Тип клиента».
- **Корень:** компонент рендера /fill не применял условия при инициализации или не получал актуальные ответы. Закрыт (commit `303a535`).
- **Источник:** `docs/archive/tasks/tasks/BUG-012-conditional-blocks-all-visible-on-fill.md`.

### BUG-019: скрытые блоки попадают в DOCX/PDF
- **Симптом:** в выхлопе параметр подставлен (после BUG-018), но conditionalBlock/conditionalInline рендерятся безусловно.
- **Корень:** `renderNode` экспортёров не применял `ConditionEvaluator`.
- **Урок:** экспорт и генерация должны использовать ОДНУ логику фильтрации условий. Закрыт в DocxExportServiceImpl/PdfExportServiceImpl.
- **Источник:** `docs/archive/tasks/tasks/BUG-019-conditional-blocks-visible-in-export.md`, тег `architecture:export`.

### BUG-014: пустая строка после скрытого conditionalInline
- **Симптом:** span с `display:none`, занимавший весь контейнер `<p>`, оставляет пустую строку.
- **Корень:** блочный контейнер остаётся в DOM с ненулевой высотой.
- **Замечание:** `WorkspaceNumberedClauseNode` не подвержен — прячет себя целиком `display:none`; `WorkspaceConditionalBlockNode` регистрирован, но недоступен для вставки из редактора.
- **Источник:** `docs/archive/tasks/tasks/BUG-014-empty-line-after-hidden-conditional-inline.md`.

### BUG-013: два return в NodeView → remount
- **Корень:** `WorkspaceConditionalInlineNode` с двумя `return`-ами разных JSX-деревьев → React unmount/mount при переключении visible. Блочная версия не подвержена (единый return с условным display).
- **Урок (общий для TipTap):** у NodeView ОДИН корневой render с неизменной структурой; скрытие — через style, не через смену дерева.
- **Источник:** `docs/archive/tasks/tasks/BUG-013-inline-node-double-return.md`.

<!-- Новые записи добавляй СВЕРХУ. -->