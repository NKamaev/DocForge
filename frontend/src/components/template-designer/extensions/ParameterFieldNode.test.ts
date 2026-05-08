import { describe, it, expect } from 'vitest'
import { formatValue, renderPreview } from '../../../utils/previewRenderer'
import type { Parameter } from '../../../types/template'
import type { JSONContent } from '@tiptap/core'

const TEXT_PARAM: Parameter = {
  code: 'name',
  label: 'Имя',
  type: 'TEXT',
  required: true,
  defaultValue: null,
  options: null,
  validation: null,
  directoryId: null,
  directoryFields: null,
  format: null,
}

const BOOL_PARAM: Parameter = {
  code: 'has_penalty',
  label: 'Неустойка',
  type: 'BOOLEAN',
  required: false,
  defaultValue: null,
  options: null,
  validation: null,
  directoryId: null,
  directoryFields: null,
  format: null,
}

const DATE_PARAM: Parameter = {
  code: 'contract_date',
  label: 'Дата',
  type: 'DATE',
  required: true,
  defaultValue: null,
  options: null,
  validation: null,
  directoryId: null,
  directoryFields: null,
  format: { dateFormat: 'DOTS' },
}

// ── formatValue ───────────────────────────────────────────────────────────────

describe('formatValue — ParameterFieldNode logic', () => {
  it('returns empty string for null answer', () => {
    expect(formatValue('name', null, [TEXT_PARAM])).toBe('')
  })

  it('returns empty string for undefined answer', () => {
    expect(formatValue('name', undefined, [TEXT_PARAM])).toBe('')
  })

  it('returns string value for TEXT param', () => {
    expect(formatValue('name', 'Иванов Иван', [TEXT_PARAM])).toBe('Иванов Иван')
  })

  it('returns "Да" for true BOOLEAN param', () => {
    expect(formatValue('has_penalty', true, [BOOL_PARAM])).toBe('Да')
  })

  it('returns "Нет" for false BOOLEAN param', () => {
    expect(formatValue('has_penalty', false, [BOOL_PARAM])).toBe('Нет')
  })

  it('formats DATE param with DOTS format', () => {
    expect(formatValue('contract_date', '2026-01-15', [DATE_PARAM])).toBe('15.01.2026')
  })

  it('returns raw value for unknown param code', () => {
    expect(formatValue('unknown', 'some value', [])).toBe('some value')
  })
})

// ── renderPreview — parameterField node injection ─────────────────────────────

describe('renderPreview — parameterField node', () => {
  const makeDoc = (paramCode: string): JSONContent => ({
    type: 'doc',
    content: [
      {
        type: 'paragraph',
        content: [
          { type: 'text', text: 'Клиент: ' },
          {
            type: 'parameterField',
            attrs: { parameterCode: paramCode, display: 'Имя', format: null },
          },
        ],
      },
    ],
  })

  it('injects formatted value into parameterField node attrs', () => {
    const doc = makeDoc('name')
    const result = renderPreview(doc as Record<string, unknown>, { name: 'Петров' }, [TEXT_PARAM])
    const para = result.content?.[0]
    const field = para?.content?.[1]
    expect(field?.type).toBe('parameterField')
    expect(field?.attrs?.value).toBe('Петров')
  })

  it('injects empty string when answer is missing', () => {
    const doc = makeDoc('name')
    const result = renderPreview(doc as Record<string, unknown>, {}, [TEXT_PARAM])
    const field = result.content?.[0]?.content?.[1]
    expect(field?.attrs?.value).toBe('')
  })

  it('preserves other paragraph content around parameterField', () => {
    const doc = makeDoc('name')
    const result = renderPreview(doc as Record<string, unknown>, { name: 'Козлов' }, [TEXT_PARAM])
    const para = result.content?.[0]
    expect(para?.content?.[0]).toMatchObject({ type: 'text', text: 'Клиент: ' })
  })
})
