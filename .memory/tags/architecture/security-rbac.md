# Безопасность — JWT, RBAC, грейды

> Тег: `architecture:security-rbac` · Обновлено: `2026-10-10`

## [2026-10-10 10:00] Аутентификация и доступ

- **JWT:** `SecurityConfig` — csrf off, STATELESS; permitAll `/api/users/login`, `/api/users/register`, `/actuator/health` (:35 - actuator не на classpath, мёртвый конфиг); CORS настроен. `JwtAuthFilter` — OncePerRequestFilter, валидирует токен. `JwtTokenProvider` — HS256, `app.jwt.*`, expiration 24h (`expiration-ms=86400000`). Роли: ROLE_ADMIN+ROLE_USER для admin, иначе ROLE_USER (`UserDetailsServiceImpl`).
- **RBAC документов:** `DocumentPermission` (VIEW, READ, WRITE, MANAGE, SHARE, DELETE, ADMIN); `DocumentRole` (VIEWER, EDITOR, MANAGER, OWNER) с `implies()` (`DocumentRole.java:6-11`). `getEffectiveRole`: owner > прямые members > folder-inherit (`DocumentPermissionServiceImpl:30-39`). `MemberSource` = DIRECT / FOLDER_INHERIT (V012). `FolderPermission`/`FolderRole` — той же формы.
- **Где проверяется:** `DocumentController` — READ на getById/questionnaire (:87/:131), MANAGE на move (:100), WRITE на body (:162), DELETE (:116); members — SHARE/VIEW.
- **Источник:** `backend/src/main/java/com/docforge/security/`, explore backend report.

## Известные грейды (на 2026-10-10)

`saveAnswers` (:137-144), `generate` (:149-152), revisions (:225-267), participants (:271-296), document export (:170-221) — **без permission-check**. `TemplateController` — нет owner-проверок вовсе. Кандидаты на hardening-тикет.

## Frontend-зеркало RBAC

- `types/member.ts` — `roleImplies(role, 'read'|'write'|'manage'|'share'|'delete')` по весам (:21-25). `useMyRole`/`useMyDocumentRole` — хук текущей роли. `/fill` для VIEWER — read-only (`QuestionnairePage.tsx:33`).

<!-- Новые записи добавляй СВЕРХУ. -->