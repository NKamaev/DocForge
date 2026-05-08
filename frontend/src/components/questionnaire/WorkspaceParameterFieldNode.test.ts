import { describe, it, expect } from 'vitest'
import { formatValue } from '../../utils/previewRenderer'
import type { Parameter } from '../../types/template'

// Tests for the display logic used by WorkspaceParameterFieldNode.
// The actual React node view subscribes to editor storage; here we verify
// the underlying formatValue helper that drives what gets displayed.

const PARAMS: Parameter[] = [
  {
    code: 'client_name',
    label: 'ФИО клиента',
    type: 'FIO',
    required: true,
    defaultValue: null,
    options: null,
    validation: null,
    directoryId: null,
    directoryFields: null,
    format: null,
  },
  {
    code: 'amount',
    label: 'Сумма',
    type: 'NUMBER',
    required: true,
    defaultValue: null,
    options: null,
    validation: null,
    directoryId: null,
    directoryFields: null,
    format: { numberFormat: 'WITH_UNIT', unit: 'рубль|рубля|рублей' },
  },
  {
    code: 'agreed',
    label: 'Согласовано',
    type: 'BOOLEAN',
    required: false,
    defaultValue: null,
    options: null,
    validation: null,
    directoryId: null,
    directoryFields: null,
    format: null,
  },
]

describe('WorkspaceParameterFieldNode — display logic (formatValue)', () => {
  it('shows empty string when answer is absent', () => {
    expect(formatValue('client_name', undefined, PARAMS)).toBe('')
  })

  it('shows formatted number with unit', () => {
    const result = formatValue('amount', 5, PARAMS)
    expect(result).toContain('рублей')
  })

  it('shows "Да" for boolean true', () => {
    expect(formatValue('agreed', true, PARAMS)).toBe('Да')
  })

  it('shows "Нет" for boolean false', () => {
    expect(formatValue('agreed', false, PARAMS)).toBe('Нет')
  })

  it('shows raw FIO string when no format override', () => {
    const result = formatValue('client_name', 'Иванов Иван Иванович', PARAMS)
    expect(result).toBeTruthy()
    expect(typeof result).toBe('string')
  })

  it('format override supersedes param-level format', () => {
    const override = { numberFormat: 'PERCENTAGE' as const }
    const result = formatValue('amount', 42, PARAMS, override)
    expect(result).toContain('%')
  })
})
