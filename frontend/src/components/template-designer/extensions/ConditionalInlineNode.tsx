import { useRef, useState } from 'react'
import { Node, mergeAttributes } from '@tiptap/core'
import { ReactNodeViewRenderer, NodeViewWrapper, NodeViewContent } from '@tiptap/react'
import type { ReactNodeViewProps } from '@tiptap/react'
import { Button, Space, Tooltip } from 'antd'
import { DeleteOutlined, ScissorOutlined, SettingOutlined } from '@ant-design/icons'
import { useDesignerContext } from '../DesignerContext'
import ConditionBuilder from '../ConditionBuilder'
import type { Condition } from '../../../types/templateModel'
import type { BlockCondition } from './ConditionalBlockNode'

// ── Helpers ───────────────────────────────────────────────────────────────────

function toCondition(bc: BlockCondition): Condition {
  return {
    type: bc.type === 'python' ? 'PYTHON' : 'SIMPLE',
    expression: bc.expression,
    scriptId: bc.scriptId ?? null,
  }
}

function fromCondition(c: Condition): BlockCondition {
  return {
    type: c.type === 'PYTHON' ? 'python' : 'simple',
    expression: c.expression ?? '',
    scriptId: c.scriptId ?? undefined,
  }
}

// ── Types ─────────────────────────────────────────────────────────────────────

interface ConditionalInlineAttrs {
  condition: BlockCondition
  label: string
}

// ── Command augmentation ──────────────────────────────────────────────────────

declare module '@tiptap/core' {
  interface Commands<ReturnType> {
    conditionalInline: {
      wrapInConditionalInline: (attrs?: Partial<ConditionalInlineAttrs>) => ReturnType
      unwrapConditionalInline: () => ReturnType
    }
  }
}

// ── React view ────────────────────────────────────────────────────────────────

function ConditionalInlineView({ node, updateAttributes, deleteNode, editor }: ReactNodeViewProps) {
  const [hovered, setHovered] = useState(false)
  const [settingsOpen, setSettingsOpen] = useState(false)
  const hoverTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  const { parameters, published } = useDesignerContext()
  const attrs = node.attrs as ConditionalInlineAttrs

  // Hover handlers live on NodeViewWrapper (the outermost DOM node).
  // This avoids ProseMirror swallowing events inside the contenteditable zone.
  const startHover = () => {
    if (hoverTimerRef.current) clearTimeout(hoverTimerRef.current)
    setHovered(true)
  }
  const endHover = () => {
    hoverTimerRef.current = setTimeout(() => setHovered(false), 150)
  }

  const conditionText = attrs.condition.expression || '—'
  const conditionPreview =
    conditionText.length > 40 ? conditionText.slice(0, 40) + '...' : conditionText

  const panelVisible = hovered && !published

  return (
    <NodeViewWrapper
      as="span"
      style={{ display: 'inline-block', position: 'relative' }}
      onMouseEnter={startHover}
      onMouseLeave={endHover}
    >
      {/* Visible highlighted text */}
      <span
        style={{
          display: 'inline',
          background: '#FFF7E6',
          border: '1px solid #FFD591',
          borderRadius: 4,
          padding: '1px 4px',
        }}
      >
        <span
          contentEditable={false}
          style={{ fontSize: '0.8em', color: '#D46B08', userSelect: 'none', marginRight: 2 }}
        >
          ⚡
        </span>
        <NodeViewContent style={{ display: 'inline' }} />
      </span>

      {/* Floating controls panel — always rendered, CSS-toggled */}
      <span
        contentEditable={false}
        onMouseEnter={startHover}
        onMouseLeave={endHover}
        style={{
          position: 'absolute',
          bottom: '100%',
          left: 0,
          marginBottom: 2,
          background: '#fff',
          border: '1px solid #d9d9d9',
          borderRadius: 4,
          padding: '2px 6px',
          boxShadow: '0 2px 8px rgba(0,0,0,0.15)',
          zIndex: 100,
          display: 'inline-flex',
          alignItems: 'center',
          gap: 4,
          whiteSpace: 'nowrap',
          opacity: panelVisible ? 1 : 0,
          pointerEvents: panelVisible ? 'auto' : 'none',
          transition: 'opacity 0.1s',
        }}
      >
        <span style={{ fontSize: 11, color: '#595959' }}>
          {attrs.condition.type === 'python' ? 'Python' : 'Условие'}: {conditionPreview}
        </span>
        <Space size={0}>
          <Tooltip title="Настроить условие">
            <Button
              size="small"
              type="text"
              icon={<SettingOutlined />}
              onClick={() => setSettingsOpen(true)}
              style={{ color: '#8C8C8C' }}
            />
          </Tooltip>
          <Tooltip title="Убрать обёртку (текст сохранится)">
            <Button
              size="small"
              type="text"
              icon={<ScissorOutlined />}
              onClick={() => editor.chain().focus().unwrapConditionalInline().run()}
              style={{ color: '#8C8C8C' }}
            />
          </Tooltip>
          <Tooltip title="Удалить с контентом">
            <Button size="small" type="text" danger icon={<DeleteOutlined />} onClick={deleteNode} />
          </Tooltip>
        </Space>
      </span>

      <ConditionBuilder
        open={settingsOpen}
        condition={toCondition(attrs.condition)}
        parameters={parameters}
        onSave={(next: Condition) => updateAttributes({ condition: fromCondition(next) })}
        onClose={() => setSettingsOpen(false)}
      />
    </NodeViewWrapper>
  )
}

// ── TipTap Node definition ────────────────────────────────────────────────────

const DEFAULT_INLINE_CONDITION: BlockCondition = { type: 'simple', expression: '' }

export const ConditionalInlineNode = Node.create({
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
        default: DEFAULT_INLINE_CONDITION,
        parseHTML: (el) => {
          try {
            return JSON.parse(el.getAttribute('data-condition') ?? '{}')
          } catch {
            return DEFAULT_INLINE_CONDITION
          }
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
    return ReactNodeViewRenderer(ConditionalInlineView)
  },

  addCommands() {
    return {
      wrapInConditionalInline:
        (attrs = {}) =>
        ({ state, dispatch }) => {
          const { from, to, empty } = state.selection
          if (empty) return false

          const nodeType = state.schema.nodes[this.name]
          if (!nodeType) return false

          try {
            const slice = state.doc.slice(from, to)
            const newNode = nodeType.create(
              {
                label: attrs.label ?? 'Условный текст',
                condition: attrs.condition ?? DEFAULT_INLINE_CONDITION,
              },
              slice.content,
            )
            if (dispatch) {
              dispatch(state.tr.replaceWith(from, to, newNode))
            }
            return true
          } catch {
            return false
          }
        },

      unwrapConditionalInline:
        () =>
        ({ state, dispatch }) => {
          const { $from } = state.selection
          for (let depth = $from.depth; depth > 0; depth--) {
            const node = $from.node(depth)
            if (node.type.name === 'conditionalInline') {
              const pos = $from.before(depth)
              if (dispatch) {
                dispatch(state.tr.replaceWith(pos, pos + node.nodeSize, node.content))
              }
              return true
            }
          }
          return false
        },
    }
  },
})
