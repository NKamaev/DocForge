import { Mark, Node, mergeAttributes } from '@tiptap/core'
import type { Extensions } from '@tiptap/core'

// ── Inline diffs ──────────────────────────────────────────────────────────────
//
// `diffInsert` / `diffDelete` are marks, so a single word (or run of words) can
// be highlighted without splitting the surrounding block. They render a
// `data-diff` attribute plus a self-contained colour scheme so the diff is
// readable even when only these extensions are loaded (read-only diff view).

const INSERT_STYLE = 'background-color: #dcfce7; text-decoration: underline;'
const DELETE_STYLE = 'background-color: #fee2e2; text-decoration: line-through;'

export const DiffInsertMark = Mark.create({
  name: 'diffInsert',

  parseHTML() {
    return [
      { tag: 'span[data-diff="insert"]' },
      { tag: 'ins' },
    ]
  },

  renderHTML({ HTMLAttributes }) {
    return [
      'span',
      mergeAttributes(HTMLAttributes, {
        'data-diff': 'insert',
        class: 'diff-insert',
        style: INSERT_STYLE,
      }),
      0,
    ]
  },
})

export const DiffDeleteMark = Mark.create({
  name: 'diffDelete',

  parseHTML() {
    return [
      { tag: 'span[data-diff="delete"]' },
      { tag: 'del' },
    ]
  },

  renderHTML({ HTMLAttributes }) {
    return [
      'span',
      mergeAttributes(HTMLAttributes, {
        'data-diff': 'delete',
        class: 'diff-delete',
        style: DELETE_STYLE,
      }),
      0,
    ]
  },
})

// ── Block diff ────────────────────────────────────────────────────────────────

export type DiffBlockKind = 'insert' | 'delete'

export const DiffBlockNode = Node.create({
  name: 'diffBlock',
  group: 'block',
  content: 'block+',
  defining: true,

  addAttributes() {
    return {
      kind: {
        default: 'insert' as DiffBlockKind,
        parseHTML: (el) =>
          el.getAttribute('data-diff') === 'delete' ? 'delete' : 'insert',
        renderHTML: (attrs) => ({ 'data-diff': attrs.kind }),
      },
    }
  },

  parseHTML() {
    return [
      { tag: 'div[data-diff="insert"]' },
      { tag: 'div[data-diff="delete"]' },
    ]
  },

  renderHTML({ node, HTMLAttributes }) {
    const kind = (node.attrs.kind as DiffBlockKind) ?? 'insert'
    const style =
      kind === 'delete'
        ? 'border-left: 3px solid #ef4444; background-color: #fef2f2; padding: 4px 8px;'
        : 'border-left: 3px solid #22c55e; background-color: #f0fdf4; padding: 4px 8px;'
    return [
      'div',
      mergeAttributes(HTMLAttributes, {
        class: `diff-block diff-block-${kind}`,
        style,
      }),
      0,
    ]
  },
})

/**
 * Convenience bundle of every diff extension. Consumers that want the diff
 * schema without the rest of the workspace can spread this into their own
 * extension list.
 */
export const DiffExtensions: Extensions = [
  DiffInsertMark,
  DiffDeleteMark,
  DiffBlockNode,
]
