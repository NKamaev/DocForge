# Участие в разработке

## Рабочий процесс

1. Найди задачу в [Issues](https://github.com/NKamaev/DocForge/issues) или создай новую
2. Создай ветку от `master`: `git checkout -b feat/TASK-NNN-description`
3. Реализуй изменения, следуя конвенциям кода из [`CLAUDE.md`](CLAUDE.md)
4. Убедись, что тесты проходят
5. Создай Pull Request в `master`

## Именование веток

| Тип | Шаблон |
|---|---|
| Фича | `feat/TASK-NNN-short-description` |
| Баг | `fix/BUG-NNN-short-description` |
| Рефакторинг | `refactor/TASK-NNN-short-description` |

## Конвенции коммитов

Используем [Conventional Commits](https://www.conventionalcommits.org/):

```
feat(template): add conditional block collapse lock in published mode
fix(fill): conditional blocks visibility based on questionnaire answers
refactor(collab): atomic debounce in YjsDocumentStoreImpl
docs(tasks): add TASK-NNN description
```

## Код-стайл

- **Backend**: следуй правилам из `backend/README.md`, тонкие контроллеры, логика в сервисах
- **Frontend**: функциональные компоненты, Zustand для стейта, типизация через `types/`
- Не добавляй комментарии, объясняющие *что* делает код — только *почему* (неочевидные решения)

## Тесты

- Backend unit: `*Test.java` (JUnit 5 + Mockito)
- Backend integration: `*IT.java` (Testcontainers)
- Frontend: Vitest + React Testing Library

Новая функциональность должна сопровождаться тестами.
