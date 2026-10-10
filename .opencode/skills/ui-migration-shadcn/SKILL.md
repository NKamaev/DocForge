---
name: ui-migration-shadcn
description: Step-by-step migration from Ant Design to shadcn/ui + Tailwind
---

## Steps

### 1. Prepare
```bash
cd frontend
npm uninstall antd @ant-design/icons
npx shadcn@latest init   # Set up Tailwind + shadcn/ui
```

### 2. Add base components
```bash
npx shadcn@latest add button input card dialog table select checkbox
```
Components land in `frontend/src/components/ui/`.

### 3. Map Ant Design → shadcn/ui
| Ant Design | shadcn/ui |
|------------|-----------|
| `Button` | `Button` (variant: primary/secondary/outline/ghost) |
| `Input` | `Input` |
| `Card` | `Card` (CardHeader, CardContent, CardFooter) |
| `Modal` | `Dialog` (DialogTrigger, DialogContent) |
| `Table` | `Table` |
| `Select` | `Select` |
| `Checkbox` | `Checkbox` |
| `Radio` | `RadioGroup` |
| `Form` | Use native `<form>` + react-hook-form |

### 4. Replace imports
```typescript
// Before
import { Button } from 'antd'
// After
import { Button } from '@/components/ui/button'
```

### 5. Adapt styles
- Colors and typography from Claude Design (`docs/design/`)
- Tailwind classes replace Ant Design's CSS-in-JS
- Use `cn()` utility for conditional classes

### 6. Verify
- Run Playwright E2E tests (written RED before migration)
- Check visual consistency across all pages
- Ensure TipTap editor still works in both designer and document views

### 7. Priority order
1. Button, Input, Card — simple replacements
2. Table, Dialog, Select, Form — more complex
3. Domain-specific: Toolbar, ParticipantPanel, QuestionnairePanel
