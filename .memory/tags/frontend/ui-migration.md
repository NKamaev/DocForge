# Frontend — миграция на shadcn/ui + Tailwind

> Тег: `frontend:ui-migration` · Обновлено: `2026-10-10`

## [2026-10-10 10:00] Статус миграции

- **Факт:** `src/components/ui/` — 36 shadcn-модулей, все экспортированы через `index.tsx` (accordion, alert, alert-dialog, avatar, badge, button, calendar, card, checkbox, color-picker, date-picker, dialog, dropdown-menu, hover-card, input, label, multi-select, pagination, popover, progress, radio-group, scroll-area, select, separator, sheet, skeleton, slider, switch, table, tabs, textarea, toast, tooltip и др.).
- `components.json` — стиль `radix-nova`; deps: `radix-ui ^1.4.3`, `shadcn ^4.7.0`, `tw-animate-css`, `class-variance-authority`, `tailwind-merge`, `lucide-react`.
- **Tailwind 4** через `@tailwindcss/postcss`; токены в `tailwind.config.ts` (brand/ink/line/surface), darkMode `[data-theme="dark"]`, шрифт Geist.
- **Остатки AntD:** 1 импорт — `{ FileWordOutlined, FilePdfOutlined, RollbackOutlined }` в `QuestionnairePage.tsx:7` (исп. :510,:529,:600); зависимость `antd ^5.21.5` в package.json:47; matchMedia-mock в `src/test/setup.ts:3-15`. Дальше в `src/` импортов antd нет (проверено grep).
- **План миграции:** `docs/specs/migration-plan.md` (971 строка, по этапам).
- **Источник:** explore frontend report, `frontend/`.

<!-- Новые записи добавляй СВЕРХУ. -->