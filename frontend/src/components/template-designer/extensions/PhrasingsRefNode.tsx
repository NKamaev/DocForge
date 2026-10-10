import { Node, mergeAttributes } from '@tiptap/core'
import { ReactNodeViewRenderer, NodeViewWrapper } from '@tiptap/react'
import type { ReactNodeViewProps } from '@tiptap/react'
import { X } from 'lucide-react'
import { useDesignerContext } from '../DesignerContext'

// ── Command augmentation ──────────────────────────────────────────────────────

declare module '@tiptap/core' {
  interface Commands<ReturnType> {
    phrasingsRef: {
      insertPhrasingsRef: (attrs: {
        phrasingsIds: string[]
        displayNames: string[]
        separator: string
        /** @deprecated legacy compat */
        phrasingsId?: string
        /** @deprecated legacy compat */
        displayName?: string
      }) => ReturnType
    }
  }
}

// ── React view ────────────────────────────────────────────────────────────────

function PhrasingsRefView({ node, deleteNode }: ReactNodeViewProps) {
  const { phrasingsIds, displayNames, phrasingsId, displayName } = node.attrs as {
    phrasingsIds: string[]
    displayNames: string[]
    phrasingsId: string
    displayName: string
  }
  const { phrasings } = useDesignerContext()

  // New format or legacy fallback
  const ids   = phrasingsIds?.length ? phrasingsIds   : (phrasingsId ? [phrasingsId] : [])
  const names = displayNames?.length ? displayNames   : (displayName ? [displayName] : [])

  const isOrphaned = ids.length === 0 || ids.some(id => !phrasings.find(p => p.id === id))
  const label = names.length > 0 ? names.join(', ') : 'Формулировка'

  return (
    <NodeViewWrapper as="span" contentEditable={false} style={{ display: 'inline' }}>
      <span
        style={{
          display: 'inline-flex',
          alignItems: 'center',
          gap: 4,
          padding: '2px 6px',
          borderRadius: 9999,
          fontSize: 12,
          cursor: 'default',
          userSelect: 'none',
          margin: '0 2px',
          background: isOrphaned ? '#fff2f0' : '#f6ffed',
          border: `1px solid ${isOrphaned ? '#ffccc7' : '#b7eb8f'}`,
          color: isOrphaned ? '#ff4d4f' : '#389e0d',
        }}
      >
        <span>📝 {isOrphaned ? `${label} (не найдена)` : label}</span>
        <button
          onClick={(e) => { e.preventDefault(); e.stopPropagation(); deleteNode() }}
          style={{
            display: 'inline-flex',
            alignItems: 'center',
            justifyContent: 'center',
            cursor: 'pointer',
            padding: 0,
            margin: 0,
            border: 'none',
            background: 'transparent',
            color: 'inherit',
            fontSize: 10,
            lineHeight: 1,
          }}
        >
          <X size={10} />
        </button>
      </span>
    </NodeViewWrapper>
  )
}

// ── TipTap Node definition ────────────────────────────────────────────────────

export const PhrasingsRefNode = Node.create({
  name: 'phrasingsRef',
  group: 'inline',
  inline: true,
  atom: true,

  addAttributes() {
    return {
      // ── New multi-phrasing attrs ──────────────────────────────────────────
      phrasingsIds: {
        default: [],
        parseHTML: (el) => {
          try { return JSON.parse(el.getAttribute('data-phrasings-ids') ?? '[]') } catch { return [] }
        },
        renderHTML: (attrs) => ({ 'data-phrasings-ids': JSON.stringify(attrs.phrasingsIds ?? []) }),
      },
      displayNames: {
        default: [],
        parseHTML: (el) => {
          try { return JSON.parse(el.getAttribute('data-display-names') ?? '[]') } catch { return [] }
        },
        renderHTML: (attrs) => ({ 'data-display-names': JSON.stringify(attrs.displayNames ?? []) }),
      },
      separator: {
        default: ', ',
        parseHTML: (el) => el.getAttribute('data-separator') ?? ', ',
        renderHTML: (attrs) => ({ 'data-separator': attrs.separator }),
      },
      // ── Legacy attrs (remain for backwards compat) ───────────────────────
      phrasingsId: {
        default: '',
        parseHTML: (el) => el.getAttribute('data-phrasings-id') ?? '',
        renderHTML: (attrs) => ({ 'data-phrasings-id': attrs.phrasingsId }),
      },
      displayName: {
        default: 'Формулировка',
        parseHTML: (el) => el.getAttribute('data-display-name') ?? 'Формулировка',
        renderHTML: (attrs) => ({ 'data-display-name': attrs.displayName }),
      },
    }
  },

  parseHTML() {
    return [{ tag: 'span[data-type="phrasings-ref"]' }]
  },

  renderHTML({ HTMLAttributes }) {
    return ['span', mergeAttributes(HTMLAttributes, { 'data-type': 'phrasings-ref' })]
  },

  addNodeView() {
    return ReactNodeViewRenderer(PhrasingsRefView)
  },

  addCommands() {
    return {
      insertPhrasingsRef:
        (attrs) =>
        ({ commands }) =>
          commands.insertContent({ type: this.name, attrs }),
    }
  },
})
