# Frontend — API-слой

> Тег: `frontend:api-layer` · Обновлено: `2026-10-10`

## [2026-10-10 10:00] Паттерн API-клиента

- **Факт:** тонкие модули, экспортирующие объект методов: `api.<verb>(url, ...)`, `.then(r => r.data)`; blob для экспортов; типизированные record из `src/types`.
- **`axiosInstance.ts`:** baseURL `/api`; request-interceptor добавляет `Bearer` из `localStorage.token`; response-interceptor: 401 → `location.href='/login'`, кроме `/users/login`, `/users/register`.
- **Модули (`src/api/`):** `authApi` (login/register/me), `templatesApi` (77 строк: CRUD, publish/unpublish, export docx/pdf blob POST, `getSnapshot`, versions, `saveVersion{MAJOR|MINOR}`, `rollback`, `getDiff`), `documentsApi` (67 строк: CRUD, questionnaire, answers, generate, `updateBody`, export blob GET, revisions list/get/save/restore, `getRevisionDiff`, `moveToFolder`), `directoriesApi`, `foldersApi`, `membersApi`, `uploadsApi`, `usersApi`.
- **Уведомления:** `showNotification(type, msg)` — локальные хелперы страниц; `showToast(msg, 'success'|'error')` из `ui/toast` через CustomEvent `'app-toast'` (программный путь — покрыт e2e `toast-debug.spec.ts`).
- **Источник:** `frontend/src/api/`, explore frontend report.

## Store (Zustand)

- Единственный стор — `authStore.ts` (`{token, user, isAuthenticated}`, `setAuth`, `setUser`, `logout`; token в `localStorage 'token'`). Zustand-`documentStore` НЕТ — состояние документов компонент-локальное.

<!-- Новые записи добавляй СВЕРХУ. -->