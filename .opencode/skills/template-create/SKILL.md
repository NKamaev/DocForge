---
name: template-create
description: Standardized process for creating a document template
---

## Steps

### 1. Define parameters
- Create parameters with unique `code` (snake_case)
- Set type: `text`, `number`, `date`, `fio`, `boolean`, `select`, `directory`
- Configure validation: min, max, pattern, required
- Set default values (use `$TODAY`, `$TOMORROW` for dates)

### 2. Build questionnaire
- Organize questions into pages
- Bind each question to a parameter via `parameter_code`
- Set order within page
- Add visibility conditions where needed

### 3. Write template body
- Use TipTap editor to compose document text
- Insert `parameter_field` nodes for dynamic values
- Wrap conditional fragments in `conditional_block` nodes
- Configure formatting: page size, margins, fonts

### 4. Add conditions
- Simple: `param == value`, `param > 10`, `param in [a, b]`
- Python: full scripts with `result = bool` output
- Test conditions with mock data via `/api/templates/{id}/scripts/test`

### 5. Validate
- Run `POST /api/templates/{id}/validate`
- Check all parameters referenced in body exist in parameters list
- Check all questions reference valid parameters
- Verify condition expressions are syntactically correct

### 6. Publish
- `POST /api/templates/{id}/publish`
- Template becomes available in user catalog
