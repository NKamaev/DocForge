import { useState, useEffect, useRef } from 'react'
import { Node, mergeAttributes } from '@tiptap/core'
import { ReactNodeViewRenderer, NodeViewWrapper, NodeViewContent } from '@tiptap/react'
import type { ReactNodeViewProps } from '@tiptap/react'
import { evaluateSimple } from '../../utils/previewRenderer'
import type { WorkspaceParamStorage } from './WorkspaceParameterFieldNode'

interface BlockCondition {
  type: 'simple' | 'python'
  expression: string
}

interface ConditionalInlineAttrs {
  condition: BlockCondition
  label: string
}

const DEFAULT_CONDITION: BlockCondition = { type: 'simple', expression: '' }

function WorkspaceConditionalInlineView({ node, editor }: ReactNodeViewProps) {
  const attrs = node.attrs as ConditionalInlineAttrs

  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const storage = (editor.storage as any).parameterField as WorkspaceParamStorage | undefined

  const tickRef = useRef(0)
  const [_tick, setTick] = useState(0)
  useEffect(() => {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const handler = ({ transaction }: { transaction: any }) => {
      if (transaction.getMeta('answers-updated')) {
        tickRef.current += 1
        setTick(tickRef.current)
      }
    }
    editor.on('transaction', handler)
    return () => { editor.off('transaction', handler) }
  }, [editor])

  const answers = storage?.answers ?? {}
  const cond = attrs.condition
  let visible = true
  if (cond?.type === 'simple' && cond.expression?.trim()) {
    visible = evaluateSimple(cond.expression, answers)
  }

  if (!visible) return <NodeViewWrapper as="span" style={{ display: 'none' }}><NodeViewContent style={{ display: 'none' }} /></NodeViewWrapper>

  return (
    <NodeViewWrapper as="span" style={{ display: 'inline' }}>
      <NodeViewContent style={{ display: 'inline' }} />
    </NodeViewWrapper>
  )
}

export const WorkspaceConditionalInlineNode = Node.create({
  name: 'conditionalInline',
  group: 'inline',
  inline: true,
  content: 'inline*',

  addAttributes() {
    return {
      label: {
        default: 'Условный текст',
        parseHTML: (el) => el.getAttribute('data-label') ?? 'Условный текст',
        renderHTML: (attrs) => ({ 'data-label': attrs.label }),
      },
      condition: {
        default: DEFAULT_CONDITION,
        parseHTML: (el) => {
          try { return JSON.parse(el.getAttribute('data-condition') ?? '{}') } catch { return DEFAULT_CONDITION }
        },
        renderHTML: (attrs) => ({ 'data-condition': JSON.stringify(attrs.condition) }),
      },
    }
  },

  parseHTML() {
    return [{ tag: 'span[data-type="conditional-inline"]' }]
  },

  renderHTML({ HTMLAttributes }) {
    return ['span', mergeAttributes(HTMLAttributes, { 'data-type': 'conditional-inline' }), 0]
  },

  addNodeView() {
    return ReactNodeViewRenderer(WorkspaceConditionalInlineView)
  },
})
