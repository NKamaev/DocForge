import { useEffect, useState } from 'react'
import { Node, mergeAttributes } from '@tiptap/core'
import { ReactNodeViewRenderer, NodeViewWrapper, NodeViewContent } from '@tiptap/react'
import type { ReactNodeViewProps } from '@tiptap/react'
import {
  autoNumberingPluginKey,
  createAutoNumberingPlugin,
  type NumberingStyle,
} from '../template-designer/extensions/NumberedClauseNode'
import { evaluateSimple } from '../../utils/previewRenderer'
import type { WorkspaceParamStorage } from './WorkspaceParameterFieldNode'

interface ClauseCondition {
  type: 'simple' | 'python'
  expression: string
}

// ── Storage ───────────────────────────────────────────────────────────────────

export interface WorkspaceNumberedClauseStorage {
  hiddenPositions: Set<number>
}

// ── React view ────────────────────────────────────────────────────────────────

function WorkspaceNumberedClauseView({ node, getPos, editor }: ReactNodeViewProps) {
  const level = (node.attrs.level as number) || 1
  const condition = node.attrs.condition as ClauseCondition | null

  // Re-render when doc changes or when answers-updated meta is dispatched
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

  const pos = typeof getPos === 'function' ? getPos() : undefined
  const state = autoNumberingPluginKey.getState(editor.view.state)
  const label = pos !== undefined ? (state?.byPos.get(pos) ?? '') : ''

  // Evaluate own condition
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const paramStorage = (editor as any)?.storage?.parameterField as WorkspaceParamStorage | undefined
  const answers = paramStorage?.answers ?? {}
  let show = true
  if (condition?.type === 'simple' && condition.expression) {
    show = evaluateSimple(condition.expression, answers)
  }
  // python-условие: только бэкенд, на фронте показываем

  // Paragraph spacing attrs (set by ParagraphSpacingExtension on the numberedClause node)
  const spaceBefore     = node.attrs.spaceBefore     as string | null
  const spaceAfter      = node.attrs.spaceAfter      as string | null
  const marginLeftAttr  = node.attrs.marginLeft      as string | null
  const marginRight     = node.attrs.marginRight     as string | null
  const firstLineIndent = node.attrs.firstLineIndent as string | null

  const levelMargin = (level - 1) * 20
  const resolvedMarginLeft = marginLeftAttr
    ? (levelMargin > 0 ? `calc(${levelMargin}px + ${marginLeftAttr})` : marginLeftAttr)
    : levelMargin

  return (
    <NodeViewWrapper
      as="div"
      style={{
        marginLeft: resolvedMarginLeft,
        marginTop: spaceBefore ?? 2,
        marginBottom: spaceAfter ?? 2,
        marginRight: marginRight ?? undefined,
        display: show ? 'flow-root' : 'none',
      }}
    >
      <span
        contentEditable={false}
        style={{
          float: 'left',
          marginLeft: firstLineIndent ?? 0,
          marginRight: 6,
          fontWeight: 600,
          color: '#595959',
          userSelect: 'none',
          lineHeight: '1.5em',
          minWidth: `${level + 1}ch`,
        }}
      >
        {label}
      </span>
      <NodeViewContent as="div" style={{ minWidth: 0 }} />
    </NodeViewWrapper>
  )
}

// ── TipTap Node definition ────────────────────────────────────────────────────

export const WorkspaceNumberedClauseNode = Node.create({
  name: 'numberedClause',
  group: 'block',
  content: 'block+',

  addStorage(): WorkspaceNumberedClauseStorage {
    return { hiddenPositions: new Set<number>() }
  },

  addAttributes() {
    return {
      level: {
        default: 1,
        parseHTML: (el) => parseInt(el.getAttribute('data-level') ?? '1', 10),
        renderHTML: (attrs) => ({ 'data-level': attrs.level }),
      },
      numberingStyle: {
        default: 'decimal' as NumberingStyle,
        parseHTML: (el) => (el.getAttribute('data-numbering-style') ?? 'decimal') as NumberingStyle,
        renderHTML: (attrs) => ({ 'data-numbering-style': attrs.numberingStyle }),
      },
      listStart: {
        default: false,
        parseHTML: (el) => el.hasAttribute('data-list-start'),
        renderHTML: (attrs) => (attrs.listStart ? { 'data-list-start': '' } : {}),
      },
      id: {
        default: null as string | null,
        parseHTML: (el) => el.getAttribute('data-clause-id') ?? null,
        renderHTML: (attrs) => (attrs.id ? { 'data-clause-id': attrs.id } : {}),
      },
      condition: {
        default: null as ClauseCondition | null,
        parseHTML: (el) => {
          const raw = el.getAttribute('data-condition')
          if (!raw) return null
          try { return JSON.parse(raw) as ClauseCondition } catch { return null }
        },
        renderHTML: (attrs) => attrs.condition ? { 'data-condition': JSON.stringify(attrs.condition) } : {},
      },
    }
  },

  parseHTML() {
    return [{ tag: 'div[data-type="numbered-clause"]' }]
  },

  renderHTML({ HTMLAttributes }) {
    return ['div', mergeAttributes(HTMLAttributes, { 'data-type': 'numbered-clause' }), 0]
  },

  addNodeView() {
    return ReactNodeViewRenderer(WorkspaceNumberedClauseView)
  },

  addProseMirrorPlugins() {
    const editor = this.editor
    return [
      createAutoNumberingPlugin((pos) => {
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        const s = (editor as any)?.storage?.numberedClause as WorkspaceNumberedClauseStorage | undefined
        return s?.hiddenPositions?.has(pos) ?? false
      }),
    ]
  },
})
