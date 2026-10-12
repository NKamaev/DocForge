---
name: template-create
description: Standardized process for creating a document template. Use for real AND test/QA templates — every template MUST be complete (parameters + questionnaire questions + body text with placed parameter fields), otherwise the template is not suitable for verification
---

## Mandatory for EVERY template (incl. test/QA)

A template created for tests/QA **must be complete** — otherwise documents created from it are unusable for verification:

- ✅ **Parameters** — 2+ of different types (`text`, `number`, `date`, `fio`, `boolean`, `select`, `directory`)
- ✅ **Questionnaire** — pages → questions, each bound to a parameter via `parameter_code`
- ✅ **Body text** — document text with `parameter_field` nodes placed at proper positions (+ `conditional_block` nodes where relevant)
- ✅ **Publish AFTER the questionnaire/parameters are filled** — the publish snapshot only captures what is saved at that moment

A template with only a parameter but no questionnaire/body produces documents with an empty ОЛ — never use such a template for verification.

## Steps

### 1. Define parameters
- Create parameters with unique `code` (snake_case)
- Add 2+ parameters of different types: `text`, `number`, `date`, `fio`, `boolean`, `select`, `directory`
- Set type: `text`, `number`, `date`, `fio`, `boolean`, `select`, `directory`
- Configure validation: min, max, pattern, required
- Set default values (use `$TODAY`, `$TOMORROW` for dates)

### 2. Build questionnaire
- Organize questions into pages
- Bind each question to a parameter via `parameter_code`
- Every parameter used in the body must have a bound question in the questionnaire
- Set order within page
- Add visibility conditions where needed

### 3. Write template body
- Use TipTap editor to compose document text
- Insert `parameter_field` nodes for dynamic values — place ALL questionnaire parameters into the body text
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

### 6. Publish (after the questionnaire is filled)
- `POST /api/templates/{id}/publish`
- The publish snapshot fixes the current state; params/questions added AFTER publish do NOT reach new documents
- Template becomes available in user catalog
- If the template is already published, editing is blocked: «Снять с публикации» → edit ОЛ/params/body → publish again
