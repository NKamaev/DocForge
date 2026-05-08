import { useState, useEffect, useRef } from 'react'
import { Node, mergeAttributes } from '@tiptap/core'
import { ReactNodeViewRenderer, NodeViewWrapper } from '@tiptap/react'
import type { ReactNodeViewProps } from '@tiptap/react'
import { Tooltip } from 'antd'
import type { Parameter } from '../../types/template'
import type { ParameterFormat } from '../../types/templateModel'
import type { Questionnaire } from '../../types/document'
import { formatValue } from '../../utils/previewRenderer'

// ── Storage ───────────────────────────────────────────────────────────────────

export interface WorkspaceParamStorage {
  answers: Record<string, unknown>
  parameters: Parameter[]
  questionnaire: Questionnaire | null
  onAnswerChange: ((paramCode: string, value: unknown) => void) | null
  onParamFocus: ((paramCode: string) => void) | null
  highlightedParam: string | null
}

function findParamPage(
  questionnaire: Questionnaire | null,
  paramCode: string,
): { pageIndex: number; pageTitle: string } | null {
  if (!questionnaire) return null
  for (let i = 0; i < questionnaire.pages.length; i++) {
    const page = questionnaire.pages[i]
    if (page.questions.some(q => q.parameterCode === paramCode)) {
      return { pageIndex: i + 1, pageTitle: page.title }
    }
  }
  return null
}

// ── React component ───────────────────────────────────────────────────────────

function WorkspaceParameterFieldView({ node, editor }: ReactNodeViewProps) {
  const { parameterCode, display, format } = node.attrs as {
    parameterCode: string
    display: string
    format: ParameterFormat | null
  }

  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const storage     = (editor.storage as any).parameterField as WorkspaceParamStorage | undefined
  const param       = storage?.parameters?.find(p => p.code === parameterCode)
  const rawValue    = storage?.answers?.[parameterCode]
  const isHighlighted = storage?.highlightedParam === parameterCode
  const isEmpty     = rawValue === undefined || rawValue === null || rawValue === ''
  const displayVal  = formatValue(parameterCode, rawValue, storage?.parameters ?? [], format)

  // TipTap v3 does NOT re-render NodeViews on no-op transactions (it guards on
  // docChanged). Subscribe to 'transaction' event and look for the custom
  // 'answers-updated' meta that DocumentWorkspace dispatches when answers change.
  const tickRef = useRef(0)
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
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

  const handleLabelClick = (e: React.MouseEvent) => {
    e.stopPropagation()
    storage?.onParamFocus?.(parameterCode)
  }

  const stopProp = (e: React.SyntheticEvent) => e.stopPropagation()

  const borderColor = isHighlighted ? '#1677FF' : (isEmpty ? '#FFD591' : '#B7EB8F')
  const bg          = isHighlighted ? '#BAE0FF' : (isEmpty ? '#FFF7E6' : '#F6FFED')

  const pageInfo = findParamPage(storage?.questionnaire ?? null, parameterCode)
  const tooltipTitle = (
    <div>
      <div><strong>{param?.label || display || parameterCode}</strong></div>
      {pageInfo && (
        <div style={{ color: '#aaa', fontSize: 11 }}>
          Стр. {pageInfo.pageIndex}: {pageInfo.pageTitle}
        </div>
      )}
    </div>
  )

  return (
    <NodeViewWrapper as="span" style={{ display: 'inline' }}>
      <Tooltip title={tooltipTitle} mouseEnterDelay={0.4}>
        <span
          contentEditable={false}
          style={{
            display: 'inline',
            background: bg,
            border: `1.5px solid ${borderColor}`,
            borderRadius: 4,
            padding: '1px 5px',
            fontSize: 'inherit',
            fontFamily: 'inherit',
            lineHeight: 1.5,
            transition: 'all 0.2s',
            boxShadow: isHighlighted ? '0 0 0 2px rgba(22,119,255,0.2)' : undefined,
            verticalAlign: 'baseline',
            whiteSpace: 'pre-wrap',
            wordBreak: 'break-word',
            cursor: 'pointer',
          }}
          onClick={handleLabelClick}
          onMouseDown={stopProp}
        >
          {isEmpty ? `[${display || parameterCode}]` : displayVal}
        </span>
      </Tooltip>
    </NodeViewWrapper>
  )
}

// ── TipTap Node definition ────────────────────────────────────────────────────

export const WorkspaceParameterFieldNode = Node.create({
  name: 'parameterField',
  group: 'inline',
  inline: true,
  atom: true,

  addStorage(): WorkspaceParamStorage {
    return {
      answers: {},
      parameters: [],
      questionnaire: null,
      onAnswerChange: null,
      onParamFocus: null,
      highlightedParam: null,
    }
  },

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
      // value attr is used by renderPreview to inject the initial answer
      value: {
        default: '',
        parseHTML: (el) => el.getAttribute('data-value'),
        renderHTML: (attrs) => ({ 'data-value': attrs.value }),
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
    return ReactNodeViewRenderer(WorkspaceParameterFieldView)
  },
})
