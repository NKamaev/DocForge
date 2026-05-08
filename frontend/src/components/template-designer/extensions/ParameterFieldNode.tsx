import { useState } from 'react'
import { Node, mergeAttributes } from '@tiptap/core'
import { ReactNodeViewRenderer, NodeViewWrapper } from '@tiptap/react'
import type { ReactNodeViewProps } from '@tiptap/react'
import { Popover, Typography } from 'antd'
import type { ParameterFormat, ParameterType } from '../../../types/templateModel'
import { useDesignerContext } from '../DesignerContext'
import ParameterFormatSelector from '../ParameterFormatSelector'

const { Text } = Typography

// ── Command type augmentation ─────────────────────────────────────────────────
declare module '@tiptap/core' {
  interface Commands<ReturnType> {
    parameterField: {
      insertParameterField: (attrs: { parameterCode: string; display: string }) => ReturnType
    }
  }
}

// ── Format label helper ───────────────────────────────────────────────────────

const CASE_ABBR: Record<string, string> = {
  NOMINATIVE:    'ИМ',
  GENITIVE:      'РОД',
  DATIVE:        'ДАТ',
  ACCUSATIVE:    'ВИН',
  INSTRUMENTAL:  'ТВ',
  PREPOSITIONAL: 'ПР',
}

function formatLabel(fmt: ParameterFormat | null | undefined): string | null {
  if (!fmt) return null
  const parts: string[] = []
  if (fmt.dateFormat)    parts.push(fmt.dateFormat)
  if (fmt.numberFormat)  parts.push(fmt.numberFormat)
  if (fmt.unit)          parts.push(fmt.unit)
  if (fmt.fioFormat)     parts.push(fmt.fioFormat)
  if (fmt.grammaticalCase && fmt.grammaticalCase !== 'NOMINATIVE')
    parts.push(CASE_ABBR[fmt.grammaticalCase] ?? fmt.grammaticalCase)
  return parts.length > 0 ? parts.join(' / ') : null
}

// ── React component rendered inside the editor ────────────────────────────────

function ParameterFieldView({ node, updateAttributes }: ReactNodeViewProps) {
  const { parameterCode, display, format } = node.attrs as {
    parameterCode: string
    display: string
    format: ParameterFormat | null
  }

  const [popoverOpen, setPopoverOpen] = useState(false)
  const { parameters, published } = useDesignerContext()
  const param = parameters.find(p => p.code === parameterCode)
  const label = formatLabel(format)

  const handleFormatChange = (fmt: ParameterFormat | null) => {
    updateAttributes({ format: fmt })
  }

  const popoverContent = param ? (
    <div style={{ width: 320 }}>
      <Text type="secondary" style={{ display: 'block', marginBottom: 8, fontSize: 12 }}>
        Переопределяет формат только для этого поля. Оставьте пустым — будет использован формат параметра.
      </Text>
      <ParameterFormatSelector
        paramType={param.type as ParameterType}
        value={format}
        onChange={handleFormatChange}
      />
    </div>
  ) : null

  return (
    <NodeViewWrapper
      as="span"
      style={{ display: 'inline' }}
    >
      <Popover
        open={!published && popoverOpen && !!popoverContent}
        onOpenChange={published ? undefined : setPopoverOpen}
        content={popoverContent}
        title={`Формат поля: ${display}`}
        trigger={published ? [] : 'click'}
        placement="bottomLeft"
        destroyTooltipOnHide
      >
        <span
          data-parameter-code={parameterCode}
          style={{
            display: 'inline-flex',
            flexDirection: 'column',
            alignItems: 'flex-start',
            background: '#E6F1FB',
            border: `1px solid ${popoverOpen ? '#1677FF' : '#85B7EB'}`,
            borderRadius: 4,
            padding: '2px 6px',
            fontSize: 13,
            cursor: published ? 'default' : 'pointer',
            userSelect: 'none',
            fontFamily: 'sans-serif',
            lineHeight: 1.3,
            verticalAlign: 'middle',
            transition: 'border-color 0.15s',
          }}
        >
          <span>[{display}]</span>
          {label && (
            <span style={{ fontSize: 10, color: '#4096FF', lineHeight: 1 }}>{label}</span>
          )}
        </span>
      </Popover>
    </NodeViewWrapper>
  )
}

// ── TipTap Node definition ────────────────────────────────────────────────────
export const ParameterFieldNode = Node.create({
  name: 'parameterField',
  group: 'inline',
  inline: true,
  atom: true,

  addAttributes() {
    return {
      parameterCode: {
        default: null,
        parseHTML: (el) => el.getAttribute('data-parameter-code'),
        renderHTML: (attrs) => ({ 'data-parameter-code': attrs.parameterCode }),
      },
      display: {
        default: '',
        parseHTML: (el) => el.getAttribute('data-display'),
        renderHTML: (attrs) => ({ 'data-display': attrs.display }),
      },
      format: {
        default: null,
        parseHTML: (el) => {
          const raw = el.getAttribute('data-format')
          if (!raw) return null
          try { return JSON.parse(raw) } catch { return null }
        },
        renderHTML: (attrs) => ({
          'data-format': attrs.format ? JSON.stringify(attrs.format) : undefined,
        }),
      },
    }
  },

  parseHTML() {
    return [{ tag: 'span[data-type="parameter-field"]' }]
  },

  renderHTML({ HTMLAttributes }) {
    return ['span', mergeAttributes(HTMLAttributes, { 'data-type': 'parameter-field' })]
  },

  addNodeView() {
    return ReactNodeViewRenderer(ParameterFieldView)
  },

  addCommands() {
    return {
      insertParameterField:
        (attrs) =>
        ({ commands }) =>
          commands.insertContent({
            type: this.name,
            attrs,
          }),
    }
  },
})
