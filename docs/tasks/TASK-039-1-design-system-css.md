# TASK-039-1: Дизайн-система: styles.css, CSS-переменные, шрифты

| Поле | Значение |
|---|---|
| **Дата** | 2026-04-25 |
| **Тип** | `feature` |
| **Статус** | `TODO` |
| **Агенты** | `/frontend` |
| **Коммит** | — |
| **Зависит от** | — |
| **Родитель** | [TASK-039](TASK-039-new-design-handoff.md) |

---

## Постановка задачи

Перенести `styles.css` из handoff-бандла в проект как фундамент дизайн-системы.

---

## Интерпретация

Файл `styles.css` (~39К) содержит полную CSS-систему: reset, переменные брендинга, переменные темы (light/dark), плотности (compact/regular/comfy), типографики (grotesk/serif-display/mono-ui), а также стили всех компонентов приложения. Это единственный источник стилей — вместо Ant Design tokens.

---

## Файлы для изменения

```
frontend/src/styles/design-system.css   — новый файл (копия из бандла)
frontend/index.html                      — добавить Google Fonts link tags
frontend/src/main.tsx                    — импорт design-system.css
frontend/src/App.tsx                     — data-density, data-theme, data-fonts на root div
```

---

## Поэтапное исполнение

1. Скопировать `/tmp/docforge-handoff/docforge/project/styles.css` → `frontend/src/styles/design-system.css`
2. Добавить в `index.html` preconnect + stylesheet для Google Fonts:
   - `Inter+Tight:wght@400;450;500;550;600;700`
   - `Source+Serif+4:wght@400;500;600;700`
   - `JetBrains+Mono:wght@400;500;600`
3. В `main.tsx` добавить `import './styles/design-system.css'` (после существующих импортов)
4. В корневом `div` приложения добавить атрибуты:
   - `data-density="regular"`
   - `data-theme="light"`
   - `data-fonts="grotesk"`
   - `style={{ "--brand-h": 232 }}`
5. Проверить конфликты с Ant Design Reset — при необходимости обернуть Ant Design в свой scope или удалить его глобальные стили

---

## Ключевые CSS-переменные

```css
/* Брендинг */
--brand-h: 232;           /* hue, меняется через inline style */
--brand: oklch(0.55 0.16 var(--brand-h));
--brand-soft: oklch(0.96 0.04 var(--brand-h));
--brand-ink: oklch(0.38 0.12 var(--brand-h));

/* Тема (light) */
--surface: #fff;
--bg-soft: oklch(0.975 0.005 var(--brand-h));
--ink-1: oklch(0.18 0.02 var(--brand-h));
--ink-3: oklch(0.52 0.02 var(--brand-h));
--line: oklch(0.91 0.01 var(--brand-h));

/* data-density="compact" | "regular" | "comfy" */
/* data-fonts="grotesk" | "serif-display" | "mono-ui" */
```

---

## Автоматические тесты

Визуальная проверка — автотесты не требуются для этого шага.

---

## Чеклист ручного тестирования

- [ ] Открыть app → шрифт Inter Tight применён к UI
- [ ] Переменная `--brand` работает (можно проверить в DevTools)
- [ ] Нет явных сломанных стилей от конфликта с Ant Design
- [ ] Страницы не потеряли базовую разметку

---

## История

| Дата | Событие |
|---|---|
| 2026-04-25 | Задача создана |

---

## Завершение задачи

- [ ] Автотесты запущены и проходят (`npm test`)
- [ ] Ручной чеклист выполнен полностью
- [ ] Git-коммит создан: `feat(ui): add design-system css and fonts`
- [ ] Статус обновлён: DONE
- [ ] Реестр обновлён: docs/tasks/README.md
