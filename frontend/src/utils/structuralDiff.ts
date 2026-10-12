import type { JSONContent } from '@tiptap/core'

/**
 * A TipTap / ProseMirror JSON document. Structural diff works on the raw
 * `{ type: 'doc', content: [...] }` shape used by revision bodies.
 */
export type TipTapDoc = JSONContent

type Mark = NonNullable<JSONContent['marks']>[number]
type DiffKind = 'insert' | 'delete'

// ── Node classification ───────────────────────────────────────────────────────

/** Node types whose children are blocks (need block-level recursion). */
const CONTAINER_BLOCK_TYPES = new Set<string>([
  'listItem',
  'tableRow',
  'tableCell',
  'tableHeader',
  'blockquote',
  'bulletList',
  'orderedList',
  'numberedClause',
  'conditionalBlock',
  'diffBlock',
])

/** Broad set of block-level node types — used to infer whether a `content`
 *  array holds blocks or inline nodes. */
const BLOCK_LEVEL_TYPES = new Set<string>([
  ...CONTAINER_BLOCK_TYPES,
  'paragraph',
  'heading',
  'table',
  'codeBlock',
  'horizontalRule',
  'pageBreak',
])

/**
 * Node types that a container constrains to a fixed child type, so a
 * `diffBlock` (group `block`) cannot replace them as a sibling:
 * `listItem` inside `bulletList`/`orderedList`, `tableRow` inside `table`, and
 * the cells inside a `tableRow`. Added/removed instances of these are marked
 * *inside* the item, never wrapped as a sibling diff block.
 */
const NAMED_ITEM_TYPES = new Set<string>([
  'listItem',
  'tableRow',
  'tableCell',
  'tableHeader',
])

/**
 * Block types whose schema forbids inline marks (`marks: ''`), so the inline
 * diff must never attach `diffInsert`/`diffDelete` to their text. Verified
 * against `getSchema(buildWorkspaceExtensions({ diff: true }))` — `codeBlock`
 * is the only such node in the workspace schema.
 */
const NO_MARK_BLOCK_TYPES = new Set<string>(['codeBlock'])

function nodeType(node: JSONContent): string {
  return node.type ?? ''
}

function textOf(node: JSONContent): string {
  if (typeof node.text === 'string') return node.text
  if (!node.content?.length) return ''
  return node.content.map((child) => textOf(child)).join('')
}

/**
 * Stable identity for leaf/atom nodes (no text, no content), e.g.
 * `parameterField`, `clauseRef`, `pageBreak`. Volatile attributes injected at
 * render time (`value`, `_resolvedLabel`) are ignored.
 */
function leafKey(node: JSONContent): string {
  if (node.content?.length || typeof node.text === 'string') return ''
  const attrs: Record<string, unknown> = { ...(node.attrs ?? {}) }
  delete attrs.value
  delete attrs._resolvedLabel
  try {
    return JSON.stringify(attrs)
  } catch {
    return ''
  }
}

/**
 * Concatenated `leafKey` of every atom descendant. `textOf` yields an empty
 * string for atom leaves and `leafKey` is empty for nodes that carry
 * `content`, so without this a block whose only change is an embedded atom
 * (`parameterField`, `clauseRef`) would hash identically on both sides and be
 * skipped as unchanged.
 */
function descendantAtomKeys(node: JSONContent): string {
  if (!node.content?.length) return ''
  const keys: string[] = []
  for (const child of node.content) {
    if (typeof child.text === 'string') continue
    if (child.content?.length) {
      const nested = descendantAtomKeys(child)
      if (nested) keys.push(nested)
    } else {
      const key = leafKey(child)
      if (key) keys.push(key)
    }
  }
  return keys.join('\u0001')
}

/** Block signature for LCS: node type + concatenated text + atom keys. */
function blockSignature(node: JSONContent): string {
  return `${nodeType(node)}\u0000${textOf(node)}\u0000${leafKey(node)}\u0000${descendantAtomKeys(node)}`
}

/** Whether a node's `content` array holds block-level children. */
function isBlockContent(node: JSONContent): boolean {
  if (CONTAINER_BLOCK_TYPES.has(nodeType(node))) return true
  const first = node.content?.[0]
  return first ? BLOCK_LEVEL_TYPES.has(nodeType(first)) : false
}

// ── LCS ───────────────────────────────────────────────────────────────────────

/** Longest-common-subsequence alignment of two key sequences. */
function lcsMatches(a: string[], b: string[]): Array<[number, number]> {
  const n = a.length
  const m = b.length
  const dp: number[][] = Array.from({ length: n + 1 }, () => new Array<number>(m + 1).fill(0))

  for (let i = n - 1; i >= 0; i--) {
    for (let j = m - 1; j >= 0; j--) {
      dp[i][j] = a[i] === b[j]
        ? dp[i + 1][j + 1] + 1
        : Math.max(dp[i + 1][j], dp[i][j + 1])
    }
  }

  const matches: Array<[number, number]> = []
  let i = 0
  let j = 0
  while (i < n && j < m) {
    if (a[i] === b[j]) {
      matches.push([i, j])
      i++
      j++
    } else if (dp[i + 1][j] >= dp[i][j + 1]) {
      i++
    } else {
      j++
    }
  }
  return matches
}

// ── Block-level diff ──────────────────────────────────────────────────────────

function wrapDiffBlock(block: JSONContent, kind: DiffKind): JSONContent {
  return { type: 'diffBlock', attrs: { kind }, content: [block] }
}

/**
 * Mark an added/removed child with a diff marker that is valid for its parent.
 *
 * Block-group nodes get a `diffBlock` wrapper (valid wherever block content is
 * allowed). "Named items" that a container constrains to a fixed child type
 * (`listItem` in a list, `tableRow` in a table, cells in a row) keep their
 * identity and receive the marker internally — wrapping them as a sibling
 * `diffBlock` produces schema-invalid nesting (e.g. `bulletList > diffBlock`).
 */
function wrapGapNode(node: JSONContent, kind: DiffKind): JSONContent {
  if (NAMED_ITEM_TYPES.has(nodeType(node))) return markNamedItem(node, kind)
  return wrapDiffBlock(node, kind)
}

/**
 * Preserve a named item and push the diff marker inside it.
 *
 * - `listItem` content is `paragraph block*`: a `diffBlock` may not replace the
 *   required leading paragraph, so the item's inline content is marked with
 *   `diffInsert`/`diffDelete` instead (valid for `paragraph block*`).
 * - `tableRow`/`tableCell`/`tableHeader` can host a `diffBlock` after their
 *   leading structure, so block leaves inside them are wrapped, recursing
 *   through nested named items (`tableRow > tableCell > diffBlock > paragraph`).
 */
function markNamedItem(item: JSONContent, kind: DiffKind): JSONContent {
  if (nodeType(item) === 'listItem') return markInlineDeep(item, kind)
  const content = item.content
  if (!content?.length) return item
  return { ...item, content: content.map((child) => markNamedItemChild(child, kind)) }
}

function markNamedItemChild(child: JSONContent, kind: DiffKind): JSONContent {
  if (NAMED_ITEM_TYPES.has(nodeType(child))) return markNamedItem(child, kind)
  return wrapDiffBlock(child, kind)
}

/**
 * Recursively mark the inline content of a node with a diff mark, leaving the
 * block structure untouched. Text nodes and inline atom leaves (e.g.
 * `parameterField`) receive the mark; block nodes are traversed only.
 */
function markInlineDeep(node: JSONContent, kind: DiffKind): JSONContent {
  // No-mark blocks (e.g. `codeBlock`) reject `diffInsert`/`diffDelete` on their
  // text, so mark the block as a whole instead. A `diffBlock` is a block-group
  // node and therefore valid wherever a block child is allowed — e.g. inside
  // `listItem`'s `paragraph block*` after its leading paragraph.
  if (NO_MARK_BLOCK_TYPES.has(nodeType(node))) return wrapDiffBlock(node, kind)
  const mark = kind === 'insert' ? 'diffInsert' : 'diffDelete'
  if (typeof node.text === 'string') {
    return { ...node, marks: withDiffMark(node.marks, mark) }
  }
  if (node.content?.length) {
    return { ...node, content: node.content.map((child) => markInlineDeep(child, kind)) }
  }
  // Block leaves (horizontalRule, pageBreak, …) cannot carry marks.
  if (BLOCK_LEVEL_TYPES.has(nodeType(node))) return node
  return { ...node, marks: withDiffMark(node.marks, mark) }
}

function diffKind(node: JSONContent): string {
  return String(node.attrs?.['kind'] ?? '')
}

function mergeAdjacentBlocks(blocks: JSONContent[]): JSONContent[] {
  const out: JSONContent[] = []
  for (const block of blocks) {
    const last = out[out.length - 1]
    if (
      last &&
      nodeType(last) === 'diffBlock' &&
      nodeType(block) === 'diffBlock' &&
      diffKind(last) === diffKind(block)
    ) {
      last.content = [...(last.content ?? []), ...(block.content ?? [])]
    } else {
      out.push(block)
    }
  }
  return out
}

/**
 * Block-level diff of two `content` arrays. Equal blocks are copied through
 * unchanged, removed blocks are wrapped in a `diffBlock { kind: 'delete' }`
 * and added blocks in a `diffBlock { kind: 'insert' }`. Camouflaged
 * replacements (same node type, edited text) are recursed into.
 */
function diffBlocks(target: JSONContent[], compare: JSONContent[]): JSONContent[] {
  const matches = lcsMatches(target.map(blockSignature), compare.map(blockSignature))
  const out: JSONContent[] = []
  let ti = 0
  let ci = 0

  for (const [mi, mj] of matches) {
    out.push(...resolveBlockGap(target.slice(ti, mi), compare.slice(ci, mj)))
    out.push(target[mi])
    ti = mi + 1
    ci = mj + 1
  }
  out.push(...resolveBlockGap(target.slice(ti), compare.slice(ci)))
  return mergeAdjacentBlocks(out)
}

/** Difference between two LCS matches — a run of removed and/or added blocks. */
function resolveBlockGap(target: JSONContent[], compare: JSONContent[]): JSONContent[] {
  if (!target.length) return compare.map((block) => wrapGapNode(block, 'delete'))
  if (!compare.length) return target.map((block) => wrapGapNode(block, 'insert'))

  const out: JSONContent[] = []
  const count = Math.max(target.length, compare.length)
  for (let k = 0; k < count; k++) {
    const t = target[k]
    const c = compare[k]
    if (t && c && nodeType(t) === nodeType(c)) {
      out.push(...diffSimilarBlock(t, c))
    } else {
      if (c) out.push(wrapGapNode(c, 'delete'))
      if (t) out.push(wrapGapNode(t, 'insert'))
    }
  }
  return out
}

/**
 * Two blocks of the same type — recurse (blocks), word-diff (inline), or
 * replace wholesale (no-mark blocks).
 *
 * A no-mark block such as `codeBlock` rejects `diffInsert`/`diffDelete` on its
 * text, so inlining the diff would produce a schema-invalid document
 * (`Invalid content for node codeBlock`). Instead the old block is emitted as a
 * `delete` `diffBlock` and the new one as an `insert` `diffBlock` — both valid
 * wherever block content is allowed. Returns an array because this case yields
 * two blocks while the others yield one.
 */
function diffSimilarBlock(target: JSONContent, compare: JSONContent): JSONContent[] {
  if (NO_MARK_BLOCK_TYPES.has(nodeType(target))) {
    return [wrapDiffBlock(compare, 'delete'), wrapDiffBlock(target, 'insert')]
  }
  if (isBlockContent(target)) {
    return [{ ...target, content: diffBlocks(target.content ?? [], compare.content ?? []) }]
  }
  return [{ ...target, content: diffInline(target.content ?? [], compare.content ?? []) }]
}

// ── Inline (word-level) diff ──────────────────────────────────────────────────

interface InlineUnit {
  key: string
  text?: string
  marks?: Mark[]
  node?: JSONContent
}

/** Split text into words and whitespace runs. */
function tokenize(text: string): string[] {
  return text.match(/\s+|\S+/g) ?? []
}

function marksSignature(marks?: Mark[]): string {
  if (!marks?.length) return ''
  return marks.map((m) => `${m.type}:${JSON.stringify(m.attrs ?? {})}`).join(',')
}

function toInlineUnits(nodes: JSONContent[]): InlineUnit[] {
  const units: InlineUnit[] = []
  for (const node of nodes) {
    if (typeof node.text === 'string') {
      const marks = node.marks
      const sig = marksSignature(marks)
      for (const token of tokenize(node.text)) {
        units.push({ key: `t:${token}\u0000${sig}`, text: token, marks })
      }
    } else {
      // Atom inline nodes (parameterField, clauseRef, …) are opaque tokens.
      units.push({ key: `n:${blockSignature(node)}`, node })
    }
  }
  return units
}

function withDiffMark(marks: Mark[] | undefined, type: 'diffInsert' | 'diffDelete'): Mark[] {
  return [...(marks ?? []), { type }]
}

function emitInlineUnit(unit: InlineUnit, kind: 'equal' | DiffKind): JSONContent {
  if (unit.node) {
    if (kind === 'equal') return unit.node
    return {
      ...unit.node,
      marks: withDiffMark(unit.node.marks, kind === 'insert' ? 'diffInsert' : 'diffDelete'),
    }
  }

  const node: JSONContent = { type: 'text', text: unit.text ?? '' }
  const marks = kind === 'equal'
    ? unit.marks
    : withDiffMark(unit.marks, kind === 'insert' ? 'diffInsert' : 'diffDelete')
  if (marks?.length) node.marks = marks
  return node
}

function mergeAdjacentText(nodes: JSONContent[]): JSONContent[] {
  const out: JSONContent[] = []
  for (const node of nodes) {
    const last = out[out.length - 1]
    if (
      last &&
      nodeType(last) === 'text' &&
      nodeType(node) === 'text' &&
      marksSignature(last.marks) === marksSignature(node.marks)
    ) {
      last.text = (last.text ?? '') + (node.text ?? '')
    } else {
      out.push(node)
    }
  }
  return out
}

/** Word-level inline diff: equal tokens plain, added/removed tokens marked. */
function diffInline(target: JSONContent[], compare: JSONContent[]): JSONContent[] {
  const t = toInlineUnits(target)
  const c = toInlineUnits(compare)
  const matches = lcsMatches(t.map((u) => u.key), c.map((u) => u.key))

  const out: JSONContent[] = []
  let ti = 0
  let ci = 0
  for (const [mi, mj] of matches) {
    for (const unit of c.slice(ci, mj)) out.push(emitInlineUnit(unit, 'delete'))
    for (const unit of t.slice(ti, mi)) out.push(emitInlineUnit(unit, 'insert'))
    out.push(emitInlineUnit(t[mi], 'equal'))
    ti = mi + 1
    ci = mj + 1
  }
  for (const unit of c.slice(ci)) out.push(emitInlineUnit(unit, 'delete'))
  for (const unit of t.slice(ti)) out.push(emitInlineUnit(unit, 'insert'))
  return mergeAdjacentText(out)
}

// ── Public API ────────────────────────────────────────────────────────────────

function docContent(body: TipTapDoc | null | undefined): JSONContent[] {
  if (!body || typeof body !== 'object') return []
  const content = body.content
  return Array.isArray(content) ? content : []
}

/**
 * Structural diff of two TipTap documents.
 *
 * `targetBody` is the newer/requested revision, `compareBody` the baseline.
 * Insertions are blocks/tokens present in target but not compare; deletions are
 * present in compare but not target. The result is a valid TipTap document:
 * equal content is copied through unchanged, removed/added blocks are wrapped
 * in `diffBlock` nodes, and edited text receives `diffDelete`/`diffInsert`
 * marks.
 */
export function structuralDiff(
  targetBody: TipTapDoc | null | undefined,
  compareBody: TipTapDoc | null | undefined,
): TipTapDoc {
  const target = docContent(targetBody)
  const compare = docContent(compareBody)

  if (!target.length && !compare.length) {
    return { type: 'doc', content: [] }
  }
  if (!target.length) {
    return { type: 'doc', content: compare.map((block) => wrapGapNode(block, 'delete')) }
  }
  if (!compare.length) {
    return { type: 'doc', content: target.map((block) => wrapGapNode(block, 'insert')) }
  }
  return { type: 'doc', content: diffBlocks(target, compare) }
}

/** Whether a structural-diff document contains any insert/delete markers. */
export function diffHasChanges(doc: TipTapDoc | null | undefined): boolean {
  let found = false
  const walk = (node: JSONContent): void => {
    if (found) return
    if (nodeType(node) === 'diffBlock') {
      found = true
      return
    }
    if (node.marks?.some((m) => m.type === 'diffInsert' || m.type === 'diffDelete')) {
      found = true
      return
    }
    node.content?.forEach(walk)
  }
  if (doc) walk(doc)
  return found
}
