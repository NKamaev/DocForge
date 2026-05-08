# REST API contracts

See `docs/architecture.md` section 4 for full API contract definitions.

## Base URL

`/api/{resource}` — plural nouns, kebab-case.

## HTTP methods

| Method | Usage |
|--------|-------|
| GET | Read / list |
| POST | Create / action (e.g. `/api/documents/{id}/generate`) |
| PUT | Full replace |
| PATCH | Partial update |
| DELETE | Remove |

## Status codes

- 200 OK, 201 Created, 204 No Content
- 400 Bad Request, 404 Not Found, 409 Conflict, 422 Unprocessable Entity

## Pagination

`?page=0&size=20&sort=createdAt,desc`

## Error response format

```json
{ "error": "NOT_FOUND", "message": "Template not found", "details": {} }
```

## Key endpoints

- `POST /api/auth/login` — authenticate
- `POST /api/auth/register` — register
- `GET/PUT /api/users/{id}` — user profile
- `GET/POST /api/templates` — list/create templates
- `GET/PUT/DELETE /api/templates/{id}` — template CRUD
- `POST /api/templates/{id}/publish` — publish template
- `GET/POST /api/documents` — list/create documents
- `GET/PUT/DELETE /api/documents/{id}` — document CRUD
- `POST /api/documents/{id}/generate` — generate from template
- `POST /api/documents/{id}/export/docx` — export DOCX
- `POST /api/documents/{id}/export/pdf` — export PDF
- `GET/POST /api/directories` — directory CRUD
- `POST /api/feedback` — error report
