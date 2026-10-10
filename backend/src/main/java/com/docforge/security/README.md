# Модуль security

Аутентификация и авторизация на основе JWT.

## Ответственность

- Генерация и валидация JWT-токенов
- Фильтрация HTTP-запросов (проверка JWT)
- Конфигурация Spring Security (CORS, CSRF, endpoints)

## Ключевые классы

| Класс | Описание |
|-------|---------|
| `JwtTokenProvider` | Генерация и валидация JWT (секрет + срок 24ч) |
| `JwtAuthFilter` | `OncePerRequestFilter` — извлекает JWT из `Authorization: Bearer` |
| `SecurityConfig` | `SecurityFilterChain`: защита `/api/**`, CORS, stateless-сессии |
| `UserDetailsServiceImpl` | Загрузка пользователя из БД для Spring Security |

## Использование в других модулях

- `collaboration.JwtHandshakeInterceptor` — валидация JWT при WS-upgrade
- Все Контроллеры защищены через `SecurityConfig`
