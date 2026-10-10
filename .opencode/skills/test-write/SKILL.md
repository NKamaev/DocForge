---
name: test-write
description: Writing tests across all layers — JUnit, Vitest, Playwright
---

## Steps

### 1. Determine test type
| Test type | Framework | Location | When |
|-----------|-----------|----------|------|
| Backend unit | JUnit 5 + Mockito | `*Test.java` next to source | Service logic, validators |
| Backend integration | Testcontainers | `*IT.java` | Repository, full API |
| Frontend unit | Vitest + RTL | `*.test.tsx` next to component | Component rendering |
| E2E | Playwright | `frontend/tests/*.spec.ts` | User flows, critical paths |

### 2. Backend unit test structure
```java
@Test
void methodName_givenCondition_expectedResult() {
    // given
    var input = ...;
    // when
    var result = service.method(input);
    // then
    assertThat(result).isEqualTo(expected);
}
```

### 3. Frontend test structure
```typescript
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'

it('renders and responds to click', async () => {
  render(<Component />)
  await userEvent.click(screen.getByRole('button'))
  expect(screen.getByText('clicked')).toBeVisible()
})
```

### 4. Playwright E2E (TDD: RED → GREEN)
```typescript
import { test, expect } from '@playwright/test'

test('user can create a document', async ({ page }) => {
  await page.goto('/templates')
  await page.getByRole('button', { name: 'Create' }).click()
  await expect(page.getByText('New Document')).toBeVisible()
})
```

### 5. Verify
```bash
cd backend && ./gradlew test
cd frontend && npm test -- --run
npx playwright test
```
