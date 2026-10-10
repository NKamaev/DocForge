---
name: param-format-add
description: Adding a new parameter format type (backend + frontend)
---

## Steps

### 1. Backend: Add enum value
- Add to `ParameterType` enum in `com.docforge.template.model`
```java
public enum ParameterType {
    TEXT, NUMBER, DATE, FIO, BOOLEAN, SELECT, DIRECTORY
    // Add: CURRENCY, PHONE, EMAIL, etc.
}
```

### 2. Backend: Add formatter
- Extend `ParameterSubstitutor.java`
```java
case CURRENCY -> formatCurrency(value, locale);
case PHONE -> formatPhone(value);
```
- Add formatting method with locale support

### 3. Backend: Add validator
- Extend `TemplateValidator.java`
- Add type-specific validation rules (e.g., phone regex, currency range)

### 4. Frontend: Add field component
- Create `frontend/src/components/questionnaire/field-types/<Type>Field.tsx`
- Follow existing component pattern (TextField, NumberField, etc.)
- Register in the field type resolver map

### 5. Frontend: Add type definition
- Add to `frontend/src/types/template.ts` `ParameterType` union

### 6. Test
```typescript
// Frontend: render test
it('renders currency field with formatting', () => { ... })

// Backend: formatting test
@Test
void formatCurrency_givenValue_returnsFormatted() { ... }
```

### 7. Verify full cycle
1. Create template with new parameter type
2. Fill questionnaire → value formatted correctly in preview
3. Generate document → correct value in output
4. Export DOCX/PDF → formatting preserved
