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

interface ConditionalBlockAttrs {
  condition: BlockCondition
  label: string
  collapsed: boolean
}

const DEFAULT_CONDITION: BlockCondition = { type: 'simple', expression: '' }

function WorkspaceConditionalBlockView({ node, editor }: ReactNodeViewProps) {
  const attrs = node.attrs as ConditionalBlockAttrs

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
  // python conditions: always show (require server evaluation)

  return (
    <NodeViewWrapper as="div" style={{ display: visible ? undefined : 'none' }}>
      <NodeViewContent as="div" />
    </NodeViewWrapper>
  )
}

export const WorkspaceConditionalBlockNode = Node.create({
  name: 'conditionalBlock',
  group: 'block',
  content: 'block+',

  addAttributes() {
    return {
      label: {
        default: 'Условный блок',
        parseHTML: (el) => el.getAttribute('data-label') ?? 'Условный блок',
        renderHTML: (attrs) => ({ 'data-label': attrs.label }),
      },
      condition: {
        default: DEFAULT_CONDITION,
        parseHTML: (el) => {
          try { return JSON.parse(el.getAttribute('data-condition') ?? '{}') } catch { return DEFAULT_CONDITION }
        },
        renderHTML: (attrs) => ({ 'data-condition': JSON.stringify(attrs.condition) }),
      },
      collapsed: {
        default: false,
        parseHTML: (el) => el.getAttribute('data-collapsed') === 'true',
        renderHTML: (attrs) => ({ 'data-collapsed': attrs.collapsed }),
      },
    }
  },

  parseHTML() {
    return [{ tag: 'div[data-type="conditional-block"]' }]
  },

  renderHTML({ HTMLAttributes }) {
    return ['div', mergeAttributes(HTMLAttributes, { 'data-type': 'conditional-block' }), 0]
  },

  addNodeView() {
    return ReactNodeViewRenderer(WorkspaceConditionalBlockView)
  },
})
