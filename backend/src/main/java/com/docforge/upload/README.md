# Модуль upload

Загрузка и хранение изображений, вставляемых в шаблоны документов.

## API

| Метод | Путь | Описание |
|-------|------|---------|
| `POST` | `/api/uploads/images` | Загрузить изображение (multipart/form-data, поле `file`) |
| `GET` | `/api/uploads/images/{filename}` | Получить файл по имени |

### POST /api/uploads/images

Принимает `multipart/form-data` с полем `file`.

**Ответ:**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "url": "/api/uploads/images/550e8400-e29b-41d4-a716-446655440000.png"
}
```

**Ограничения:**
- Допустимые типы: `image/jpeg`, `image/png`, `image/gif`, `image/webp`
- Максимальный размер: 10 МБ (настраивается через `spring.servlet.multipart.max-file-size`)
- Имя файла: `{UUID}.{ext}` — расширение берётся из оригинального имени файла

## Конфигурация

```yaml
app:
  upload:
    dir: ./uploads/images   # директория хранения (создаётся автоматически при старте)
```

## Ключевые классы

| Класс | Описание |
|-------|---------|
| `ImageStorageService` | Интерфейс: `store(MultipartFile)` → `ImageUploadResponse`, `load(filename)` → `Resource` |
| `LocalImageStorageService` | Реализация: хранение на локальной ФС. Создаёт директорию при старте (`@PostConstruct`). Защита от path traversal. |
| `ImageUploadController` | REST-контроллер: POST загрузка + GET отдача файла |
| `dto/ImageUploadResponse` | Record: `(String id, String url)` |

## Безопасность

- Требуется JWT-аутентификация (все `/api/**` защищены).
- Валидация MIME-типа через `file.getContentType()` — при несоответствии бросается `ValidationException`.
- Защита от path traversal: имя файла генерируется как UUID, исходное имя используется только для извлечения расширения.
