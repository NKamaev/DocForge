import { describe, it, expect } from 'vitest'
import { evaluateSimple } from '../../utils/previewRenderer'
import { renderPreview } from '../../utils/previewRenderer'
import type { JSONContent } from '@tiptap/core'

// ── evaluateSimple unit tests ─────────────────────────────────────────────────

describe('evaluateSimple', () => {
  it('returns true for empty expression', () => {
    expect(evaluateSimple('', {})).toBe(true)
  })

  it('returns true when condition matches', () => {
    expect(evaluateSimple('clientType == ЮЛ', { clientType: 'ЮЛ' })).toBe(true)
  })

  it('returns false when answer does not match', () => {
    expect(evaluateSimple('clientType == ЮЛ', { clientType: 'ИП' })).toBe(false)
  })

  it('returns false when answer is absent', () => {
    expect(evaluateSimple('clientType == ЮЛ', {})).toBe(false)
  })
})

// ── renderPreview with preserveConditionalBlocks ──────────────────────────────

const makeDoc = (condition: string, children: JSONContent[]): JSONContent => ({
  type: 'doc',
  content: [
    {
      type: 'conditionalBlock',
      attrs: { condition: { type: 'simple', expression: condition }, label: 'Блок', collapsed: false },
      content: children,
    },
  ],
})

const para = (text: string): JSONContent => ({
  type: 'paragraph',
  content: [{ type: 'text', text }],
})

describe('renderPreview preserveConditionalBlocks', () => {
  it('preserves conditionalBlock node with matching condition', () => {
    const doc = makeDoc('clientType == ЮЛ', [para('Текст ЮЛ')])
    const result = renderPreview(doc as Record<string, unknown>, { clientType: 'ЮЛ' }, [], {
      preserveConditionalBlocks: true,
    })
    const block = result.content?.[0]
    expect(block?.type).toBe('conditionalBlock')
    expect(block?.attrs?.condition?.expression).toBe('clientType == ЮЛ')
  })

  it('preserves conditionalBlock node even when condition does not match', () => {
    const doc = makeDoc('clientType == ЮЛ', [para('Текст ЮЛ')])
    const result = renderPreview(doc as Record<string, unknown>, { clientType: 'ИП' }, [], {
      preserveConditionalBlocks: true,
    })
    const block = result.content?.[0]
    // Node is preserved — visibility is handled by WorkspaceConditionalBlockNode
    expect(block?.type).toBe('conditionalBlock')
  })

  it('preserves conditionalBlock when answers are empty', () => {
    const doc = makeDoc('clientType == ЮЛ', [para('Текст ЮЛ')])
    const result = renderPreview(doc as Record<string, unknown>, {}, [], {
      preserveConditionalBlocks: true,
    })
    expect(result.content?.[0]?.type).toBe('conditionalBlock')
  })
})

describe('renderPreview default (showAllConditionals = false)', () => {
  it('removes conditionalBlock when condition does not match', () => {
    const doc = makeDoc('clientType == ЮЛ', [para('Текст ЮЛ')])
    const result = renderPreview(doc as Record<string, unknown>, { clientType: 'ИП' }, [])
    expect(result.content ?? []).toHaveLength(0)
  })

  it('expands conditionalBlock to children when condition matches', () => {
    const doc = makeDoc('clientType == ЮЛ', [para('Текст ЮЛ')])
    const result = renderPreview(doc as Record<string, unknown>, { clientType: 'ЮЛ' }, [])
    expect(result.content?.[0]?.type).toBe('paragraph')
  })
})
