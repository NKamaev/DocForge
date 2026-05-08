import { describe, it, expect } from 'vitest'
import { renderPreview } from '../../../utils/previewRenderer'
import type { JSONContent } from '@tiptap/core'

// ── Helpers ───────────────────────────────────────────────────────────────────

function clause(id: string, level: number, text: string): JSONContent {
  return {
    type: 'numberedClause',
    attrs: { id, level, numberingStyle: 'decimal', listStart: false, condition: null },
    content: [{ type: 'paragraph', content: [{ type: 'text', text }] }],
  }
}

function clauseRef(refId: string): JSONContent {
  return {
    type: 'clauseRef',
    attrs: { refId },
  }
}

// ── renderPreview — clauseRef label resolution ────────────────────────────────

describe('renderPreview — clauseRef resolves clause number', () => {
  it('resolves first clause to label "1."', () => {
    const doc: JSONContent = {
      type: 'doc',
      content: [
        clause('clause-1', 1, 'Первый пункт'),
        {
          type: 'paragraph',
          content: [clauseRef('clause-1')],
        },
      ],
    }
    const result = renderPreview(doc as Record<string, unknown>, {}, [])
    const ref = result.content?.[1]?.content?.[0]
    expect(ref?.type).toBe('clauseRef')
    expect(ref?.attrs?._resolvedLabel).toBe('1.')
  })

  it('resolves second clause to label "2."', () => {
    const doc: JSONContent = {
      type: 'doc',
      content: [
        clause('c1', 1, 'Первый'),
        clause('c2', 1, 'Второй'),
        {
          type: 'paragraph',
          content: [clauseRef('c2')],
        },
      ],
    }
    const result = renderPreview(doc as Record<string, unknown>, {}, [])
    const ref = result.content?.[2]?.content?.[0]
    expect(ref?.attrs?._resolvedLabel).toBe('2.')
  })

  it('resolves nested clause to hierarchical label "1.1."', () => {
    const doc: JSONContent = {
      type: 'doc',
      content: [
        {
          type: 'numberedClause',
          attrs: { id: 'p1', level: 1, numberingStyle: 'decimal', listStart: false, condition: null },
          content: [
            {
              type: 'paragraph',
              content: [{ type: 'text', text: 'Родительский' }],
            },
            {
              type: 'numberedClause',
              attrs: { id: 'p1-1', level: 2, numberingStyle: 'decimal', listStart: false, condition: null },
              content: [{ type: 'paragraph', content: [{ type: 'text', text: 'Вложенный' }] }],
            },
          ],
        },
        {
          type: 'paragraph',
          content: [clauseRef('p1-1')],
        },
      ],
    }
    const result = renderPreview(doc as Record<string, unknown>, {}, [])
    const ref = result.content?.[1]?.content?.[0]
    expect(ref?.attrs?._resolvedLabel).toBe('1.1.')
  })

  it('shows "???" for unknown ref id', () => {
    const doc: JSONContent = {
      type: 'doc',
      content: [
        clause('real-id', 1, 'Пункт'),
        {
          type: 'paragraph',
          content: [clauseRef('non-existent-id')],
        },
      ],
    }
    const result = renderPreview(doc as Record<string, unknown>, {}, [])
    const ref = result.content?.[1]?.content?.[0]
    expect(ref?.attrs?._resolvedLabel).toBe('???')
  })
})
