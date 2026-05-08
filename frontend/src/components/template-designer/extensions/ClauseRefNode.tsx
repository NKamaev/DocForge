import { useEffect, useState } from 'react'
import { Node, mergeAttributes } from '@tiptap/core'
import { ReactNodeViewRenderer, NodeViewWrapper } from '@tiptap/react'
import type { ReactNodeViewProps } from '@tiptap/react'
import { Tooltip } from 'antd'
import { autoNumberingPluginKey } from './NumberedClauseNode'

// ── Command augmentation ──────────────────────────────────────────────────────

declare module '@tiptap/core' {
  interface Commands<ReturnType> {
    clauseRef: {
      /** Insert an inline reference to a numbered clause by its stable id */
      insertClauseRef: (refId: string) => ReturnType
    }
  }
}

// ── React view ────────────────────────────────────────────────────────────────

function ClauseRefView({ node, editor }: ReactNodeViewProps) {
  const refId = node.attrs.refId as string

  // Re-render when numbering changes
  const [, setTick] = useState(0)
  useEffect(() => {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const handler = ({ transaction }: { transaction: any }) => {
      if (transaction.docChanged || transaction.getMeta('answers-updated')) {
        setTick(t => t + 1)
      }
    }
    editor.on('transaction', handler)
    return () => { editor.off('transaction', handler) }
  }, [editor])

  const numberingState = autoNumberingPluginKey.getState(editor.view.state)
  const label = numberingState?.byId.get(refId)

  const handleClick = () => {
    if (!label) return
    // Find the referenced node's position and scroll to it
    editor.view.state.doc.descendants((n, pos) => {
      if (n.type.name === 'numberedClause' && n.attrs.id === refId) {
        editor.view.dispatch(
          editor.view.state.tr.setSelection(
            // eslint-disable-next-line @typescript-eslint/no-explicit-any
            (editor.view.state.selection.constructor as any).near(
              editor.view.state.doc.resolve(pos + 1)
            )
          )
        )
        editor.view.domAtPos(pos).node instanceof Element &&
          (editor.view.domAtPos(pos).node as Element).scrollIntoView({ behavior: 'smooth', block: 'center' })
        return false
      }
    })
  }

  if (!label) {
    return (
      <NodeViewWrapper as="span" contentEditable={false}>
        <Tooltip title="Пункт удалён или не найден">
          <span
            style={{
              color: '#ff4d4f',
              background: '#fff2f0',
              border: '1px solid #ffccc7',
              borderRadius: 2,
              padding: '1px 4px',
              fontSize: '0.9em',
              cursor: 'default',
              userSelect: 'none',
            }}
          >
            ???
          </span>
        </Tooltip>
      </NodeViewWrapper>
    )
  }

  return (
    <NodeViewWrapper as="span" contentEditable={false}>
      <Tooltip title="Перейти к пункту">
        <span
          onClick={handleClick}
          style={{
            background: '#E6F4FF',
            border: '1px solid #91CAFF',
            borderRadius: 2,
            padding: '1px 4px',
            fontSize: '0.9em',
            cursor: 'pointer',
            userSelect: 'none',
            color: '#0958D9',
          }}
        >
          п.&nbsp;{label}
        </span>
      </Tooltip>
    </NodeViewWrapper>
  )
}

// ── TipTap Node definition ────────────────────────────────────────────────────

export const ClauseRefNode = Node.create({
  name: 'clauseRef',
  group: 'inline',
  inline: true,
  atom: true,

  addAttributes() {
    return {
      refId: {
        default: '',
        parseHTML: (el) => el.getAttribute('data-ref-id') ?? '',
        renderHTML: (attrs) => ({ 'data-ref-id': attrs.refId }),
      },
    }
  },

  parseHTML() {
    return [{ tag: 'span[data-type="clause-ref"]' }]
  },

  renderHTML({ HTMLAttributes }) {
    return ['span', mergeAttributes(HTMLAttributes, { 'data-type': 'clause-ref' })]
  },

  addNodeView() {
    return ReactNodeViewRenderer(ClauseRefView)
  },

  addCommands() {
    return {
      insertClauseRef:
        (refId: string) =>
        ({ commands }) =>
          commands.insertContent({
            type: this.name,
            attrs: { refId },
          }),
    }
  },
})
