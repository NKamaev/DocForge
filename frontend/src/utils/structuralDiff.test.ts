import { describe, it, expect } from 'vitest'
import type { JSONContent } from '@tiptap/core'
import { getSchema } from '@tiptap/core'
import { Node as PMNode } from 'prosemirror-model'
import { structuralDiff, diffHasChanges } from './structuralDiff'
import { buildWorkspaceExtensions } from '../components/questionnaire/extensions/buildWorkspaceExtensions'

// ── schema guard ──────────────────────────────────────────────────────────────
//
// The diff output is rendered by the same TipTap schema as the document editor.
// Any representation the editor could not parse (e.g. a `diffBlock` sitting
// where a `listItem`/`tableRow` is required) is a regression, so every produced
// document is checked against the real workspace schema.

const schema = getSchema(buildWorkspaceExtensions({ diff: true }))

/** Asserts that `node` is a valid document for the workspace TipTap schema. */
function expectValid(node: JSONContent | null | undefined): void {
  const json: JSONContent = node ?? { type: 'doc', content: [] }
  expect(() => PMNode.fromJSON(schema, json).check()).not.toThrow()
}

// ── builders ──────────────────────────────────────────────────────────────────

const doc = (...content: JSONContent[]): JSONContent => ({ type: 'doc', content })

const para = (...content: JSONContent[]): JSONContent => ({ type: 'paragraph', content })

const heading = (level: number, ...content: JSONContent[]): JSONContent => ({
  type: 'heading',
  attrs: { level },
  content,
})

const text = (value: string, marks?: JSONContent['marks']): JSONContent =>
  marks && marks.length ? { type: 'text', text: value, marks } : { type: 'text', text: value }

/**
 * `codeBlock` is a no-mark block (`content: 'text*'`, `marks: ''` in the
 * workspace schema): its text can never carry `diffInsert`/`diffDelete`.
 */
const codeBlock = (value: string): JSONContent => ({
  type: 'codeBlock',
  content: value ? [text(value)] : [],
})

const parameterField = (code: string, display = ''): JSONContent => ({
  type: 'parameterField',
  attrs: { parameterCode: code, display },
})

const li = (...content: JSONContent[]): JSONContent => ({ type: 'listItem', content })

const cell = (...content: JSONContent[]): JSONContent => ({ type: 'tableCell', content })

const row = (...cells: JSONContent[]): JSONContent => ({ type: 'tableRow', content: cells })

const table = (...rows: JSONContent[]): JSONContent => ({ type: 'table', content: rows })

const bulletList = (...items: JSONContent[]): JSONContent => ({
  type: 'bulletList',
  content: items,
})

/** True when the node carries the given diff mark. */
function hasMark(node: JSONContent | undefined, mark: 'diffInsert' | 'diffDelete'): boolean {
  return !!node?.marks?.some((m) => m.type === mark)
}

function textNodes(node: JSONContent | undefined): JSONContent[] {
  return (node?.content ?? []).filter((n) => n.type === 'text')
}

/** Every direct child of a `codeBlock` anywhere in the document. */
function codeBlockChildren(node: JSONContent | undefined): JSONContent[] {
  const out: JSONContent[] = []
  const walk = (n: JSONContent | undefined): void => {
    if (!n) return
    if (n.type === 'codeBlock') {
      out.push(...(n.content ?? []))
      return
    }
    n.content?.forEach(walk)
  }
  walk(node)
  return out
}

/** Finds the first node of `type` anywhere in the tree. */
function findNode(node: JSONContent | undefined, type: string): JSONContent | undefined {
  if (!node) return undefined
  if (node.type === type) return node
  for (const child of node.content ?? []) {
    const found = findNode(child, type)
    if (found) return found
  }
  return undefined
}

// ── identical bodies ──────────────────────────────────────────────────────────

describe('structuralDiff — identical bodies', () => {
  it('copies an identical document through untouched', () => {
    // given
    const body = doc(para(text('Hello world')), heading(1, text('Title')))

    // when
    const result = structuralDiff(body, body)

    // then — no diff markers, no diffBlock wrapper
    expect(result).toEqual(body)
    expect(diffHasChanges(result)).toBe(false)
    expect(JSON.stringify(result)).not.toContain('diffBlock')
    expect(JSON.stringify(result)).not.toContain('diffInsert')
    expect(JSON.stringify(result)).not.toContain('diffDelete')
    expectValid(result)
  })

  it('treats structurally-equal but distinct objects as unchanged', () => {
    // given — two independently-built but identical documents
    const target = doc(para(text('Same text')), para(text('Second')))
    const compare = doc(para(text('Same text')), para(text('Second')))

    // when
    const result = structuralDiff(target, compare)

    // then
    expect(diffHasChanges(result)).toBe(false)
    expect(result.content).toHaveLength(2)
    expectValid(result)
  })
})

// ── pure insert / delete blocks ───────────────────────────────────────────────

describe('structuralDiff — block insert / delete', () => {
  it('marks a block present only in target as an insert diffBlock', () => {
    // given
    const target = doc(para(text('A')), para(text('B')))
    const compare = doc(para(text('A')))

    // when
    const result = structuralDiff(target, compare)

    // then — unchanged block passes through, new block is wrapped as insert
    expect(result.content?.[0]).toEqual(para(text('A')))
    expect(result.content?.[1]).toEqual({
      type: 'diffBlock',
      attrs: { kind: 'insert' },
      content: [para(text('B'))],
    })
    expect(diffHasChanges(result)).toBe(true)
    expectValid(result)
  })

  it('marks a block present only in compare as a delete diffBlock', () => {
    // given
    const target = doc(para(text('A')))
    const compare = doc(para(text('A')), para(text('B')))

    // when
    const result = structuralDiff(target, compare)

    // then
    expect(result.content?.[0]).toEqual(para(text('A')))
    expect(result.content?.[1]).toEqual({
      type: 'diffBlock',
      attrs: { kind: 'delete' },
      content: [para(text('B'))],
    })
    expect(diffHasChanges(result)).toBe(true)
    expectValid(result)
  })

  it('normalises a different-type replacement to delete-then-insert diffBlocks', () => {
    // given — heading replaced by a paragraph (no common block type)
    const target = doc(heading(1, text('X')))
    const compare = doc(para(text('A')))

    // when
    const result = structuralDiff(target, compare)

    // then
    expect(result.content).toEqual([
      { type: 'diffBlock', attrs: { kind: 'delete' }, content: [para(text('A'))] },
      { type: 'diffBlock', attrs: { kind: 'insert' }, content: [heading(1, text('X'))] },
    ])
    expectValid(result)
  })

  it('merges adjacent diffBlocks of the same kind', () => {
    // given — two new blocks in a row
    const target = doc(para(text('A')), para(text('New1')), para(text('New2')))
    const compare = doc(para(text('A')))

    // when
    const result = structuralDiff(target, compare)

    // then — both insertions live in a single diffBlock
    expect(result.content).toHaveLength(2)
    const block = result.content?.[1]
    expect(block?.type).toBe('diffBlock')
    expect(block?.attrs?.kind).toBe('insert')
    expect(block?.content?.map((n) => n.content?.[0]?.text)).toEqual(['New1', 'New2'])
    expectValid(result)
  })
})

// ── inline (word-level) diff ──────────────────────────────────────────────────

describe('structuralDiff — inline word-level diff', () => {
  it('marks only the changed word with diffDelete / diffInsert', () => {
    // given
    const target = doc(para(text('Hello world')))
    const compare = doc(para(text('Hello there')))

    // when
    const result = structuralDiff(target, compare)

    // then — unchanged prefix is merged and unmarked; old word deleted, new word inserted
    expect(result.content?.[0]).toEqual(
      para(
        text('Hello '),
        text('there', [{ type: 'diffDelete' }]),
        text('world', [{ type: 'diffInsert' }]),
      ),
    )
    expect(diffHasChanges(result)).toBe(true)
    expectValid(result)
  })

  it('preserves the paragraph node type and attrs of an edited block', () => {
    // given — paragraph with alignment attrs
    const target: JSONContent = {
      type: 'paragraph',
      attrs: { textAlign: 'center' },
      content: [text('New value')],
    }
    const compare: JSONContent = {
      type: 'paragraph',
      attrs: { textAlign: 'center' },
      content: [text('Old value')],
    }

    // when
    const result = structuralDiff(doc(target), doc(compare))

    // then
    const edited = result.content?.[0]
    expect(edited?.type).toBe('paragraph')
    expect(edited?.attrs?.textAlign).toBe('center')
    expect(textNodes(edited).some((n) => hasMark(n, 'diffDelete'))).toBe(true)
    expect(textNodes(edited).some((n) => hasMark(n, 'diffInsert'))).toBe(true)
    expectValid(result)
  })

  it('preserves the heading node type and level of an edited block', () => {
    // given
    const target = doc(heading(2, text('New title')))
    const compare = doc(heading(2, text('Old title')))

    // when
    const result = structuralDiff(target, compare)

    // then
    const edited = result.content?.[0]
    expect(edited?.type).toBe('heading')
    expect(edited?.attrs?.level).toBe(2)
    expect(textNodes(edited).some((n) => hasMark(n, 'diffDelete'))).toBe(true)
    expect(textNodes(edited).some((n) => hasMark(n, 'diffInsert'))).toBe(true)
    expectValid(result)
  })
})

// ── recursion into list / table containers ────────────────────────────────────

describe('structuralDiff — container recursion', () => {
  it('recurses through bulletList → listItem → paragraph to reach inline changes', () => {
    // given
    const target = doc({ type: 'bulletList', content: [li(para(text('New item')))] })
    const compare = doc({ type: 'bulletList', content: [li(para(text('Old item')))] })

    // when
    const result = structuralDiff(target, compare)

    // then — container types are preserved
    const list = result.content?.[0]
    expect(list?.type).toBe('bulletList')
    const item = list?.content?.[0]
    expect(item?.type).toBe('listItem')
    const p = item?.content?.[0]
    expect(p?.type).toBe('paragraph')

    // then — the changed words carry inline marks
    expect(textNodes(p).some((n) => n.text === 'Old' && hasMark(n, 'diffDelete'))).toBe(true)
    expect(textNodes(p).some((n) => n.text === 'New' && hasMark(n, 'diffInsert'))).toBe(true)
    expectValid(result)
  })

  it('recurses through table → tableRow → tableCell → paragraph', () => {
    // given
    const target = doc(table(row(cell(para(text('New cell'))))))
    const compare = doc(table(row(cell(para(text('Old cell'))))))

    // when
    const result = structuralDiff(target, compare)

    // then — table structure is preserved
    const t = result.content?.[0]
    expect(t?.type).toBe('table')
    const r = t?.content?.[0]
    expect(r?.type).toBe('tableRow')
    const c = r?.content?.[0]
    expect(c?.type).toBe('tableCell')
    const p = c?.content?.[0]
    expect(p?.type).toBe('paragraph')

    // then — inline marks land on the edited words
    expect(textNodes(p).some((n) => n.text === 'Old' && hasMark(n, 'diffDelete'))).toBe(true)
    expect(textNodes(p).some((n) => n.text === 'New' && hasMark(n, 'diffInsert'))).toBe(true)
    expectValid(result)
  })

  it('marks a whole row present only in target as an insert block', () => {
    // given — a new tableRow appended
    const target = doc(table(row(cell(para(text('A')))), row(cell(para(text('B'))))))
    const compare = doc(table(row(cell(para(text('A'))))))

    // when
    const result = structuralDiff(target, compare)

    // then — the table keeps its row identity; the new row's block leaves are
    // wrapped in a diffBlock *inside* the cell (a `diffBlock` cannot be a
    // sibling of `tableRow`, which the schema constrains to `tableRow+`).
    const t = result.content?.[0]
    expect(t?.type).toBe('table')
    expect(t?.content?.[0]).toEqual(row(cell(para(text('A')))))
    expect(t?.content?.[1]).toEqual(
      row(
        cell({
          type: 'diffBlock',
          attrs: { kind: 'insert' },
          content: [para(text('B'))],
        }),
      ),
    )
    expect(diffHasChanges(result)).toBe(true)
    expectValid(result)
  })

  it('marks a whole row present only in compare as a delete block', () => {
    // given — a tableRow removed from target
    const target = doc(table(row(cell(para(text('A'))))))
    const compare = doc(table(row(cell(para(text('A')))), row(cell(para(text('B'))))))

    // when
    const result = structuralDiff(target, compare)

    // then — the surviving row is untouched, the removed row keeps its identity
    const t = result.content?.[0]
    expect(t?.type).toBe('table')
    expect(t?.content?.[0]).toEqual(row(cell(para(text('A')))))
    expect(t?.content?.[1]).toEqual(
      row(
        cell({
          type: 'diffBlock',
          attrs: { kind: 'delete' },
          content: [para(text('B'))],
        }),
      ),
    )
    expect(diffHasChanges(result)).toBe(true)
    expectValid(result)
  })

  it('marks a whole listItem added to a list as an inline insert inside the item', () => {
    // given — a new listItem appended
    const target = doc(bulletList(li(para(text('A'))), li(para(text('B')))))
    const compare = doc(bulletList(li(para(text('A')))))

    // when
    const result = structuralDiff(target, compare)

    // then — the listItem keeps its identity; its inline content carries the
    // mark (a `diffBlock` cannot replace `listItem`, which the list constrains
    // to `listItem+`, and `listItem` requires a leading paragraph).
    const list = result.content?.[0]
    expect(list?.type).toBe('bulletList')
    expect(list?.content?.[0]).toEqual(li(para(text('A'))))
    expect(list?.content?.[1]).toEqual(li(para(text('B', [{ type: 'diffInsert' }]))))
    expect(diffHasChanges(result)).toBe(true)
    expectValid(result)
  })

  it('marks a whole listItem removed from a list as an inline delete inside the item', () => {
    // given — a listItem removed from target
    const target = doc(bulletList(li(para(text('A')))))
    const compare = doc(bulletList(li(para(text('A'))), li(para(text('B')))))

    // when
    const result = structuralDiff(target, compare)

    // then
    const list = result.content?.[0]
    expect(list?.type).toBe('bulletList')
    expect(list?.content?.[0]).toEqual(li(para(text('A'))))
    expect(list?.content?.[1]).toEqual(li(para(text('B', [{ type: 'diffDelete' }]))))
    expect(diffHasChanges(result)).toBe(true)
    expectValid(result)
  })

  it('recurses into a nested list and marks an added nested listItem', () => {
    // given — the outer list stays; a nested listItem is added inside the item
    const target = doc(
      bulletList(
        li(
          para(text('A')),
          bulletList(li(para(text('x'))), li(para(text('y')))),
        ),
      ),
    )
    const compare = doc(
      bulletList(
        li(
          para(text('A')),
          bulletList(li(para(text('x')))),
        ),
      ),
    )

    // when
    const result = structuralDiff(target, compare)

    // then — nesting is preserved and the added nested item is marked inline
    const outer = result.content?.[0]
    const outerItem = outer?.content?.[0]
    const nested = outerItem?.content?.[1]
    expect(nested?.type).toBe('bulletList')
    expect(nested?.content?.[0]).toEqual(li(para(text('x'))))
    expect(nested?.content?.[1]).toEqual(li(para(text('y', [{ type: 'diffInsert' }]))))
    expect(diffHasChanges(result)).toBe(true)
    expectValid(result)
  })
})

// ── no-mark blocks (codeBlock) ────────────────────────────────────────────────
//
// `codeBlock` forbids marks (`marks: ''`), so inlining a word diff would emit
// `text` nodes carrying `diffInsert`/`diffDelete` and produce an invalid doc
// (`Invalid content for node codeBlock`). The differ therefore treats a
// `codeBlock` opaquely and wraps the whole block in a `diffBlock`.

describe('structuralDiff — codeBlock (no-mark block)', () => {
  it('replaces a modified codeBlock with delete-then-insert diffBlocks and no marks', () => {
    // given — same position, different code
    const target = doc(codeBlock('const x = 2'))
    const compare = doc(codeBlock('const x = 1'))

    // when
    const result = structuralDiff(target, compare)

    // then — old code deleted, new code inserted, both opaque (no marks)
    expect(result.content).toEqual([
      { type: 'diffBlock', attrs: { kind: 'delete' }, content: [codeBlock('const x = 1')] },
      { type: 'diffBlock', attrs: { kind: 'insert' }, content: [codeBlock('const x = 2')] },
    ])
    expect(result.content?.map((n) => n.attrs?.kind)).toEqual(['delete', 'insert'])
    expect(codeBlockChildren(result).length).toBeGreaterThan(0)
    expect(codeBlockChildren(result).every((n) => !n.marks?.length)).toBe(true)
    expect(diffHasChanges(result)).toBe(true)
    expectValid(result)
  })

  it('marks an added listItem containing a codeBlock, wrapping the nested codeBlock as insert', () => {
    // given — a listItem (paragraph + codeBlock) appended to the list
    const target = doc(
      bulletList(
        li(para(text('A')), codeBlock('kept')),
        li(para(text('B')), codeBlock('added')),
      ),
    )
    const compare = doc(bulletList(li(para(text('A')), codeBlock('kept'))))

    // when
    const result = structuralDiff(target, compare)

    // then — the listItem keeps its identity; its paragraph is marked inline
    // while the nested codeBlock is wrapped in a diffBlock (valid after the
    // leading paragraph, since a diffBlock cannot carry marks itself).
    const list = result.content?.[0]
    expect(list?.type).toBe('bulletList')
    expect(list?.content?.[0]).toEqual(li(para(text('A')), codeBlock('kept')))

    const inserted = list?.content?.[1]
    expect(inserted?.type).toBe('listItem')
    expect(inserted?.content?.[0]).toEqual(para(text('B', [{ type: 'diffInsert' }])))
    expect(inserted?.content?.[1]).toEqual({
      type: 'diffBlock',
      attrs: { kind: 'insert' },
      content: [codeBlock('added')],
    })
    expect(codeBlockChildren(result).every((n) => !n.marks?.length)).toBe(true)
    expect(diffHasChanges(result)).toBe(true)
    expectValid(result)
  })

  it('marks a removed listItem containing a codeBlock, wrapping the nested codeBlock as delete', () => {
    // given — a listItem (paragraph + codeBlock) dropped from compare
    const target = doc(bulletList(li(para(text('A')), codeBlock('kept'))))
    const compare = doc(
      bulletList(
        li(para(text('A')), codeBlock('kept')),
        li(para(text('B')), codeBlock('removed')),
      ),
    )

    // when
    const result = structuralDiff(target, compare)

    // then — symmetric to the insert case
    const list = result.content?.[0]
    expect(list?.type).toBe('bulletList')
    expect(list?.content?.[0]).toEqual(li(para(text('A')), codeBlock('kept')))

    const removed = list?.content?.[1]
    expect(removed?.type).toBe('listItem')
    expect(removed?.content?.[0]).toEqual(para(text('B', [{ type: 'diffDelete' }])))
    expect(removed?.content?.[1]).toEqual({
      type: 'diffBlock',
      attrs: { kind: 'delete' },
      content: [codeBlock('removed')],
    })
    expect(codeBlockChildren(result).every((n) => !n.marks?.length)).toBe(true)
    expect(diffHasChanges(result)).toBe(true)
    expectValid(result)
  })

  it('passes an unchanged codeBlock through untouched', () => {
    // given — identical documents containing a codeBlock
    const body = doc(para(text('Intro')), codeBlock('const x = 1'))

    // when
    const result = structuralDiff(body, body)

    // then — copied through verbatim, no diffBlock and no diff marks
    expect(result).toEqual(body)
    expect(findNode(result, 'diffBlock')).toBeUndefined()
    expect(codeBlockChildren(result).every((n) => !n.marks?.length)).toBe(true)
    expect(diffHasChanges(result)).toBe(false)
    expectValid(result)
  })
})

// ── atom nodes ────────────────────────────────────────────────────────────────

describe('structuralDiff — atom nodes', () => {
  it('treats an added parameterField as an opaque inline insert', () => {
    // given — target paragraph gains a parameterField
    const target = doc(para(text('New '), parameterField('bank', 'Банк')))
    const compare = doc(para(text('Old ')))

    // when
    const result = structuralDiff(target, compare)

    // then
    const content = result.content?.[0]?.content ?? []
    const atom = content.find((n) => n.type === 'parameterField')
    expect(atom).toBeDefined()
    expect(atom?.attrs?.parameterCode).toBe('bank')
    expect(hasMark(atom, 'diffInsert')).toBe(true)
    expect(hasMark(atom, 'diffDelete')).toBe(false)
    expectValid(result)
  })

  it('treats a removed parameterField as an opaque inline delete', () => {
    // given — compare paragraph has a parameterField that target dropped
    const target = doc(para(text('New ')))
    const compare = doc(para(text('Old '), parameterField('bank', 'Банк')))

    // when
    const result = structuralDiff(target, compare)

    // then
    const content = result.content?.[0]?.content ?? []
    const atom = content.find((n) => n.type === 'parameterField')
    expect(atom?.attrs?.parameterCode).toBe('bank')
    expect(hasMark(atom, 'diffDelete')).toBe(true)
    expect(hasMark(atom, 'diffInsert')).toBe(false)
    expectValid(result)
  })

  it('leaves an unchanged parameterField unmarked', () => {
    // given — same atom on both sides, only surrounding text changes
    const target = doc(para(text('New '), parameterField('bank', 'Банк')))
    const compare = doc(para(text('Old '), parameterField('bank', 'Банк')))

    // when
    const result = structuralDiff(target, compare)

    // then — atom is opaque and carried through without a diff mark
    const atom = (result.content?.[0]?.content ?? []).find((n) => n.type === 'parameterField')
    expect(atom?.marks).toBeUndefined()
    expectValid(result)
  })

  it('ignores volatile render-time atom attrs when matching', () => {
    // given — same atom, differing volatile `value` / `_resolvedLabel` attrs
    const target = doc(
      para(text('New '), { type: 'parameterField', attrs: { parameterCode: 'bank', value: 'b' } }),
    )
    const compare = doc(
      para(text('Old '), {
        type: 'parameterField',
        attrs: { parameterCode: 'bank', value: 'a', _resolvedLabel: 'x' },
      }),
    )

    // when
    const result = structuralDiff(target, compare)

    // then — still matched as the same atom
    const atom = (result.content?.[0]?.content ?? []).find((n) => n.type === 'parameterField')
    expect(atom?.marks).toBeUndefined()
    expectValid(result)
  })

  // Review #6 — a paragraph whose visible text is identical but whose embedded
  // atom changed must still be reported as changed. Without `descendantAtomKeys`
  // the block signature (type + concatenated text) is equal on both sides, the
  // paragraph is copied through unchanged, and the change is silently lost.
  it('detects a parameterField whose code changed inside otherwise-unchanged text', () => {
    // given — same surrounding text, different atom parameter code
    const target = doc(para(text('Hello '), parameterField('bank', 'Банк')))
    const compare = doc(para(text('Hello '), parameterField('old_bank', 'Банк')))

    // when
    const result = structuralDiff(target, compare)

    // then — the paragraph is diffed rather than skipped as unchanged
    expect(diffHasChanges(result)).toBe(true)
    const edited = result.content?.[0]
    expect(edited?.type).toBe('paragraph')

    const content = edited?.content ?? []
    const inserted = content.find(
      (n) => n.type === 'parameterField' && n.attrs?.parameterCode === 'bank',
    )
    const deleted = content.find(
      (n) => n.type === 'parameterField' && n.attrs?.parameterCode === 'old_bank',
    )
    expect(inserted).toBeDefined()
    expect(deleted).toBeDefined()
    expect(hasMark(inserted, 'diffInsert')).toBe(true)
    expect(hasMark(deleted, 'diffDelete')).toBe(true)
    expectValid(result)
  })
})

// ── empty / missing bodies ────────────────────────────────────────────────────

describe('structuralDiff — empty and missing bodies', () => {
  it('returns an empty doc for two empty/missing bodies', () => {
    expect(structuralDiff(null, null)).toEqual({ type: 'doc', content: [] })
    expect(structuralDiff(undefined, undefined)).toEqual({ type: 'doc', content: [] })
    expect(structuralDiff({ type: 'doc', content: [] }, { type: 'doc', content: [] })).toEqual({
      type: 'doc',
      content: [],
    })
  })

  it('wraps every target block as insert when compare is missing', () => {
    // given
    const target = doc(para(text('Only')))

    // when
    const result = structuralDiff(target, null)

    // then
    expect(result.content).toEqual([
      { type: 'diffBlock', attrs: { kind: 'insert' }, content: [para(text('Only'))] },
    ])
    expect(diffHasChanges(result)).toBe(true)
    expectValid(result)
  })

  it('wraps every compare block as delete when target is missing', () => {
    // given
    const compare = doc(para(text('Gone')))

    // when
    const result = structuralDiff(null, compare)

    // then
    expect(result.content).toEqual([
      { type: 'diffBlock', attrs: { kind: 'delete' }, content: [para(text('Gone'))] },
    ])
    expect(diffHasChanges(result)).toBe(true)
    expectValid(result)
  })
})

// ── diffHasChanges ────────────────────────────────────────────────────────────

describe('diffHasChanges', () => {
  it('detects a diffBlock', () => {
    expect(
      diffHasChanges({
        type: 'doc',
        content: [{ type: 'diffBlock', attrs: { kind: 'insert' }, content: [] }],
      }),
    ).toBe(true)
  })

  it('detects an inline diff mark nested deep in the tree', () => {
    expect(
      diffHasChanges(
        doc(para(text('nested', [{ type: 'diffInsert' }]))),
      ),
    ).toBe(true)
  })

  it('returns false for a clean document and for null', () => {
    expect(diffHasChanges(doc(para(text('clean'))))).toBe(false)
    expect(diffHasChanges(null)).toBe(false)
    expect(diffHasChanges(undefined)).toBe(false)
  })
})
