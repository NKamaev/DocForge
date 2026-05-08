import { useEffect, useState } from 'react'
import { Node, mergeAttributes } from '@tiptap/core'
import { ReactNodeViewRenderer, NodeViewWrapper } from '@tiptap/react'
import type { ReactNodeViewProps } from '@tiptap/react'
import type { Phrasing } from '../../types/templateModel'
import type { Parameter } from '../../types/template'
import { evaluateSimple } from '../../utils/previewRenderer'

// ── Storage ───────────────────────────────────────────────────────────────────

export interface PhrasingsRefStorage {
  phrasings: Phrasing[]
}

// ── Inline content renderer ───────────────────────────────────────────────────

function renderInlineContent(
  content: Record<string, unknown>[],
  answers: Record<string, unknown>,
  parameters: Parameter[],
): string {
  return content.map(node => {
    if (node.type === 'text') return String(node.text ?? '')
    if (node.type === 'parameterField') {
      const code = ((node.attrs as Record<string, unknown>)?.parameterCode) as string
      const answer = answers[code]
      if (answer === undefined || answer === null || answer === '') return ''
      const param = parameters.find(p => p.code === code)
      if (param?.type === 'BOOLEAN') return (answer === true || answer === 'true') ? 'Да' : 'Нет'
      return String(answer)
    }
    return ''
  }).join('')
}

// ── React view ────────────────────────────────────────────────────────────────

function WorkspacePhrasingsRefView({ node, editor }: ReactNodeViewProps) {
  const { phrasingsIds, phrasingsId, separator } = node.attrs as {
    phrasingsIds: string[]
    phrasingsId: string
    separator: string
  }

  const [, setTick] = useState(0)
  useEffect(() => {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const handler = ({ transaction }: { transaction: any }) => {
      if (transaction.getMeta('answers-updated')) setTick(t => t + 1)
    }
    editor.on('transaction', handler)
    return () => { editor.off('transaction', handler) }
  }, [editor])

  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const storage = (editor as any)?.storage?.phrasingsRef as PhrasingsRefStorage | undefined
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const paramStorage = (editor as any)?.storage?.parameterField
  const phrasings: Phrasing[] = storage?.phrasings ?? []
  const answers: Record<string, unknown> = paramStorage?.answers ?? {}
  const parameters: Parameter[] = paramStorage?.parameters ?? []

  // New format or legacy fallback
  const allIds = phrasingsIds?.length ? phrasingsIds : (phrasingsId ? [phrasingsId] : [])

  if (allIds.length === 0) {
    return <NodeViewWrapper as="span" contentEditable={false} />
  }

  const actualSep = separator === '\\n' || separator === '\n' ? '\n' : (separator ?? ', ')

  const parts: string[] = []
  for (const id of allIds) {
    const phrasing = phrasings.find(p => p.id === id)
    if (!phrasing?.variants?.length) continue

    const variant = phrasing.variants.find(v => {
      if (!v.condition) return true
      if (v.condition.type === 'SIMPLE') {
        return evaluateSimple(v.condition.expression ?? '', answers)
      }
      return true // python: show by default on client
    }) ?? phrasing.variants[phrasing.variants.length - 1]

    if (!variant?.content?.length) continue

    const text = renderInlineContent(
      variant.content as Record<string, unknown>[],
      answers,
      parameters,
    )
    if (text) parts.push(text)
  }

  if (parts.length === 0) {
    return <NodeViewWrapper as="span" contentEditable={false} />
  }

  if (actualSep === '\n') {
    return (
      <NodeViewWrapper as="span" contentEditable={false}>
        {parts.map((part, i) => (
          <span key={i}>{i > 0 && <br />}{part}</span>
        ))}
      </NodeViewWrapper>
    )
  }

  return (
    <NodeViewWrapper as="span" contentEditable={false}>
      {parts.join(actualSep)}
    </NodeViewWrapper>
  )
}

// ── TipTap Node definition ────────────────────────────────────────────────────

export const WorkspacePhrasingsRefNode = Node.create({
  name: 'phrasingsRef',
  group: 'inline',
  inline: true,
  atom: true,

  addStorage(): PhrasingsRefStorage {
    return { phrasings: [] }
  },

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
      // ── Legacy attrs ─────────────────────────────────────────────────────
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
    return ReactNodeViewRenderer(WorkspacePhrasingsRefView)
  },
})
