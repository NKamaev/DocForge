import React, { useEffect, useState } from 'react'
import { Node, mergeAttributes } from '@tiptap/core'
import { Plugin, PluginKey, TextSelection } from '@tiptap/pm/state'
import type { ResolvedPos } from '@tiptap/pm/model'
import type { Node as PmNode } from '@tiptap/pm/model'
import { ReactNodeViewRenderer, NodeViewWrapper, NodeViewContent } from '@tiptap/react'
import type { ReactNodeViewProps } from '@tiptap/react'
import { Tooltip } from 'antd'
import { FilterOutlined } from '@ant-design/icons'
import ConditionBuilder from '../ConditionBuilder'
import { useDesignerContext } from '../DesignerContext'
import type { Condition } from '../../../types/templateModel'

// Lowercase-тип условия (хранится в attrs ноды)
interface ClauseCondition {
  type: 'simple' | 'python'
  expression: string
}

function toApiCondition(c: ClauseCondition): Condition {
  return { type: c.type === 'python' ? 'PYTHON' : 'SIMPLE', expression: c.expression, scriptId: null }
}
function fromApiCondition(c: Condition): ClauseCondition {
  return { type: c.type === 'PYTHON' ? 'python' : 'simple', expression: c.expression ?? '' }
}

// ── Types ─────────────────────────────────────────────────────────────────────

export type NumberingStyle = 'decimal' | 'alpha-ru' | 'alpha-en'

export interface AutoNumberingState {
  byPos: Map<number, string>   // pos → label (for node views)
  byId:  Map<string, string>   // id  → label (for ClauseRefNode)
}

// ── Command augmentation ──────────────────────────────────────────────────────

declare module '@tiptap/core' {
  interface Commands<ReturnType> {
    numberedClause: {
      /** Insert a new numbered clause at the given level and optional style */
      insertNumberedClause: (level?: number, style?: NumberingStyle) => ReturnType
      /** Change the level of the nearest numberedClause ancestor */
      setClauseLevel: (level: number) => ReturnType
      /** Change the numbering style of the nearest numberedClause ancestor */
      setClauseNumberingStyle: (style: NumberingStyle) => ReturnType
      /** Toggle "start new list" on the nearest numberedClause ancestor */
      toggleClauseListStart: () => ReturnType
    }
  }
}

// ── Formatting helpers ────────────────────────────────────────────────────────

const RU_ALPHA = 'абвгдежзийклмнопрстуфхцчшщэюя'
const EN_ALPHA = 'abcdefghijklmnopqrstuvwxyz'

function formatCounter(n: number, style: NumberingStyle): string {
  if (style === 'alpha-ru') return RU_ALPHA[n - 1] ?? String(n)
  if (style === 'alpha-en') return EN_ALPHA[n - 1] ?? String(n)
  return String(n)
}

// ── AutoNumberingPlugin ───────────────────────────────────────────────────────
//
// Plugin state: AutoNumberingState
//   byPos: Map<pos, label>  — read by NumberedClauseView (and workspace variant)
//   byId:  Map<id,  label>  — read by ClauseRefNode
//
// Labels are computed dynamically; they are NOT stored in node attrs.
// This avoids storing order-dependent state in the document JSON.

export const autoNumberingPluginKey = new PluginKey<AutoNumberingState>('autoNumbering')

export function createAutoNumberingPlugin(isHidden?: (pos: number) => boolean): Plugin {
  function buildLabels(doc: PmNode): AutoNumberingState {
    const byPos = new Map<number, string>()
    const byId  = new Map<string, string>()

    const counters: number[] = [0, 0, 0, 0, 0]
    // Track the numbering style last used at each level (for label segment formatting)
    const levelStyles: NumberingStyle[] = ['decimal', 'decimal', 'decimal', 'decimal', 'decimal']

    doc.descendants((node, pos) => {
      if (node.type.name !== 'numberedClause') return
      if (isHidden?.(pos)) return false

      const level = Math.min(Math.max((node.attrs.level as number) || 1, 1), 5)
      const style: NumberingStyle = (node.attrs.numberingStyle as NumberingStyle) || 'decimal'

      // Reset all counters if this node starts a new list
      if (node.attrs.listStart) {
        counters.fill(0)
      }

      // Reset child-level counters
      for (let i = level; i < 5; i++) counters[i] = 0

      counters[level - 1]++
      levelStyles[level - 1] = style

      // Build label from all levels 1..level using their respective styles
      const segments = counters.slice(0, level).map((c, i) => formatCounter(c, levelStyles[i]))
      const label = segments.join('.') + '.'

      byPos.set(pos, label)

      const id = node.attrs.id as string | null
      if (id) byId.set(id, label)
    })

    return { byPos, byId }
  }

  return new Plugin({
    key: autoNumberingPluginKey,
    state: {
      init(_, { doc }) {
        return buildLabels(doc)
      },
      apply(tr, old) {
        if (!tr.docChanged && !tr.getMeta('answers-updated')) return old
        return buildLabels(tr.doc)
      },
    },
  })
}

// ── Helpers ───────────────────────────────────────────────────────────────────

function getFirstTextStyle(node: PmNode): React.CSSProperties {
  let style: React.CSSProperties = {}
  node.descendants((child) => {
    if (child.isText && child.marks.length > 0) {
      const textStyleMark = child.marks.find((m) => m.type.name === 'textStyle')
      if (textStyleMark) {
        if (textStyleMark.attrs.fontFamily) style.fontFamily = textStyleMark.attrs.fontFamily as string
        if (textStyleMark.attrs.fontSize)   style.fontSize   = textStyleMark.attrs.fontSize   as string
        if (textStyleMark.attrs.fontWeight) style.fontWeight = textStyleMark.attrs.fontWeight  as string
      }
      return false // stop after first text node
    }
  })
  return style
}

// ── React view ────────────────────────────────────────────────────────────────

function NumberedClauseView({ node, getPos, editor, updateAttributes }: ReactNodeViewProps) {
  const level = (node.attrs.level as number) || 1
  const condition = node.attrs.condition as ClauseCondition | null
  const [conditionOpen, setConditionOpen] = useState(false)
  const { parameters } = useDesignerContext()

  // Force re-render when the document changes so the label stays current.
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

  const handleConditionSave = (next: Condition) => {
    if (!next.expression?.trim()) {
      updateAttributes({ condition: null })
    } else {
      updateAttributes({ condition: fromApiCondition(next) })
    }
  }

  const pos = typeof getPos === 'function' ? getPos() : undefined
  const state = autoNumberingPluginKey.getState(editor.view.state)
  const label = pos !== undefined ? (state?.byPos.get(pos) ?? '') : ''

  // Paragraph spacing attrs (set by ParagraphSpacingExtension)
  const spaceBefore     = node.attrs.spaceBefore     as string | null
  const spaceAfter      = node.attrs.spaceAfter      as string | null
  const marginLeftAttr  = node.attrs.marginLeft      as string | null
  const marginRight     = node.attrs.marginRight     as string | null
  const firstLineIndent = node.attrs.firstLineIndent as string | null

  const levelMargin = (level - 1) * 20
  const resolvedMarginLeft = marginLeftAttr
    ? (levelMargin > 0 ? `calc(${levelMargin}px + ${marginLeftAttr})` : marginLeftAttr)
    : levelMargin

  const markerBaseStyle: React.CSSProperties = {
    userSelect: 'none',
    lineHeight: '1.5em',
    minWidth: `${level + 1}ch`,
  }

  return (
    <NodeViewWrapper
      as="div"
      style={{
        marginLeft: resolvedMarginLeft,
        marginTop: spaceBefore ?? 2,
        marginBottom: spaceAfter ?? 2,
        marginRight: marginRight ?? undefined,
        // flow-root creates a BFC so the floated marker is contained within this box
        // without it, the float would escape and affect surrounding content
        display: 'flow-root',
      }}
    >
      {/* Number label — floated so subsequent text lines wrap to the wrapper's left edge
          (= clause marginLeft), not to the right edge of the marker.
          firstLineIndent shifts the marker rightward from the clause left margin,
          giving a "number position" effect as in Word. */}
      <span
        contentEditable={false}
        style={{
          ...markerBaseStyle,
          ...getFirstTextStyle(node),
          float: 'left',
          marginLeft: firstLineIndent ?? 0,
          marginRight: 4,
          display: 'inline-flex',
          alignItems: 'center',
          gap: 2,
        }}
      >
        {label}
        <Tooltip title={condition ? `Условие: ${condition.expression || '(пусто)'}` : 'Добавить условие на пункт'}>
          <FilterOutlined
            onClick={() => setConditionOpen(true)}
            style={{
              fontSize: 10,
              color: condition ? '#D46B08' : '#d9d9d9',
              cursor: 'pointer',
              flexShrink: 0,
            }}
          />
        </Tooltip>
      </span>

      <ConditionBuilder
        open={conditionOpen}
        condition={condition ? toApiCondition(condition) : { type: 'SIMPLE', expression: '', scriptId: null }}
        parameters={parameters}
        onSave={handleConditionSave}
        onClose={() => setConditionOpen(false)}
      />

      {/* Editable content — block element; first line flows beside the float,
          subsequent lines wrap back to the block's left edge (= 0 within wrapper) */}
      <NodeViewContent as="div" style={{ minWidth: 0 }} />
    </NodeViewWrapper>
  )
}

// ── TipTap Node definition ────────────────────────────────────────────────────

export const NumberedClauseNode = Node.create({
  name: 'numberedClause',
  group: 'block',
  content: 'block+',

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
    return ReactNodeViewRenderer(NumberedClauseView)
  },

  addCommands() {
    return {
      insertNumberedClause:
        (level = 1, style: NumberingStyle = 'decimal') =>
        ({ commands }) =>
          commands.insertContent({
            type: this.name,
            attrs: { level, numberingStyle: style, id: crypto.randomUUID() },
            content: [{ type: 'paragraph' }],
          }),

      setClauseLevel:
        (level) =>
        ({ commands }) =>
          commands.updateAttributes(this.name, { level }),

      setClauseNumberingStyle:
        (style) =>
        ({ commands }) =>
          commands.updateAttributes(this.name, { numberingStyle: style }),

      toggleClauseListStart:
        () =>
        ({ editor: e, commands }) => {
          const current = e.isActive(this.name)
            ? (e.getAttributes(this.name).listStart as boolean)
            : false
          return commands.updateAttributes(this.name, { listStart: !current })
        },
    }
  },

  addKeyboardShortcuts() {
    // Helper: find the nearest numberedClause ancestor depth, or -1 if not found
    const findClauseDepth = ($from: ResolvedPos) => {
      let depth = $from.depth
      while (depth > 0 && $from.node(depth).type.name !== this.name) depth--
      return $from.node(depth).type.name === this.name ? depth : -1
    }

    return {
      // ── Enter ──────────────────────────────────────────────────────────────
      // • Empty clause  → replace the clause with a clean paragraph (exit the list)
      // • Non-empty     → create a new clause of the same level after the current one
      Enter: () => {
        if (!this.editor.isActive(this.name)) return false

        const { state } = this.editor
        const { $from, empty } = state.selection
        if (!empty) return false  // let default behaviour handle selection replacement

        const clauseDepth = findClauseDepth($from)
        if (clauseDepth < 0) return false

        const clauseNode  = $from.node(clauseDepth)
        const clauseStart = $from.before(clauseDepth)
        const clauseEnd   = $from.after(clauseDepth)
        const isClauseEmpty = clauseNode.textContent.length === 0

        if (isClauseEmpty) {
          // Empty clause + Enter: replace with a plain paragraph (no spacing attrs)
          // so the user exits the numbered-clause "mode"
          return this.editor
            .chain()
            .deleteRange({ from: clauseStart, to: clauseEnd })
            .insertContentAt(clauseStart, { type: 'paragraph' })
            .setTextSelection(clauseStart + 1)
            .run()
        }

        // Non-empty clause + Enter: split the clause at the cursor position.
        // Text before the cursor stays in the current clause;
        // text after the cursor moves to the new clause.
        //
        // tr.split(pos, depth, typesAfter) typesAfter ordering:
        //   index 0 = outermost new node (the new numberedClause)
        //   index 1 = next level inward (the new paragraph, null = keep same type)
        const clauseType = state.schema.nodes[this.name]
        const splitDepth = $from.depth - clauseDepth + 1
        const typesAfter = Array.from({ length: splitDepth }, (_, i) =>
          i === 0
            ? {
                type: clauseType,
                attrs: {
                  level:           clauseNode.attrs.level,
                  numberingStyle:  clauseNode.attrs.numberingStyle,
                  id:              crypto.randomUUID(),
                  spaceBefore:     clauseNode.attrs.spaceBefore,
                  spaceAfter:      clauseNode.attrs.spaceAfter,
                  marginLeft:      clauseNode.attrs.marginLeft,
                  marginRight:     clauseNode.attrs.marginRight,
                  firstLineIndent: clauseNode.attrs.firstLineIndent,
                },
              }
            : null,
        )

        const { tr } = state
        tr.split($from.pos, splitDepth, typesAfter)
        // After the split, the mapped position of the old cursor lands at the
        // very start of the new clause's paragraph — place the cursor there.
        tr.setSelection(TextSelection.create(tr.doc, tr.mapping.map($from.pos)))
        this.editor.view.dispatch(tr)
        return true
      },

      // ── Backspace ───────────────────────────────────────────────────────────
      // Cursor at the very start of an empty clause: delete the clause and move
      // the cursor to the end of the previous block.
      // This prevents ProseMirror from joining the clause's paragraph into the
      // previous numberedClause and "inheriting" its spacing in the toolbar.
      Backspace: () => {
        if (!this.editor.isActive(this.name)) return false

        const { state } = this.editor
        const { $from, empty } = state.selection
        if (!empty) return false
        if ($from.parentOffset !== 0) return false  // not at block start

        const clauseDepth = findClauseDepth($from)
        if (clauseDepth < 0) return false

        // Only intercept when cursor is in the FIRST child block of the clause
        if ($from.index(clauseDepth) !== 0) return false

        const clauseNode  = $from.node(clauseDepth)
        const clauseStart = $from.before(clauseDepth)
        const clauseEnd   = $from.after(clauseDepth)
        const isClauseEmpty = clauseNode.textContent.length === 0

        if (isClauseEmpty) {
          // Delete the empty clause; cursor lands at end of the previous block
          return this.editor
            .chain()
            .deleteRange({ from: clauseStart, to: clauseEnd })
            .run()
        }

        // Clause has content: lift it to a plain paragraph so the user can freely
        // edit without the clause's formatting being applied
        const firstChild = clauseNode.firstChild
        if (!firstChild) return false

        return this.editor
          .chain()
          .deleteRange({ from: clauseStart, to: clauseEnd })
          .insertContentAt(clauseStart, {
            type: 'paragraph',
            // Preserve inline text (marks etc.) but drop all block-level attrs
            content: firstChild.content.toJSON() as [],
          })
          .setTextSelection(clauseStart + 1)
          .run()
      },
    }
  },

  addProseMirrorPlugins() {
    return [createAutoNumberingPlugin()]
  },
})
