<!-- Вставь этот блок в AGENTS.md целевого проекта. Замени <...> на своё. -->

## Долговременная память (`.memory/`)

Память проекта версионируется в Git и хранится в `.memory/`:
теги — `.memory/tags/{category}/{file}.md`, сводки — `daily/` (YYYY-MM-DD),
`weekly/` (YYYY-Www), `monthly/` (YYYY-MM). Реестр тегов — `.memory/tag-index.md`.

### В начале сессии

1. Прочитай `.memory/tag-index.md` (при большом размере — только релевантные секции).
2. Всегда загружай `best-practices.md` и набор `pitfalls-*.md`.
3. Догрузи теги, релевантные запросу пользователя.
4. При противоречии тега и кода — верь коду, тег обнови/пометь устаревшим.

### В конце сессии

1. Создай/обнови тег тикета или тематический тег; зарегистрируй в `tag-index.md`.
2. Запиши вывод в `.memory/daily/YYYY-MM-DD.md`.
3. Проверь целостность: `.opencode/skills/memory-manager/scripts/memory-lint.sh`.
4. Закоммить: `git add .memory/ && git commit -m "memory: <описание>"`.

### Использование навыка

- opencode: `Skill("memory-manager")` — полная процедура.
- Рантаймы без project-skills (напр. Kimi Code CLI): работай по этому блоку вручную.
