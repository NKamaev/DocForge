

# DocForge — Техническое задание и Архитектура

> **Версия:** 1.0 MVP
> **Подход к разработке:** Вайбкодинг на Claude Code
> **Язык backend:** Java 21 + Spring Boot 3.x
> **Язык frontend:** TypeScript + React

---

## 1. Общее описание

### 1.1 Назначение

Веб-приложение для автоматизированной генерации документов на основе параметризованных шаблонов. Администратор (или контент-менеджер) создаёт шаблон документа в no-code конструкторе, размечает в тексте параметры и условия вариативности, привязывает их к опросному листу. Конечный пользователь выбирает шаблон, отвечает на вопросы, получает готовый документ, при необходимости дорабатывает его во встроенном редакторе и выгружает в DOCX или PDF.

### 1.2 Ключевые сценарии

**Сценарий A — Создание шаблона (администратор):**
1. Открывает визуальный конструктор шаблонов (no-code).
2. Набирает или вставляет текст документа.
3. Выделяет фрагменты текста и вставляет параметрические поля: `{{client_name}}`, `{{contract_date}}` и т.д.
4. Задаёт типы параметров: текст, число, дата, ФИО, выбор из списка.
5. Создаёт опросный лист — список вопросов, привязанных к параметрам.
6. Настраивает вариативность: условные блоки текста, условные вопросы (через Python-выражения или визуальный конструктор условий).
7. Публикует шаблон.

**Сценарий B — Заполнение документа (пользователь):**
1. Выбирает шаблон из каталога.
2. Открывается split-panel рабочее пространство (`/documents/:id/fill`): слева — опросный лист, справа — полноценный редактор документа.
3. Отвечает на вопросы; параметры в документе обновляются мгновенно (через `editor.storage` + no-op transaction, без обращения к серверу).
4. Параметры-справочники выбираются из готовых записей; выбранные поля автоматически распространяются в связанные параметры (`paramCode__fieldKey`).
5. Параметры в документе кликабельны: клик → переход к вопросу в ОЛ + подсветка. Ввод в поле параметра → синхронизируется в ОЛ (бидиректальная навигация).
6. Помимо параметрических полей, пользователь может свободно редактировать текст документа прямо в правой панели.
7. Выгружает финальный документ в DOCX или PDF.

### 1.3 Границы MVP

**Входит в MVP:**
- Конструктор шаблонов (no-code визуальный редактор)
- Параметрические поля 7 типов (текст, число, дата, ФИО, boolean, выбор из списка, справочник)
- Справочники: пользовательские реестры с настраиваемой схемой полей
- Опросный лист с привязкой вопросов к параметрам
- Вариативность текста и вопросов (Python-скрипты + визуальный конструктор простых условий)
- WYSIWYG-редактор с форматированием (шрифт, размер, жирный, курсив, списки, таблицы)
- Совместное редактирование (real-time, несколько пользователей)
- Экспорт DOCX и PDF
- Базовая аутентификация (логин/пароль)
- Docker-контейнеризация

**Вне MVP (запланировано):**
- Ролевая модель с гранулярными правами (абзац, вопрос)
- Статусная модель документа
- Настройка отображения элементов интерфейса
- Версионирование шаблонов и документов
- Импорт шаблонов из DOCX

---

## 2. Архитектура

### 2.1 Принципы, оптимизированные под вайбкодинг

Архитектура спроектирована так, чтобы Claude Code мог эффективно работать с каждым модулем:

1. **Монорепо, модульный монолит** — не микросервисы. Один Spring Boot-проект с чётким разделением на пакеты. Claude Code работает быстрее, когда весь контекст в одном репозитории и не нужно переключаться между проектами.

2. **Тонкие контроллеры, толстые сервисы** — бизнес-логика сосредоточена в сервисном слое. Контроллеры только маршрутизируют. Это позволяет Claude Code генерировать логику в одном месте.

3. **Один файл — одна ответственность, но не мельчить** — файлы достаточно крупные, чтобы Claude Code видел контекст, но не слишком большие (до ~300 строк).

4. **Явные DTO-контракты** — каждый API-эндпоинт имеет чётко типизированные request/response DTO. Claude Code по ним сгенерирует и фронт, и бэк.

5. **Конвенции вместо конфигурации** — единый стиль именования, стандартные паттерны. Чем больше конвенций, тем предсказуемее генерация.

6. **README в каждом пакете** — краткое описание ответственности, ключевых классов и зависимостей. Claude Code читает их для контекста.

### 2.2 Высокоуровневая архитектура

```
┌──────────────────────────────────────────────────────────┐
│                    БРАУЗЕР                                │
│  ┌────────────┐ ┌──────────────┐ ┌─────────────────────┐ │
│  │ Template   │ │ Questionnaire│ │ Document Editor     │ │
│  │ Designer   │ │ UI           │ │ (TipTap + Yjs)      │ │
│  │ (no-code)  │ │              │ │                     │ │
│  └──────┬─────┘ └──────┬───────┘ └──────┬──────────────┘ │
│         └──────────────┼────────────────┘                 │
│                   REST │ WebSocket                        │
└────────────────────────┼─────────────────────────────────┘
                         │
┌────────────────────────┼─────────────────────────────────┐
│            SPRING BOOT МОНОЛИТ (Java 21)                 │
│                        │                                  │
│  ┌─────────────────────▼───────────────────────────────┐ │
│  │              Web Layer (Controllers)                 │ │
│  │   REST API  ·  WebSocket Handlers  ·  SSE           │ │
│  └──────┬──────────────┬──────────────────┬────────────┘ │
│         │              │                  │               │
│  ┌──────▼──────┐ ┌─────▼───────┐ ┌───────▼───────────┐  │
│  │  Template   │ │  Document   │ │  Collaboration     │  │
│  │  Module     │ │  Module     │ │  Module            │  │
│  │             │ │             │ │                    │  │
│  │ · Шаблоны   │ │ · Генерация │ │ · Yjs WebSocket   │  │
│  │ · Опросные  │ │ · Экспорт   │ │ · Awareness       │  │
│  │   листы     │ │ · Правки    │ │ · Курсоры         │  │
│  │ · Параметры │ │             │ │                    │  │
│  └──────┬──────┘ └──────┬──────┘ └───────┬───────────┘  │
│                                                           │
│  ┌────────────────────────────────────────────────────┐   │
│  │  Directory Module                                   │   │
│  │  · Справочники (схема + записи)                    │   │
│  │  · PostgreSQL: directory_registry, schema_fields   │   │
│  │  · MongoDB: entries (embedded array)               │   │
│  └────────────────────────────────────────────────────┘   │
│         │              │                  │               │
│  ┌──────▼──────────────▼──────────────────▼───────────┐  │
│  │              Script Engine                          │  │
│  │         GraalPython (sandbox)                       │  │
│  │   Исполнение условий вариативности                  │  │
│  └──────┬─────────────────────────────────┬───────────┘  │
│         │                                 │               │
│  ┌──────▼──────┐                 ┌────────▼──────────┐   │
│  │ PostgreSQL  │                 │    MongoDB         │   │
│  │             │                 │                    │   │
│  │ · users     │                 │ · templates        │   │
│  │ · sessions  │                 │ · documents        │   │
│  │ · audit_log │                 │ · questionnaires   │   │
│  │ · metadata  │                 │ · crdt_state       │   │
│  │ · directory_│                 │ · directories      │   │
│  │   registry  │                 │   (entries)        │   │
│  └─────────────┘                 └────────────────────┘   │
│                                                           │
│  ┌────────────────────────────────────────────────────┐   │
│  │                    Redis                            │   │
│  │   · WebSocket sessions  · Script cache              │   │
│  │   · Locks (distributed) · Pub/Sub (collab)          │   │
│  └────────────────────────────────────────────────────┘   │
└───────────────────────────────────────────────────────────┘
```

### 2.3 Почему модульный монолит, а не микросервисы

Для MVP и вайбкодинга монолит выигрывает по нескольким причинам:
- Claude Code работает в одном репозитории — не нужно менять контекст между проектами.
- Нет накладных расходов на межсервисное взаимодействие, service discovery, трассировку.
- Транзакции между модулями — простые вызовы методов.
- При необходимости монолит разрезается на сервисы по границам пакетов.

Модули общаются через интерфейсы сервисного слоя, не через прямой импорт репозиториев друг друга. Это обеспечит будущую разрезаемость.

### 2.4 Стек технологий

| Компонент | Технология | Зачем именно эта |
|-----------|-----------|------------------|
| Backend runtime | Java 21 | Требование. Virtual threads для WebSocket. |
| Framework | Spring Boot 3.3+ | Стандарт Java-экосистемы, Spring WebSocket из коробки. |
| Build | Gradle (Kotlin DSL) | Лучше поддержка модульности, чем Maven. Claude Code хорошо работает с `build.gradle.kts`. |
| СУБД (реляц.) | PostgreSQL 16 | Пользователи, аудит, метаданные. ACID. |
| СУБД (документ.) | MongoDB 7 | Шаблоны и документы — глубоко вложенные JSON-структуры. Гибкая схема. |
| Кэш / pub-sub | Redis 7 | Сессии WebSocket, distributed lock, pub/sub для Yjs. |
| Script engine | GraalPython (GraalVM) | Python-скрипты из JVM. Sandboxed, без внешних процессов. |
| Frontend | React 18 + TypeScript | Компонентная архитектура, экосистема. |
| Редактор | TipTap 3 (ProseMirror) | WYSIWYG с коллаборацией. Кастомные ноды для параметров. |
| Real-time sync | Yjs + y-websocket | CRDT для конфликт-свободной синхронизации. Нативная интеграция с TipTap. |
| UI-компоненты | Ant Design 5 | Готовые формы, таблицы, модалки. Быстрая генерация через Claude Code. |
| Экспорт DOCX | Apache POI 5 (XWPF) | Java-нативная генерация из TipTap JSON. |
| Экспорт PDF | openhtmltopdf + PDFBox | TipTap JSON → HTML → PDF. |
| Хранение изображений | Локальная ФС (`./uploads/images`) | POST /api/uploads/images, GET /api/uploads/images/{filename}. |
| Контейнеры | Docker + Docker Compose | Требование. Один `docker-compose.yml` поднимает всё. |

---

## 3. Модель данных

### 3.1 PostgreSQL — пользователи и метаданные

```sql
-- =============================================
-- Пользователи
-- =============================================
CREATE TABLE users (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    login         VARCHAR(100) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name  VARCHAR(255) NOT NULL,
    email         VARCHAR(255),
    is_admin      BOOLEAN DEFAULT FALSE,
    created_at    TIMESTAMPTZ DEFAULT now(),
    updated_at    TIMESTAMPTZ DEFAULT now()
);

-- =============================================
-- Реестр шаблонов (поиск, каталог, права)
-- Полное тело шаблона — в MongoDB
-- =============================================
CREATE TABLE template_registry (
    id            UUID PRIMARY KEY,           -- совпадает с _id в MongoDB
    code          VARCHAR(100) UNIQUE,        -- человекочитаемый код
    name          VARCHAR(500) NOT NULL,
    description   TEXT,
    category      VARCHAR(100),
    version       INT DEFAULT 1,
    is_published  BOOLEAN DEFAULT FALSE,
    owner_id      UUID REFERENCES users(id),
    created_at    TIMESTAMPTZ DEFAULT now(),
    updated_at    TIMESTAMPTZ DEFAULT now()
);

-- =============================================
-- Реестр документов
-- =============================================
CREATE TABLE document_registry (
    id            UUID PRIMARY KEY,           -- совпадает с _id в MongoDB
    template_id   UUID REFERENCES template_registry(id),
    name          VARCHAR(500) NOT NULL,
    owner_id      UUID REFERENCES users(id),
    status        VARCHAR(50) DEFAULT 'DRAFT',
    created_at    TIMESTAMPTZ DEFAULT now(),
    updated_at    TIMESTAMPTZ DEFAULT now()
);

-- =============================================
-- Участники документа (для совместной работы)
-- =============================================
CREATE TABLE document_participants (
    document_id   UUID REFERENCES document_registry(id) ON DELETE CASCADE,
    user_id       UUID REFERENCES users(id),
    role          VARCHAR(50) DEFAULT 'EDITOR', -- OWNER, EDITOR, VIEWER
    joined_at     TIMESTAMPTZ DEFAULT now(),
    PRIMARY KEY (document_id, user_id)
);

-- =============================================
-- Аудит-лог (кто что делал)
-- =============================================
CREATE TABLE audit_log (
    id            BIGSERIAL PRIMARY KEY,
    user_id       UUID REFERENCES users(id),
    entity_type   VARCHAR(50) NOT NULL,       -- TEMPLATE, DOCUMENT
    entity_id     UUID NOT NULL,
    action        VARCHAR(50) NOT NULL,       -- CREATE, UPDATE, EXPORT, etc.
    details       JSONB,
    created_at    TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX idx_audit_entity ON audit_log(entity_type, entity_id);
CREATE INDEX idx_audit_user   ON audit_log(user_id, created_at);
```

### 3.2 MongoDB — шаблоны, документы, опросные листы

#### Коллекция `templates`

```jsonc
{
  "_id": "uuid-шаблона",          // совпадает с template_registry.id
  "version": 1,

  // ── Параметры шаблона ──
  "parameters": [
    {
      "code": "client_name",       // уникальный код параметра
      "label": "ФИО клиента",
      "type": "fio",               // text | number | date | fio | boolean | select
      "required": true,
      "default_value": null,
      "options": null,              // для type=select: ["ООО", "ИП", "АО"]
      "validation": {              // опциональная валидация
        "min": null,
        "max": null,
        "pattern": null
      }
    },
    {
      "code": "contract_type",
      "label": "Тип договора",
      "type": "select",
      "required": true,
      "options": ["Поставка", "Услуги", "Подряд"]
    },
    {
      "code": "contract_date",
      "label": "Дата договора",
      "type": "date",
      "required": true,
      "default_value": "$TODAY"     // спец. значения: $TODAY, $TOMORROW
    },
    {
      "code": "has_penalty",
      "label": "Предусмотрена неустойка",
      "type": "boolean",
      "default_value": false
    },
    {
      "code": "penalty_percent",
      "label": "Процент неустойки",
      "type": "number",
      "required": false,            // зависит от has_penalty
      "validation": { "min": 0, "max": 100 }
    }
  ],

  // ── Опросный лист ──
  "questionnaire": {
    "title": "Параметры договора",
    "pages": [
      {
        "title": "Стороны договора",
        "questions": [
          {
            "id": "q1",
            "parameter_code": "client_name",    // привязка к параметру
            "label": "Укажите ФИО или наименование клиента",
            "hint": "Для юр. лица — полное наименование с ОПФ",
            "order": 1,
            "visibility_condition": null         // всегда видим
          },
          {
            "id": "q2",
            "parameter_code": "contract_type",
            "label": "Выберите тип договора",
            "order": 2,
            "visibility_condition": null
          }
        ]
      },
      {
        "title": "Условия",
        "questions": [
          {
            "id": "q3",
            "parameter_code": "has_penalty",
            "label": "Предусмотрена ли неустойка?",
            "order": 1,
            "visibility_condition": null
          },
          {
            "id": "q4",
            "parameter_code": "penalty_percent",
            "label": "Укажите процент неустойки",
            "order": 2,
            // ── Вариативность: вопрос виден только если has_penalty == true ──
            "visibility_condition": {
              "type": "simple",                  // simple | python
              "expression": "has_penalty == true"
            }
          }
        ]
      }
    ]
  },

  // ── Тело шаблона (TipTap JSON) ──
  // Хранится как ProseMirror-совместимый JSON
  "body": {
    "type": "doc",
    "content": [
      {
        "type": "heading",
        "attrs": { "level": 1 },
        "content": [{ "type": "text", "text": "ДОГОВОР" }]
      },
      {
        "type": "paragraph",
        "content": [
          { "type": "text", "text": "г. Москва, " },
          {
            // Кастомный нод TipTap — параметрическое поле
            "type": "parameter_field",
            "attrs": {
              "parameter_code": "contract_date",
              "display": "дата договора"
            }
          }
        ]
      },
      {
        "type": "paragraph",
        "content": [
          { "type": "text", "text": "Заказчик: " },
          {
            "type": "parameter_field",
            "attrs": {
              "parameter_code": "client_name",
              "display": "ФИО клиента"
            }
          }
        ]
      },
      {
        // ── Условный блок текста ──
        "type": "conditional_block",
        "attrs": {
          "condition": {
            "type": "simple",
            "expression": "has_penalty == true"
          },
          "label": "Блок неустойки"
        },
        "content": [
          {
            "type": "paragraph",
            "content": [
              { "type": "text", "text": "В случае нарушения сроков Заказчик уплачивает неустойку в размере " },
              {
                "type": "parameter_field",
                "attrs": { "parameter_code": "penalty_percent", "display": "%" }
              },
              { "type": "text", "text": "% от стоимости договора." }
            ]
          }
        ]
      },
      {
        // ── Условный блок с Python-скриптом ──
        "type": "conditional_block",
        "attrs": {
          "condition": {
            "type": "python",
            "script_id": "penalty_progressive_block",
            "script": "result = contract_type == 'Подряд' and has_penalty and penalty_percent > 10"
          },
          "label": "Прогрессивная неустойка (подряд)"
        },
        "content": [
          {
            "type": "paragraph",
            "content": [
              { "type": "text", "text": "При просрочке свыше 30 дней неустойка удваивается." }
            ]
          }
        ]
      }
    ]
  },

  // ── Форматирование документа ──
  "formatting": {
    "page_size": "A4",              // "A4" | "A3" | "Letter"
    "orientation": "portrait",      // "portrait" | "landscape"
    "margins": { "top": 20, "right": 15, "bottom": 20, "left": 30 },  // мм
    "default_font": "Times New Roman, serif",
    "default_font_size": 14,        // pt
    "line_spacing": 1.5             // множитель межстрочного интервала
  },

  // ── Python-скрипты (библиотека) ──
  "scripts": [
    {
      "id": "penalty_progressive_block",
      "name": "Прогрессивная неустойка",
      "code": "result = contract_type == 'Подряд' and has_penalty and penalty_percent > 10",
      "description": "Показывать блок прогрессивной неустойки для подряда с % > 10"
    }
  ]
}
```

#### Коллекция `documents`

```jsonc
{
  "_id": "uuid-документа",
  "template_id": "uuid-шаблона",
  "template_version": 1,

  // ── Ответы на опросный лист ──
  "answers": {
    "client_name": "Иванов Иван Иванович",
    "contract_type": "Подряд",
    "contract_date": "2026-03-20",
    "has_penalty": true,
    "penalty_percent": 15
  },

  // ── Результат генерации (TipTap JSON) ──
  // После генерации — параметры подставлены, условия разрешены
  // Далее пользователь может редактировать
  "generated_body": { /* TipTap JSON — результат рендеринга шаблона */ },

  // ── Текущее состояние документа (с правками пользователя) ──
  // Обновляется через Yjs CRDT
  "current_body": { /* TipTap JSON — актуальная версия */ },

  // ── Yjs state vector (для восстановления коллаборации) ──
  "yjs_state": "base64-encoded Yjs document state",

  // ── Мета ──
  "created_at": "2026-03-20T10:00:00Z",
  "updated_at": "2026-03-20T12:30:00Z"
}
```

### 3.3 Redis — структуры

```
# Активные WebSocket-сессии документа
collab:doc:{docId}:users          → SET of userId
collab:doc:{docId}:awareness      → HASH userId → JSON {cursor, selection, name, color}

# Distributed lock для генерации документа
lock:doc:generate:{docId}         → STRING userId (TTL 30s)

# Кэш скомпилированных Python-скриптов
script:cache:{scriptHash}         → STRING compiled bytecode (TTL 1h)

# Pub/Sub каналы (для кластера)
channel:collab:{docId}            → Yjs update messages
```

### 3.4 PostgreSQL — Справочники

```sql
-- Реестр справочников (метаданные + схема)
CREATE TABLE directory_registry (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name       VARCHAR(500) NOT NULL,
    owner_id   UUID REFERENCES users(id),
    created_at TIMESTAMPTZ DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now()
);

-- Поля схемы справочника (порядок, ключ, метка)
CREATE TABLE directory_schema_field (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    directory_id UUID NOT NULL REFERENCES directory_registry(id) ON DELETE CASCADE,
    field_key    VARCHAR(100) NOT NULL,
    label        VARCHAR(500),
    display_order INT DEFAULT 0,
    UNIQUE (directory_id, field_key)
);
```

### 3.5 MongoDB — Записи справочников

#### Коллекция `directories`

```jsonc
{
  "_id": "uuid-справочника",      // совпадает с directory_registry.id
  "entries": [
    {
      "id": "uuid-записи",
      "values": {
        "name":   "Сбербанк",
        "bik":    "044525225",
        "account": "30101810400000000225"
      }
    }
  ]
}
```

**Модель параметра типа DIRECTORY в шаблоне:**

```jsonc
{
  "code": "bank",
  "label": "Банк",
  "type": "DIRECTORY",
  "directory_id": "uuid-справочника-банков",
  "directory_fields": ["name", "bik"]  // поля, доступные в теле шаблона
}
```

При вставке в тело шаблона каждое поле образует отдельный `parameter_field`:
- `bank` — идентификатор записи (не вставляется в текст напрямую)
- `bank__name` → поле «Название банка»
- `bank__bik` → поле «БИК»

При заполнении ОЛ выбор записи из справочника атомарно устанавливает `bank`, `bank__name`, `bank__bik` и т.д.

---

## 4. API-контракт

### 4.1 REST API — Шаблоны

```yaml
# --- Каталог шаблонов ---
GET    /api/templates                    # Список шаблонов (с пагинацией и фильтрами)
GET    /api/templates/{id}               # Полный шаблон (тело + опросный лист + скрипты)
POST   /api/templates                    # Создать шаблон
PUT    /api/templates/{id}               # Обновить шаблон
DELETE /api/templates/{id}               # Удалить шаблон
POST   /api/templates/{id}/publish       # Опубликовать
POST   /api/templates/{id}/unpublish     # Снять с публикации

# --- Экспорт шаблона ---
POST   /api/templates/{id}/export/docx  # Экспорт в DOCX (Content-Type: application/vnd.openxmlformats...)
POST   /api/templates/{id}/export/pdf   # Экспорт в PDF (Content-Type: application/pdf)

# --- Валидация шаблона ---
POST   /api/templates/{id}/validate      # Проверить целостность (параметры, условия, скрипты)

# --- Скрипты ---
POST   /api/templates/{id}/scripts/test  # Тестовый запуск скрипта с mock-данными
```

### 4.1б REST API — Загрузка изображений

```yaml
POST   /api/uploads/images              # Загрузить изображение (multipart/form-data, поле: file)
                                        # Ответ: { "id": "uuid", "url": "/api/uploads/images/uuid.ext" }
                                        # Ограничения: JPEG/PNG/GIF/WebP, max 10 МБ

GET    /api/uploads/images/{filename}   # Получить изображение по имени файла
```

### 4.2 REST API — Документы

```yaml
# --- CRUD ---
GET    /api/documents                    # Мои документы (список)
POST   /api/documents                    # Создать документ из шаблона
GET    /api/documents/{id}               # Получить документ
DELETE /api/documents/{id}               # Удалить документ

# --- Опросный лист ---
GET    /api/documents/{id}/questionnaire           # Текущее состояние опросного листа (с учётом видимости)
PUT    /api/documents/{id}/questionnaire/answers    # Сохранить ответы

# --- Генерация ---
POST   /api/documents/{id}/generate      # Сгенерировать документ по ответам

# --- Экспорт ---
GET    /api/documents/{id}/export/docx   # Выгрузить DOCX
GET    /api/documents/{id}/export/pdf    # Выгрузить PDF

# --- Участники ---
GET    /api/documents/{id}/participants         # Список участников
POST   /api/documents/{id}/participants         # Добавить участника
DELETE /api/documents/{id}/participants/{userId} # Убрать участника
```

### 4.3 WebSocket — Совместное редактирование

```yaml
# Подключение к документу
WS /ws/collab/{documentId}

# Протокол: бинарный Yjs sync protocol
# Сообщения:
#   - sync step 1 (state vector)
#   - sync step 2 (update)
#   - update (incremental)
#   - awareness (курсоры, выделения)
```

### 4.4 Ключевые DTO

```java
// --- Создание документа ---
record CreateDocumentRequest(
    UUID templateId,
    String name
) {}

record CreateDocumentResponse(
    UUID id,
    String name,
    QuestionnaireDTO questionnaire
) {}

// --- Ответы на опросный лист ---
record SaveAnswersRequest(
    Map<String, Object> answers    // code → value
) {}

record SaveAnswersResponse(
    Map<String, Object> answers,
    QuestionnaireDTO questionnaire  // пересчитанный (с учётом visibility)
) {}

// --- Опросный лист (с динамической видимостью) ---
record QuestionnaireDTO(
    String title,
    List<PageDTO> pages
) {}

record PageDTO(
    String title,
    List<QuestionDTO> questions
) {}

record QuestionDTO(
    String id,
    String parameterCode,
    String label,
    String hint,
    String type,           // text, number, date, fio, boolean, select
    List<String> options,  // для select
    boolean visible,       // результат вычисления visibility_condition
    boolean required,
    Object currentValue
) {}

// --- Генерация ---
record GenerateResponse(
    UUID documentId,
    Object body            // TipTap JSON
) {}

// --- Вопрос с полями справочника (QuestionDTO) ---
record QuestionDTO(
    String id,
    String parameterCode,
    String label,
    String hint,
    String type,              // text, number, date, fio, boolean, select, directory
    List<String> options,
    boolean visible,
    boolean required,
    Object currentValue,
    String directoryId,       // только для type=directory
    List<String> directoryFields  // поля, доступные для подстановки
) {}
```

### 4.5 REST API — Справочники

```yaml
GET    /api/directories              # список справочников
POST   /api/directories              # создать справочник
GET    /api/directories/{id}         # получить справочник + поля схемы + записи
PUT    /api/directories/{id}         # обновить название/схему
DELETE /api/directories/{id}         # удалить

POST   /api/directories/{id}/entries          # добавить запись
PUT    /api/directories/{id}/entries/{eid}    # обновить запись
DELETE /api/directories/{id}/entries/{eid}    # удалить запись
```

---

## 5. Ключевые подсистемы — детали реализации

### 5.1 Механизм вариативности

Вариативность — центральная фича системы. Она работает на двух уровнях:

**Уровень 1 — Видимость вопросов опросного листа:**
Каждый вопрос может иметь `visibility_condition`. Если условие ложно — вопрос скрывается, а его параметр принимает значение `null`.

**Уровень 2 — Условные блоки текста:**
В теле шаблона могут быть `conditional_block` ноды. При генерации документа их условия вычисляются, и блок либо включается в итоговый текст, либо нет.

**Два способа задания условий:**

```
┌─────────────────────────────────────────────────────────┐
│           Визуальный конструктор (no-code)               │
│                                                          │
│   ┌──────────┐   ┌────┐   ┌──────────────┐              │
│   │ has_penalty│ = │true│   │  AND / OR     │             │
│   └──────────┘   └────┘   └──────────────┘              │
│                                                          │
│   Простые условия: ==, !=, >, <, >=, <=, in, not_in     │
│   Логические связки: AND, OR, NOT                        │
│   Сохраняется как type: "simple"                         │
└─────────────────────────┬───────────────────────────────┘
                          │
                          │  НЕ хватает? Переключись на Python
                          ▼
┌─────────────────────────────────────────────────────────┐
│              Python-скрипт (pro-mode)                    │
│                                                          │
│   # Доступны все параметры как переменные                │
│   # Результат — переменная `result` (bool)               │
│                                                          │
│   if contract_type == 'Подряд':                          │
│       result = has_penalty and penalty_percent > 10      │
│   else:                                                  │
│       result = False                                     │
│                                                          │
│   Сохраняется как type: "python"                         │
└─────────────────────────────────────────────────────────┘
```

**Безопасность Python-скриптов (GraalPython sandbox):**

```java
// Конфигурация GraalPython Context
Context context = Context.newBuilder("python")
    .allowIO(false)                    // нет доступа к файловой системе
    .allowNativeAccess(false)          // нет нативных вызовов
    .allowCreateThread(false)          // нет новых потоков
    .allowHostAccess(HostAccess.NONE)  // нет доступа к Java-классам
    .option("python.PythonPath", "")   // нет внешних модулей
    .build();

// Установка переменных из параметров
Value bindings = context.getBindings("python");
answers.forEach(bindings::putMember);

// Исполнение с таймаутом
context.eval("python", scriptCode);
Value result = bindings.getMember("result");
boolean conditionMet = result.asBoolean();
```

Таймаут исполнения скрипта — 2 секунды. При превышении — ошибка с указанием скрипта.

### 5.2 Конструктор шаблонов (no-code)

Визуальный конструктор построен на **TipTap 3** (ProseMirror) с полным набором форматирования и кастомными расширениями.

**Стандартные расширения TipTap (подключены):**
- `StarterKit` — базовый набор: параграфы, заголовки H1–H3, списки (ul/ol), цитата, code
- `Strike`, `Subscript`, `Superscript` — надстрочный/подстрочный/зачёркнутый
- `TextStyle`, `Color`, `Highlight` — цвет и выделение текста
- `FontFamily`, `FontSizeExtension` — шрифт и размер (через TextStyle marks)
- `LineSpacingExtension` — межстрочный интервал (через стиль блока)
- `TextAlign` — выравнивание (left/center/right/justify)
- `Link` — гиперссылки
- `HorizontalRule` — горизонтальная разделительная линия
- `Image` — вставка изображений (по URL или загрузка через API)
- `IndentExtension` — отступы абзаца (Tab/Shift+Tab, до 5 уровней)
- `SearchReplaceExtension` — поиск и замена (кастомный ProseMirror плагин с декорациями)
- `Table`, `TableRow`, `TableCell`, `TableHeader` — таблицы с изменением размера
- `PageBreakNode` — принудительный разрыв страницы (`Ctrl+Enter`)
- `SlashCommandExtension` — slash-команды (триггер `/`, 12 команд)

**Кастомные ноды TipTap:**

```typescript
// 1. Параметрическое поле — inline-нод
const ParameterField = Node.create({
  name: 'parameter_field',
  group: 'inline',
  inline: true,
  atom: true,   // неделимый
  attrs: {
    parameter_code: { default: '' },
    display: { default: '' },
  },
  // Рендерится как подсвеченный тег: [ФИО клиента]
});

// 2. Условный блок — block-нод
const ConditionalBlock = Node.create({
  name: 'conditional_block',
  group: 'block',
  content: 'block+',
  attrs: {
    condition: { default: { type: 'simple', expression: '' } },
    label: { default: '' },
    collapsed: { default: false },  // коллапс-режим (компактная шапка)
  },
  // Рендерится с цветной рамкой и индикатором условия; можно свернуть
});

// 3. Нумерованный пункт — block-нод с автонумерацией
const NumberedClause = Node.create({
  name: 'numbered_clause',
  group: 'block',
  content: 'block+',
  attrs: {
    level:          { default: 1 },          // 1–5, уровень вложенности
    numberingStyle: { default: 'decimal' },  // 'decimal' | 'alpha-ru' | 'alpha-en'
    listStart:      { default: false },      // true → сброс счётчиков с этого пункта
    id:             { default: null },       // UUID, стабильный идентификатор для ссылок
  },
  // Номер НЕ хранится в attrs — вычисляется AutoNumberingPlugin динамически.
  // Алгоритм: обход doc → counters[level] + levelStyles[level] → label "1.а.3."
  // listStart: true сбрасывает все counters[0..4] до инкремента.
});

// 4. Inline-условие — фрагмент текста с условием внутри абзаца
const ConditionalInline = Node.create({
  name: 'conditional_inline',
  group: 'inline',
  inline: true,
  content: 'inline*',
  attrs: {
    condition: { default: { type: 'simple', expression: '' } },
    label: { default: 'Условный текст' },
  },
  // Рендерится с жёлтой подсветкой и иконкой ⚡
});

// 5. Ссылка на нумерованный пункт — inline-атом
const ClauseRef = Node.create({
  name: 'clause_ref',
  group: 'inline',
  inline: true,
  atom: true,   // неделимый
  attrs: {
    refId: { default: '' },  // совпадает с id нода numbered_clause
  },
  // Отображает "п. 1.а." — читает label из AutoNumberingPlugin по refId.
  // При удалении referenced пункта показывает "???".
});
```

**AutoNumberingPlugin** — ProseMirror-плагин, зарегистрированный в `NumberedClauseNode`:

```typescript
interface AutoNumberingState {
  byPos: Map<number, string>  // pos → label, читается NodeView пункта
  byId:  Map<string, string>  // id  → label, читается ClauseRefNode
}
```

Алгоритм `buildLabels`: обходит doc, для каждого `numbered_clause` вычисляет счётчик своего уровня (с учётом `listStart`) и форматирует сегменты метки через `formatCounter(n, style)`. В DocumentWorkspace плагин принимает фильтр `isHidden(pos)` — пункты внутри скрытых условных блоков исключаются из счёта.

Поддерживаемые стили: `decimal` (1, 2, 3), `alpha-ru` (а, б, в), `alpha-en` (a, b, c).

**Панели конструктора:**

```
┌─────────────────────────────────────────────────────┐
│  Toolbar: B  I  U  Size▾  Font▾  │ +Параметр │ +Условие │
├────────────────────────────┬────────────────────────┤
│                            │                        │
│   ВИЗУАЛЬНЫЙ РЕДАКТОР      │   ПРАВАЯ ПАНЕЛЬ        │
│   ТЕЛА ШАБЛОНА             │                        │
│                            │   ┌──────────────────┐ │
│   Договор поставки         │   │ ПАРАМЕТРЫ        │ │
│                            │   │ · client_name    │ │
│   г. Москва, [дата]        │   │ · contract_type  │ │
│                            │   │ · contract_date  │ │
│   Заказчик: [ФИО клиента]  │   │ · has_penalty    │ │
│                            │   │ + Добавить       │ │
│   ┌── если has_penalty ──┐ │   └──────────────────┘ │
│   │ Неустойка: [%]%      │ │                        │
│   └──────────────────────┘ │   ┌──────────────────┐ │
│                            │   │ ОПРОСНЫЙ ЛИСТ    │ │
│                            │   │ Стр.1: Стороны   │ │
│                            │   │  · q1 → name     │ │
│                            │   │  · q2 → type     │ │
│                            │   │ Стр.2: Условия   │ │
│                            │   │  · q3 → penalty  │ │
│                            │   │ + Добавить стр.  │ │
│                            │   └──────────────────┘ │
│                            │                        │
│                            │   ┌──────────────────┐ │
│                            │   │ СКРИПТЫ          │ │
│                            │   │ · penalty_block  │ │
│                            │   │ + Добавить       │ │
│                            │   └──────────────────┘ │
└────────────────────────────┴────────────────────────┘
```

### 5.3 Совместное редактирование

```
Пользователь A                 Сервер                    Пользователь B
     │                            │                            │
     ├── WS connect ──────────────┤                            │
     │                            ├── WS connect ──────────────┤
     │                            │                            │
     │◄── sync step 1 ───────────┤──── sync step 1 ──────────►│
     │                            │                            │
     ├── sync step 2 ────────────►│                            │
     │                            ├──── sync step 2 ──────────►│
     │                            │◄─── sync step 2 ───────────┤
     │◄── sync step 2 ────────────┤                            │
     │                            │                            │
     │   --- документ синхронизирован ---                       │
     │                            │                            │
     ├── update (набрал текст) ──►│                            │
     │                            ├── update (relay) ─────────►│
     │                            │                            │
     │                            │◄── update (правка) ────────┤
     │◄── update (relay) ─────────┤                            │
     │                            │                            │
     ├── awareness (курсор) ─────►│                            │
     │                            ├── awareness (relay) ──────►│
```

**Серверная часть:**

```java
@Component
public class YjsWebSocketHandler implements WebSocketHandler {

    private final YjsDocumentStore store;      // MongoDB-backed
    private final RedisTemplate<String, byte[]> redis;

    @Override
    public void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        String docId = extractDocId(session);
        byte[] data = message.getPayload().array();

        int messageType = data[0];
        switch (messageType) {
            case 0 -> handleSyncStep1(session, docId, data);
            case 1 -> handleSyncStep2(session, docId, data);
            case 2 -> handleUpdate(session, docId, data);
            case 3 -> handleAwareness(session, docId, data);
        }
    }

    private void handleUpdate(WebSocketSession session, String docId, byte[] data) {
        // 1. Применить update к серверному состоянию Yjs-документа
        store.applyUpdate(docId, data);

        // 2. Broadcast всем остальным участникам
        broadcastToOthers(session, docId, data);

        // 3. Периодически (debounce 5s) сохранять состояние в MongoDB
        store.schedulePersist(docId);
    }
}
```

### 5.4 Split-panel рабочее пространство (опросный лист + редактор документа)

**Маршруты:**
- `/documents/:id/fill` — основное рабочее пространство пользователя (`QuestionnairePage`).

Страница заполнения документа — это split-panel с одновременным редактированием опросного листа и текста документа. Режима предпросмотра нет: правая панель является полноценным редактором.

**Компоновка:**
```
┌─────────────────────────┬────────────────────────────────────────┐
│  Опросный лист (420px)  │  Документ — редактируемый (flex: 1)    │
│                         │                                        │
│  [Заголовок]            │  [Toolbar: шрифт, размер, жирный...]  │
│  [Steps / страницы]     │  [История] [DOCX] [PDF]               │
│                         │                                        │
│  ┌─────────────────┐    │  ┌──────────────────────────────────┐  │
│  │ Вопрос 1 ←────────────── Иван: [Иванов И.] параметр        │  │
│  │ Вопрос 2        │    │  │  Дата: [01.01.2026]   ← редакт.  │  │
│  │ Вопрос 3        │    │  │  Lorem ipsum свободный текст...  │  │
│  └─────────────────┘    │  └──────────────────────────────────┘  │
│                         │                                        │
│  [Назад] [Далее]        │  ↑ Параметры кликабельны и редактир.  │
└─────────────────────────┴────────────────────────────────────────┘
```

**Бидиректальная связь (ОЛ ↔ документ):**

| Направление | Триггер | Эффект |
|---|---|---|
| ОЛ → документ | Изменение ответа в форме | Значение параметра в тексте обновляется мгновенно через `editor.storage` + no-op transaction |
| Документ → ОЛ (навигация) | Клик на метку параметра в документе | Скролл к вопросу + 2.5s подсветка синей рамкой; автопереключение страницы ОЛ |
| Документ → ОЛ (редактирование) | Ввод текста в поле параметра | Значение синхронизируется в ОЛ через `onAnswerChange` и автосохраняется |

**`QuestionnairePage` (компонент-хост):**
- Левая панель (420px): опросный лист — вопросы, шаги, навигация.
- Правая панель (flex: 1): заголовок с кнопками DOCX/PDF/История + `DocumentWorkspace`.
- Дебаунс-автосохранение ответов (500ms) через `PUT /api/documents/{id}/questionnaire/answers`.
- **Planned**: сохранение тела документа (`PUT /api/documents/{id}/body`) при изменении текста + создание ревизии.

**`DocumentWorkspace` (компонент):**
- Полноценный редактируемый TipTap (`editable: true`) — полный набор расширений: шрифт, размер, отступы, списки, таблицы, Subscript, Superscript, Link, LineSpacing, ParagraphSpacing, PageBreak.
- Начальный контент: `renderPreview(templateBody, answers, parameters, { showAllConditionals: true })` — если `current_body` уже есть (документ ранее генерировался) — **TODO**: загружать `current_body` вместо templateBody (см. TASK-006).
- Ответы → storage → no-op dispatch → re-render node views (без `setContent`, курсор не прыгает).
- `EditorToolbar` (из `template-designer/`) — более полный, чем в DocumentEditor.

**`WorkspaceParameterFieldNode` (TipTap extension):**
- Читает значение из `editor.storage.parameterField.answers[paramCode]` (не из node.attrs).
- Рендерит: `[метка:] [inline input]` — метка кликабельна (→ ОЛ), input редактируем (→ ОЛ).
- Local React state + `useEffect` синхронизация: плавный ввод без lag.
- Прекращает propagation keyboard/mouse событий внутри input (TipTap не перехватывает).
- Storage: `{ answers, parameters, onAnswerChange, onParamFocus, highlightedParam }`.

**`previewRenderer.ts` (утилита):**
- `conditionalBlock` → вычисляется и разворачивается/удаляется.
- `parameterField` → остаётся нодой, получает атрибут `value` для инициализации.
- Используется только для первоначальной установки контента (`setContent`).

**Известные gap-ы (отслеживаются в TASK-006):**
- `/fill` не сохраняет свободные правки текста документа в `current_body` → правки теряются при перезагрузке.
- `/fill` не загружает `current_body`, если документ уже был сгенерирован → каждый раз рендерит шаблон заново.
- `/fill` не имеет Yjs-коллаборации (нет `useCollaboration`, нет `ParticipantsPanel`).
- ~~Кнопка «История ревизий» и создание ревизий при сохранении отсутствуют~~ — **исправлено в BUG-005**: кнопка добавлена, ревизии создаются автоматически при каждом автосохранении ответов (debounce 500ms → `saveRevision()`). Исследование нагрузки и очистки — TASK-007.

### 5.5 Генерация документа

Алгоритм генерации — центральный pipeline:

```
Ответы пользователя
        │
        ▼
┌───────────────────┐
│ 1. Валидация      │  Проверить все required-параметры заполнены.
│    ответов        │  Проверить типы и ограничения.
└───────┬───────────┘
        ▼
┌───────────────────┐
│ 2. Вычисление     │  Для каждого conditional_block и visibility_condition:
│    условий        │  — простые условия → встроенный evaluator
│                   │  — python → GraalPython sandbox
│                   │  Результат: Map<conditionId, Boolean>
└───────┬───────────┘
        ▼
┌───────────────────┐
│ 3. Фильтрация     │  Обойти дерево шаблона (TipTap JSON).
│    условных       │  Удалить conditional_block где condition = false.
│    блоков         │  Оставить содержимое где condition = true.
└───────┬───────────┘
        ▼
┌───────────────────┐
│ 4. Подстановка    │  Заменить все parameter_field ноды
│    параметров     │  на текстовые ноды с реальными значениями.
│                   │  Форматирование: дата → "20 марта 2026 г.",
│                   │  число → "1 000 000", ФИО → склонение (опционально).
└───────┬───────────┘
        ▼
┌───────────────────┐
│ 5. Сохранение     │  generated_body = результат.
│                   │  current_body = копия generated_body.
│                   │  Инициализировать Yjs-документ.
└───────────────────┘
```

### 5.5 Экспорт DOCX

```java
public class DocxExporter {

    public byte[] export(TipTapDocument document, DocumentFormatting formatting) {
        XWPFDocument doc = new XWPFDocument();

        // Настройка страницы
        CTSectPr sectPr = doc.getDocument().getBody().addNewSectPr();
        setPageSize(sectPr, formatting.getPageSize());
        setMargins(sectPr, formatting.getMargins());

        // Обход TipTap JSON и генерация DOCX
        for (TipTapNode node : document.getContent()) {
            switch (node.getType()) {
                case "heading" -> renderHeading(doc, node);
                case "paragraph" -> renderParagraph(doc, node);
                case "table" -> renderTable(doc, node);
                case "bullet_list" -> renderBulletList(doc, node);
                case "ordered_list" -> renderOrderedList(doc, node);
                // ...
            }
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        doc.write(out);
        return out.toByteArray();
    }

    private void renderParagraph(XWPFDocument doc, TipTapNode node) {
        XWPFParagraph para = doc.createParagraph();
        for (TipTapNode child : node.getContent()) {
            XWPFRun run = para.createRun();
            applyMarks(run, child.getMarks());   // bold, italic, fontSize, fontFamily
            run.setText(child.getText());
        }
    }
}
```

### 5.6 Экспорт PDF

```java
public class PdfExporter {

    // Стратегия: генерируем DOCX → конвертируем через LibreOffice headless
    // Это даёт максимальную точность передачи форматирования

    public byte[] export(TipTapDocument document, DocumentFormatting formatting) {
        byte[] docx = docxExporter.export(document, formatting);

        Path tempDocx = Files.createTempFile("doc-", ".docx");
        Files.write(tempDocx, docx);

        // LibreOffice headless conversion
        ProcessBuilder pb = new ProcessBuilder(
            "libreoffice", "--headless", "--convert-to", "pdf",
            "--outdir", tempDocx.getParent().toString(),
            tempDocx.toString()
        );
        pb.start().waitFor(30, TimeUnit.SECONDS);

        Path pdfPath = tempDocx.resolveSibling(
            tempDocx.getFileName().toString().replace(".docx", ".pdf")
        );
        return Files.readAllBytes(pdfPath);
    }
}
```

---

## 6. Структура проекта (для Claude Code)

### 6.1 Структура репозитория

```
docforge/
│
├── README.md                          # Обзор проекта, как запустить
├── CLAUDE.md                          # Инструкции для Claude Code (см. ниже)
├── docker-compose.yml                 # Полный стек: app + postgres + mongo + redis
├── Dockerfile                         # Multi-stage build
│
├── backend/
│   ├── build.gradle.kts
│   ├── settings.gradle.kts
│   ├── src/main/java/com/docforge/
│   │   │
│   │   ├── DocForgeApplication.java
│   │   ├── config/
│   │   │   ├── README.md              # Описание конфигов
│   │   │   ├── SecurityConfig.java
│   │   │   ├── WebSocketConfig.java
│   │   │   ├── MongoConfig.java
│   │   │   └── GraalPythonConfig.java
│   │   │
│   │   ├── common/
│   │   │   ├── README.md
│   │   │   ├── dto/                   # Общие DTO
│   │   │   ├── exception/             # Общие исключения + GlobalExceptionHandler
│   │   │   └── util/                  # DateFormatter, FioFormatter и т.д.
│   │   │
│   │   ├── user/
│   │   │   ├── README.md
│   │   │   ├── UserController.java
│   │   │   ├── UserService.java
│   │   │   ├── UserRepository.java    # JPA (PostgreSQL)
│   │   │   ├── User.java             # Entity
│   │   │   └── dto/
│   │   │
│   │   ├── template/
│   │   │   ├── README.md              # ★ Важно: описание модели Template
│   │   │   ├── TemplateController.java
│   │   │   ├── TemplateService.java
│   │   │   ├── TemplateRepository.java         # Spring Data MongoDB
│   │   │   ├── TemplateRegistryRepository.java  # JPA (PostgreSQL)
│   │   │   ├── model/
│   │   │   │   ├── Template.java               # MongoDB document
│   │   │   │   ├── TemplateRegistry.java        # JPA entity
│   │   │   │   ├── Parameter.java
│   │   │   │   ├── Questionnaire.java
│   │   │   │   ├── Question.java
│   │   │   │   ├── Condition.java
│   │   │   │   └── Script.java
│   │   │   ├── dto/
│   │   │   └── validation/
│   │   │       └── TemplateValidator.java
│   │   │
│   │   ├── document/
│   │   │   ├── README.md
│   │   │   ├── DocumentController.java
│   │   │   ├── DocumentService.java
│   │   │   ├── DocumentRepository.java          # Spring Data MongoDB
│   │   │   ├── DocumentRegistryRepository.java  # JPA (PostgreSQL)
│   │   │   ├── model/
│   │   │   │   ├── Document.java               # MongoDB document
│   │   │   │   └── DocumentRegistry.java        # JPA entity
│   │   │   ├── dto/
│   │   │   ├── generator/
│   │   │   │   ├── DocumentGenerator.java       # Основной pipeline генерации
│   │   │   │   ├── ConditionEvaluator.java      # Вычисление simple-условий
│   │   │   │   ├── PythonConditionEvaluator.java # GraalPython
│   │   │   │   ├── ParameterSubstitutor.java    # Подстановка параметров
│   │   │   │   └── TipTapTreeWalker.java        # Обход JSON-дерева
│   │   │   └── export/
│   │   │       ├── DocxExporter.java
│   │   │       └── PdfExporter.java
│   │   │
│   │   └── collaboration/
│   │       ├── README.md
│   │       ├── YjsWebSocketHandler.java
│   │       ├── CollaborationService.java
│   │       ├── YjsDocumentStore.java
│   │       └── AwarenessManager.java
│   │
│   └── src/main/resources/
│       ├── application.yml
│       ├── application-docker.yml
│       └── db/migration/              # Flyway-миграции PostgreSQL
│           ├── V001__users.sql
│           ├── V002__template_registry.sql
│           ├── V003__document_registry.sql
│           └── V004__audit_log.sql
│
├── frontend/
│   ├── package.json
│   ├── tsconfig.json
│   ├── vite.config.ts
│   ├── src/
│   │   ├── main.tsx
│   │   ├── App.tsx
│   │   ├── api/                       # API-клиент (axios / fetch)
│   │   │   ├── templates.ts
│   │   │   ├── documents.ts
│   │   │   └── client.ts
│   │   │
│   │   ├── components/
│   │   │   ├── common/                # Кнопки, модалки, Layout
│   │   │   ├── template-designer/     # ★ No-code конструктор шаблонов
│   │   │   │   ├── TemplateDesigner.tsx
│   │   │   │   ├── ParameterPanel.tsx
│   │   │   │   ├── QuestionnairePanel.tsx
│   │   │   │   ├── ScriptEditor.tsx
│   │   │   │   ├── ConditionBuilder.tsx       # Визуальный конструктор условий
│   │   │   │   └── extensions/
│   │   │   │       ├── ParameterFieldNode.tsx  # TipTap extension
│   │   │   │       └── ConditionalBlockNode.tsx
│   │   │   │
│   │   │   ├── questionnaire/         # Опросный лист + live preview
│   │   │   │   ├── QuestionnairePage.tsx             # Split-panel: ОЛ слева, редактор справа
│   │   │   │   ├── DocumentWorkspace.tsx             # Редактируемый TipTap + тулбар
│   │   │   │   ├── WorkspaceParameterFieldNode.tsx   # TipTap extension: инлайновый параметр
│   │   │   │   ├── QuestionRenderer.tsx
│   │   │   │   └── field-types/
│   │   │   │       ├── TextField.tsx
│   │   │   │       ├── NumberField.tsx
│   │   │   │       ├── DateField.tsx
│   │   │   │       ├── FioField.tsx
│   │   │   │       ├── BooleanField.tsx
│   │   │   │       └── SelectField.tsx
│   │   │   │
│   │   │   ├── document-editor/       # вспомогательные компоненты редактора
│   │   │   │   ├── FontSize.ts
│   │   │   │   └── ParticipantsPanel.tsx
│   │   │   │
│   │   │   └── document-list/         # Каталог документов
│   │   │       ├── TemplateGallery.tsx
│   │   │       └── MyDocuments.tsx
│   │   │
│   │   ├── hooks/
│   │   │   ├── useCollaboration.ts    # Yjs + WebSocket
│   │   │   ├── useQuestionnaire.ts
│   │   │   └── useTemplate.ts
│   │   │
│   │   ├── utils/
│   │   │   └── previewRenderer.ts     # Client-side рендеринг шаблона (условия + форматирование)
│   │   │
│   │   ├── store/                     # Zustand
│   │   │   ├── authStore.ts
│   │   │   └── documentStore.ts
│   │   │
│   │   └── types/
│   │       ├── template.ts
│   │       ├── document.ts
│   │       └── questionnaire.ts
│   │
│   └── public/
│
└── docs/
    ├── architecture.md                # Эта документация
    ├── api-contract.md                # OpenAPI / описание эндпоинтов
    └── data-model.md                  # Модель данных
```

### 6.2 Файл CLAUDE.md (инструкции для Claude Code)

```markdown
# CLAUDE.md — Инструкции для Claude Code

## Проект
DocForge. Модульный монолит на Spring Boot + React.

## Как запустить
docker-compose up -d          # Postgres, Mongo, Redis
cd backend && ./gradlew bootRun
cd frontend && npm run dev

## Архитектура
- Модульный монолит. Модули: user, template, document, collaboration.
- Модули общаются ТОЛЬКО через интерфейсы сервисного слоя.
- НИКОГДА не импортируй Repository одного модуля в другой.
- В каждом модуле есть README.md с описанием.

## Конвенции

### Backend (Java)
- Java 21, virtual threads включены.
- Контроллеры — тонкие. Только маппинг + вызов сервиса + возврат DTO.
- Вся бизнес-логика — в *Service классах.
- DTO — Java records. Именование: *Request, *Response, *DTO.
- Entity PostgreSQL — JPA @Entity. Именование без суффикса или с суффиксом Registry.
- Entity MongoDB — @Document. Без суффикса.
- Ошибки — кастомные exceptions + GlobalExceptionHandler.
- Flyway для миграций PostgreSQL. Именование: V001__description.sql
- Логирование: SLF4J, не System.out.

### Frontend (TypeScript + React)
- Функциональные компоненты + hooks.
- State management — Zustand.
- API-вызовы — через файлы в api/, возвращают типизированные промисы.
- Типы — в types/, экспортируются как interfaces.
- UI-библиотека — Ant Design.
- Редактор — TipTap. Кастомные extensions в extensions/.

### Именование
- Пакеты: com.docforge.{module}
- REST: /api/{resource} (множественное число, kebab-case)
- MongoDB коллекции: множественное число (templates, documents)
- PostgreSQL таблицы: snake_case (template_registry, audit_log)

### Тестирование
- Unit-тесты: JUnit 5 + Mockito. Имя: *Test.java
- Интеграционные: @SpringBootTest + Testcontainers. Имя: *IT.java
- Frontend: Vitest + React Testing Library.

## Частые задачи

### Добавить новый тип параметра
1. Добавить значение в enum ParameterType (backend)
2. Добавить компонент поля в frontend/field-types/
3. Добавить форматирование в ParameterSubstitutor
4. Добавить валидацию в TemplateValidator

### Добавить новый тип условия
1. Расширить ConditionEvaluator (backend)
2. Расширить ConditionBuilder.tsx (frontend)

### Добавить эндпоинт
1. Создать DTO (request/response records)
2. Метод в *Service
3. Метод в *Controller
4. Метод в api/*.ts (frontend)
```

---

## 7. Docker-конфигурация

### 7.1 docker-compose.yml

```yaml
version: '3.9'

services:
  # ── Приложение ──
  app:
    build: .
    ports:
      - "8080:8080"
    environment:
      SPRING_PROFILES_ACTIVE: docker
      POSTGRES_HOST: postgres
      MONGO_HOST: mongo
      REDIS_HOST: redis
    depends_on:
      postgres:
        condition: service_healthy
      mongo:
        condition: service_started
      redis:
        condition: service_started

  # ── PostgreSQL ──
  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: docforge
      POSTGRES_USER: app
      POSTGRES_PASSWORD: app_secret
    ports:
      - "5432:5432"
    volumes:
      - pgdata:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U app -d docforge"]
      interval: 5s
      retries: 5

  # ── MongoDB ──
  mongo:
    image: mongo:7
    environment:
      MONGO_INITDB_DATABASE: docforge
    ports:
      - "27017:27017"
    volumes:
      - mongodata:/data/db

  # ── Redis ──
  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
    volumes:
      - redisdata:/data

volumes:
  pgdata:
  mongodata:
  redisdata:
```

### 7.2 Dockerfile (multi-stage)

```dockerfile
# ── Stage 1: Build frontend ──
FROM node:20-alpine AS frontend-build
WORKDIR /app/frontend
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# ── Stage 2: Build backend ──
FROM gradle:8-jdk21 AS backend-build
WORKDIR /app/backend
COPY backend/ ./
COPY --from=frontend-build /app/frontend/dist ./src/main/resources/static
RUN gradle bootJar --no-daemon

# ── Stage 3: Runtime ──
FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache libreoffice
WORKDIR /app
COPY --from=backend-build /app/backend/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

---

## 8. Фазы разработки (вайбкодинг-спринты)

Каждая фаза — это законченная итерация, которую можно полностью реализовать в Claude Code за 1–3 сессии. Фазы идут последовательно, каждая опирается на предыдущую.

### Фаза 1 — Фундамент (скелет проекта)
**Цель:** Работающий скелет, поднимается через `docker-compose up`.

Задачи для Claude Code:
- Инициализировать Gradle-проект (Spring Boot 3.3, Java 21).
- Настроить подключения: PostgreSQL (JPA + Flyway), MongoDB (Spring Data), Redis.
- Создать `docker-compose.yml` и `Dockerfile`.
- Написать Flyway-миграции для таблиц users, template_registry, document_registry, audit_log.
- Создать модуль `user`: Entity, Repository, Service, Controller, DTO. Базовая аутентификация (Spring Security + JWT).
- Инициализировать React-проект (Vite + TypeScript). Настроить proxy на backend.
- Создать Layout, страницу логина, заглушку главной страницы.
- Проверить: `docker-compose up` → логин → главная.

**Промпт-стратегия:** Дать Claude Code CLAUDE.md + docker-compose.yml + описание модуля user. Попросить сгенерировать всю фазу целиком.

### Фаза 2 — Модель шаблонов и CRUD
**Цель:** Можно создать, сохранить и загрузить шаблон через API.

Задачи:
- MongoDB-модель Template (все поля, как описано в разделе 3.2).
- PostgreSQL-модель TemplateRegistry.
- TemplateService: CRUD, publish/unpublish.
- TemplateController: REST API.
- Frontend: страница списка шаблонов, форма создания (пока без визуального редактора — простой JSON-ввод для тестирования).

### Фаза 3 — No-code конструктор шаблонов
**Цель:** Визуальный редактор шаблонов с параметрами и условиями.

Задачи:
- Интеграция TipTap (React).
- Кастомный нод `ParameterField`: вставка через toolbar или `/`-команду.
- Кастомный нод `ConditionalBlock`: обёртка с визуальным индикатором.
- Правая панель: управление параметрами (CRUD, типы, валидация).
- Правая панель: опросный лист (CRUD вопросов, привязка к параметрам, порядок, страницы).
- Визуальный конструктор простых условий (ConditionBuilder).
- Редактор Python-скриптов (CodeMirror с подсветкой синтаксиса).
- Сохранение/загрузка шаблона (TipTap JSON → MongoDB).

**Это самая объёмная фаза.** Разбить на подзадачи для Claude Code:
1. TipTap базовый + параметрический нод
2. Условный блок + визуальный конструктор условий
3. Панель параметров + панель опросного листа
4. Скриптовый редактор
5. Интеграция: сохранение/загрузка

### Фаза 4 — Опросный лист и генерация ✅
**Цель:** Пользователь заполняет опросный лист, система генерирует документ.

Задачи:
- Backend: DocumentGenerator pipeline (валидация → условия → фильтрация → подстановка). ✅
- Backend: ConditionEvaluator (простые условия). ✅
- Backend: PythonConditionEvaluator (GraalPython sandbox).
- Backend: ParameterSubstitutor (форматирование дат, чисел, ФИО). ✅
- Frontend: split-panel рабочее пространство (ОЛ + живой предпросмотр). ✅
- Frontend: бидиректальная навигация параметр ↔ вопрос (клик, скролл, подсветка). ✅
- Frontend: компоненты полей по типам (TextField, NumberField, DateField, FioField, BooleanField, SelectField, DirectoryField). ✅
- Frontend: `previewRenderer.ts` — client-side рендеринг (условия, форматирование). ✅
- API: сохранение ответов, пересчёт видимости, генерация документа. ✅
- Справочники (Directories): backend (PostgreSQL + MongoDB), REST API, frontend (список, детальная страница, DirectoryField в ОЛ, тип DIRECTORY в конструкторе). ✅

### Фаза 5 — WYSIWYG-редактор и совместная работа
**Цель:** Редактирование сгенерированного документа, real-time коллаборация.

Задачи:
- TipTap-редактор для документа (не шаблона) с toolbar: шрифт, размер, жирный, курсив, списки, таблицы.
- Yjs интеграция (y-prosemirror + y-websocket).
- Backend: YjsWebSocketHandler.
- Backend: YjsDocumentStore (MongoDB persistence).
- Redis pub/sub для масштабирования WebSocket.
- Frontend: отображение курсоров других пользователей.
- Frontend: панель участников (кто сейчас в документе).

### Фаза 6 — Экспорт
**Цель:** Выгрузка в DOCX и PDF.

Задачи:
- DocxExporter: обход TipTap JSON → Apache POI XWPF.
- PdfExporter: DOCX → LibreOffice headless → PDF.
- Frontend: кнопки экспорта, индикатор загрузки, скачивание файла.
- Обработка ошибок конвертации.

### Фаза 7 — Полировка MVP
**Цель:** Стабильная версия, готовая к демонстрации.

Задачи:
- Валидация шаблонов (целостность параметров, условий, скриптов).
- Обработка ошибок по всему стеку (4xx, 5xx, WebSocket disconnects).
- Загрузка и пустые состояния на фронте.
- Аудит-лог (запись действий).
- Базовый UI каталога шаблонов.
- Docker-образ: проверить полный цикл в контейнере.

---

## 9. Подготовка к post-MVP фичам

Архитектура MVP заложена так, чтобы будущие фичи добавлялись без рефакторинга:

### 9.1 Ролевая модель доступа

Таблица `document_participants` уже содержит поле `role`. В post-MVP добавляется:

```sql
-- Гранулярные права
CREATE TABLE paragraph_permissions (
    document_id  UUID,
    paragraph_id VARCHAR(100),   -- ID нода в TipTap JSON
    user_id      UUID,
    permission   VARCHAR(20),    -- VIEW, EDIT
    PRIMARY KEY (document_id, paragraph_id, user_id)
);

CREATE TABLE question_permissions (
    document_id  UUID,
    question_id  VARCHAR(100),
    user_id      UUID,
    permission   VARCHAR(20),    -- VIEW, ANSWER
    PRIMARY KEY (document_id, question_id, user_id)
);
```

TipTap уже поддерживает `editable` на уровне нодов — это позволит заблокировать редактирование отдельных абзацев.

### 9.2 Статусная модель

Поле `status` в `document_registry` уже есть. В post-MVP добавляется:

```sql
CREATE TABLE status_transitions (
    template_id  UUID,
    from_status  VARCHAR(50),
    to_status    VARCHAR(50),
    required_role VARCHAR(50),
    condition_script TEXT,        -- Python: можно ли перейти
    PRIMARY KEY (template_id, from_status, to_status)
);
```

### 9.3 Пользовательские справочники

```sql
-- Реестр справочников
CREATE TABLE dictionaries (
    id          UUID PRIMARY KEY,
    code        VARCHAR(100) UNIQUE,
    name        VARCHAR(255),
    owner_id    UUID REFERENCES users(id)
);
```

```jsonc
// MongoDB: содержимое справочника
// Коллекция dictionary_entries
{
  "_id": "...",
  "dictionary_id": "uuid",
  "values": {
    "code": "ООО",
    "label": "Общество с ограниченной ответственностью",
    "short_label": "ООО",
    "extra": { /* произвольные поля */ }
  }
}
```

Параметр типа `select` сможет ссылаться на справочник вместо статического списка `options`.

### 9.4 Настройка отображения элементов интерфейса

В шаблон добавляется секция `ui_config`:

```jsonc
{
  "ui_config": {
    "questionnaire_layout": "wizard",   // wizard | single_page | accordion
    "editor_toolbar": ["bold", "italic", "fontSize"],  // какие кнопки показывать
    "show_export_pdf": true,
    "show_export_docx": true,
    "custom_css": "..."                 // кастомные стили
  }
}
```

---

## 10. Нефункциональные требования

### 10.1 Производительность
- Генерация документа: до 3 секунд для шаблона с 50 параметрами и 20 условными блоками.
- Исполнение Python-скрипта: таймаут 2 секунды.
- WebSocket latency (совместное редактирование): до 200 мс.
- Экспорт DOCX: до 5 секунд. Экспорт PDF: до 15 секунд.
- Одновременных пользователей в одном документе: до 10 (MVP).

### 10.2 Безопасность
- Аутентификация: JWT, refresh-token.
- Python-скрипты: GraalPython sandbox — нет доступа к FS, сети, Java-классам.
- Валидация входных данных на уровне DTO (Jakarta Validation).
- CORS — настраиваемый whitelist.
- Пароли — BCrypt.

### 10.3 Мониторинг (MVP-минимум)
- Spring Boot Actuator: health, metrics.
- Структурированное логирование (JSON).
- Аудит-лог в PostgreSQL.

### 10.4 Ограничения MVP
- Нет версионирования шаблонов (только текущая версия).
- Нет импорта шаблонов из DOCX.
- Нет мобильной адаптации (десктоп-first).
- Один инстанс приложения (нет горизонтального масштабирования WebSocket без Redis pub/sub, который уже заложен).
- Нет SSO / LDAP (только локальные пользователи).

---

## 11. Глоссарий

| Термин | Определение |
|--------|------------|
| **Шаблон** | Параметризованный документ с условиями и опросным листом. Создаётся администратором. |
| **Параметр** | Именованное поле в шаблоне, значение которого задаётся пользователем через опросный лист. |
| **Опросный лист** | Набор вопросов, привязанных к параметрам шаблона. Управляет сбором данных от пользователя. |
| **Вариативность** | Механизм включения/исключения блоков текста или вопросов на основе условий. |
| **Условие (простое)** | Булево выражение вида `параметр == значение`, вычисляемое встроенным evaluator. |
| **Условие (Python)** | Произвольный Python-скрипт, результат которого — переменная `result` типа `bool`. |
| **Условный блок** | Фрагмент текста шаблона, который включается в документ только при истинности условия. |
| **Документ** | Конкретный экземпляр, созданный из шаблона. Содержит ответы, сгенерированный текст и правки. |
| **CRDT** | Conflict-free Replicated Data Type — структура данных для бесконфликтной синхронизации в real-time. |
| **Yjs** | Библиотека CRDT для совместного редактирования текста. |
| **TipTap** | WYSIWYG-редактор на основе ProseMirror с поддержкой кастомных нодов и коллаборации. |
| **GraalPython** | Реализация Python на GraalVM, позволяющая безопасно исполнять скрипты из JVM. |
