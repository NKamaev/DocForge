# REST API contracts

Base URL: `/api/{resource}` — plural nouns, kebab-case.

## HTTP methods

| Method | Usage |
|--------|-------|
| GET | Read / list |
| POST | Create / action |
| PUT | Full replace |
| PATCH | Partial update |
| DELETE | Remove |

## Status codes

- 200 OK, 201 Created, 204 No Content
- 400 Bad Request, 401 Unauthorized, 403 Forbidden, 404 Not Found, 409 Conflict, 422 Unprocessable Entity

## Pagination

`?page=0&size=20&sort=createdAt,desc`

## Error response

```json
{ "error": "NOT_FOUND", "message": "Template not found", "details": {} }
```

## Endpoints

### Auth

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/auth/login` | Login |
| POST | `/api/auth/register` | Register |

### Users

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/users/me` | Current user profile |
| PUT | `/api/users/{id}` | Update user |

### Templates

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/templates` | List (paginated, filtered) |
| POST | `/api/templates` | Create |
| GET | `/api/templates/{id}` | Full template (body + questionnaire + scripts) |
| PUT | `/api/templates/{id}` | Update |
| DELETE | `/api/templates/{id}` | Delete |
| POST | `/api/templates/{id}/publish` | Publish |
| POST | `/api/templates/{id}/unpublish` | Unpublish |
| POST | `/api/templates/{id}/validate` | Validate integrity |
| POST | `/api/templates/{id}/scripts/test` | Test script with mock data |
| POST | `/api/templates/{id}/export/docx` | Export DOCX |
| POST | `/api/templates/{id}/export/pdf` | Export PDF |

### Documents

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/documents` | My documents |
| POST | `/api/documents` | Create from template |
| GET | `/api/documents/{id}` | Get document |
| DELETE | `/api/documents/{id}` | Delete |
| PUT | `/api/documents/{id}/body` | Save document body |
| GET | `/api/documents/{id}/questionnaire` | Questionnaire with visibility |
| PUT | `/api/documents/{id}/questionnaire/answers` | Save answers |
| POST | `/api/documents/{id}/generate` | Generate body from answers |
| GET | `/api/documents/{id}/export/docx` | Export DOCX |
| GET | `/api/documents/{id}/export/pdf` | Export PDF |
| GET | `/api/documents/{id}/participants` | List participants |
| POST | `/api/documents/{id}/participants` | Add participant |
| DELETE | `/api/documents/{id}/participants/{uid}` | Remove participant |

### Documents — Folders

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/folders` | List folders (tree) |
| POST | `/api/folders` | Create folder |
| PUT | `/api/folders/{id}` | Update folder |
| DELETE | `/api/folders/{id}` | Delete folder |

### Documents — Members (RBAC)

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/documents/{id}/members` | List document members |
| POST | `/api/documents/{id}/members` | Add member |
| DELETE | `/api/documents/{id}/members/{userId}` | Remove member |

### Documents — Revisions

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/documents/{id}/revisions` | List revisions |
| GET | `/api/documents/{id}/revisions/{revId}` | Get revision body |
| GET | `/api/documents/{id}/revisions/diff?from={revId}&to={revId}` | Diff between revisions |

### Directories

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/directories` | List directories |
| POST | `/api/directories` | Create directory |
| GET | `/api/directories/{id}` | Get directory + schema fields + entries |
| PUT | `/api/directories/{id}` | Update name/schema |
| DELETE | `/api/directories/{id}` | Delete |
| POST | `/api/directories/{id}/entries` | Add entry |
| PUT | `/api/directories/{id}/entries/{eid}` | Update entry |
| DELETE | `/api/directories/{id}/entries/{eid}` | Delete entry |

### Uploads

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/uploads/images` | Upload image (multipart, max 10MB) |
| GET | `/api/uploads/images/{filename}` | Get image file |

### Feedback

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/feedback` | Submit error report |

### WebSocket

| Path | Description |
|------|-------------|
| `/ws/collab/{documentId}` | Yjs real-time collaboration (binary protocol) |
