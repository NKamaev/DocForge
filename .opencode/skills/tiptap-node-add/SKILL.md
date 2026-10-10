---
name: tiptap-node-add
description: Adding a custom TipTap/ProseMirror node or mark
---

## Steps

### 1. Determine node type
| Type | `group` | `inline` | `content` | Use case |
|------|---------|----------|-----------|----------|
| Inline atom | `inline` | `true` | — | `parameter_field`, `clause_ref` |
| Inline wrapper | `inline` | `true` | `inline*` | `conditional_inline` |
| Block wrapper | `block` | — | `block+` | `conditional_block` |
| Block leaf | `block` | — | — | `page_break`, `horizontal_rule` |
| Mark | — | — | — | bold, italic, custom formatting |

### 2. Create the extension file
- One file, one extension: `frontend/src/components/extensions/<NodeName>.ts`
- Use `Node.create()`, `Mark.create()`, or `Extension.create()`

### 3. Implement required methods
```typescript
// Inline atom example
const CustomNode = Node.create({
  name: 'custom_node',
  group: 'inline',
  inline: true,
  atom: true,
  attrs: { code: { default: '' } },
  parseHTML: [{ tag: 'span[data-custom]' }],
  renderHTML: ({ node }) => ['span', { 'data-custom': node.attrs.code }, node.attrs.code],
  addCommands: () => ({ ... }),
})
```

### 4. Complex rendering
- Use `ReactNodeViewRenderer` for interactive nodes
- Place React component in `extensions/` alongside the node definition

### 5. Register
- Add to the extension list where Editor is created
- Ensure `toJSON()` / `fromJSON()` are symmetric

### 6. Test
```typescript
import { renderEditor } from 'test-utils'
it('inserts and renders custom node', () => { ... })
```
