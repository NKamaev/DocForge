# Модуль user

Аутентификация и управление пользователями.

## Ответственность
- Регистрация и вход пользователей (JWT)
- Хранение профиля пользователя в PostgreSQL

## Ключевые классы

| Класс | Назначение |
|-------|-----------|
| `User` | JPA Entity, таблица `users` |
| `UserRepository` | Доступ к данным (JPA) |
| `UserService` | Интерфейс бизнес-логики — используй его для зависимостей из других модулей |
| `UserServiceImpl` | Реализация: регистрация, вход, получение профиля |
| `UserController` | REST: `POST /api/users/register`, `POST /api/users/login`, `GET /api/users/me` |
| `dto/*` | DTO records: `LoginRequest`, `LoginResponse`, `RegisterRequest`, `UserDTO` |

## Зависимости
- Входящие: `SecurityConfig` (JwtAuthFilter), другие модули через `UserService`
- Исходящие: `security.JwtTokenProvider`

## Правила
- Другие модули НЕ импортируют `UserRepository` — только `UserService`.
- Пароли хранятся как BCrypt-хэш в поле `password_hash`.
- JWT выдаётся при регистрации и входе, срок жизни — 24ч.
