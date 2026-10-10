import { useRef, useState } from 'react'
import { Node, mergeAttributes } from '@tiptap/core'
import { ReactNodeViewRenderer, NodeViewWrapper, NodeViewContent } from '@tiptap/react'
import type { ReactNodeViewProps } from '@tiptap/react'
import { Button } from '@/components/ui/button'
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from '@/components/ui/tooltip'
import { Settings, Scissors, Trash2 } from 'lucide-react'
import { useDesignerContext } from '../DesignerContext'
import ConditionBuilder from '../ConditionBuilder'
import type { Condition } from '../../../types/templateModel'
import type { BlockCondition } from './ConditionalBlockNode'

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

interface ConditionalInlineAttrs {
  condition: BlockCondition
  label: string
}

declare module '@tiptap/core' {
  interface Commands<ReturnType> {
    conditionalInline: {
      wrapInConditionalInline: (attrs?: Partial<ConditionalInlineAttrs>) => ReturnType
      unwrapConditionalInline: () => ReturnType
    }
  }
}

function ConditionalInlineView({ node, updateAttributes, deleteNode, editor }: ReactNodeViewProps) {
  const [hovered, setHovered] = useState(false)
  const [settingsOpen, setSettingsOpen] = useState(false)
  const hoverTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  const { parameters, published } = useDesignerContext()
  const attrs = node.attrs as ConditionalInlineAttrs

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

      <span
        contentEditable={false}
        onMouseEnter={startHover}
        onMouseLeave={endHover}
        className={cn(
          "absolute bottom-full left-0 mb-0.5 bg-white border border-line rounded-md px-1.5 py-0.5 shadow-lg z-50 inline-flex items-center gap-1 whitespace-nowrap transition-opacity",
          panelVisible ? "opacity-100 pointer-events-auto" : "opacity-0 pointer-events-none"
        )}
      >
        <span className="text-xs text-muted-foreground mr-1">
          {attrs.condition.type === 'python' ? 'Python' : 'Условие'}: {conditionPreview}
        </span>
        <Tooltip>
          <TooltipTrigger asChild>
            <Button
              variant="ghost"
              size="icon-sm"
              className="h-6 w-6 text-muted-foreground hover:text-foreground"
              onClick={() => setSettingsOpen(true)}
            >
              <Settings className="size-3.5" />
            </Button>
          </TooltipTrigger>
          <TooltipContent>Настроить условие</TooltipContent>
        </Tooltip>
        <Tooltip>
          <TooltipTrigger asChild>
            <Button
              variant="ghost"
              size="icon-sm"
              className="h-6 w-6 text-muted-foreground hover:text-foreground"
              onClick={() => editor.chain().focus().unwrapConditionalInline().run()}
            >
              <Scissors className="size-3.5" />
            </Button>
          </TooltipTrigger>
          <TooltipContent>Убрать обёртку (текст сохранится)</TooltipContent>
        </Tooltip>
        <Tooltip>
          <TooltipTrigger asChild>
            <Button
              variant="ghost"
              size="icon-sm"
              className="h-6 w-6 text-destructive hover:text-destructive hover:bg-destructive/10"
              onClick={deleteNode}
            >
              <Trash2 className="size-3.5" />
            </Button>
          </TooltipTrigger>
          <TooltipContent>Удалить с контентом</TooltipContent>
        </Tooltip>
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

import { cn } from '@/lib/utils'

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
