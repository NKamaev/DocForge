# DocForge

Конструктор юридических и деловых документов с поддержкой шаблонов, условных блоков и совместного редактирования в реальном времени.

## Возможности

- **Шаблоны** — создание шаблонов с условными блоками, переменными и опросными листами
- **Заполнение документов** — заполнение по шаблону с автоматической подстановкой ответов
- **Коллаборация** — совместное редактирование документов в реальном времени (Yjs / WebSocket)
- **История редакций** — автоматическое сохранение редакций с именем автора
- **Экспорт** — выгрузка в DOCX и PDF
- **Справочники** — подключение внешних справочников как источника данных для условий

## Стек технологий

| Слой | Технологии |
|---|---|
| Backend | Java 21, Spring Boot 3.3, Virtual Threads |
| Frontend | React 18, TypeScript, Ant Design, TipTap 2 |
| Совместное редактирование | Yjs, WebSocket |
| БД | PostgreSQL (JPA/Flyway), MongoDB |
| Кеш | Redis |
| Контейнеризация | Docker, Docker Compose |

## Быстрый старт

### Требования

- Java 21+
- Node.js 18+
- Docker & Docker Compose

### Запуск

```bash
# 1. Запустить инфраструктуру (PostgreSQL, MongoDB, Redis)
docker-compose up -d

# 2. Backend
cd backend
./gradlew bootRun

# 3. Frontend
cd frontend
npm install
npm run dev
```

Приложение доступно по адресу: `http://localhost:5173`  
API: `http://localhost:8080`

### Запуск тестов

```bash
# Backend
cd backend && ./gradlew test

# Frontend
cd frontend && npm run test
```

## Архитектура

Модульный монолит. Пакеты: `user`, `template`, `document`, `collaboration`.

Модули взаимодействуют только через интерфейсы `*Service`. Подробная документация:

- [`docs/architecture.md`](docs/architecture.md) — полное ТЗ, модель данных, API-контракты
- [`docs/tasks/README.md`](docs/tasks/README.md) — реестр задач

## Структура проекта

```
DocForge/
├── backend/          # Spring Boot приложение
│   └── src/main/java/com/docforge/
│       ├── user/
│       ├── template/
│       ├── document/
│       └── collaboration/
├── frontend/         # React приложение
│   └── src/
│       ├── api/
│       ├── components/
│       ├── store/
│       └── types/
├── docs/             # Документация и задачи
├── docker-compose.yml
└── Dockerfile
```

## Разработка

Для работы с задачами и специализированными агентами смотри [`CLAUDE.md`](CLAUDE.md).

Активные задачи и баги: [GitHub Issues](https://github.com/NKamaev/DocForge/issues)

## Лицензия

[MIT](LICENSE)
