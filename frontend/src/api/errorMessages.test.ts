import { describe, it, expect } from 'vitest'
import {
  extractServerMessage,
  getErrorStatus,
  isForbiddenError,
  isNotFoundError,
} from './errorMessages'

function axiosError(status: number, data?: unknown) {
  return { isAxiosError: true, response: { status, data } }
}

describe('getErrorStatus', () => {
  it('reads the HTTP status from an axios-like error', () => {
    expect(getErrorStatus(axiosError(403, { detail: 'Нет доступа' }))).toBe(403)
    expect(getErrorStatus(axiosError(404))).toBe(404)
  })

  it('returns null when there is no response (network error)', () => {
    expect(getErrorStatus({ isAxiosError: true, message: 'Network Error' })).toBeNull()
  })

  it('returns null for non-objects and empty values', () => {
    expect(getErrorStatus(null)).toBeNull()
    expect(getErrorStatus(undefined)).toBeNull()
    expect(getErrorStatus('boom')).toBeNull()
    expect(getErrorStatus(new Error('boom'))).toBeNull()
  })

  it('returns null when the status is not a number', () => {
    expect(getErrorStatus({ response: { status: '403' } })).toBeNull()
  })
})

describe('extractServerMessage', () => {
  it('reads RFC-7807 ProblemDetail.detail', () => {
    expect(extractServerMessage(axiosError(403, { detail: 'Нет доступа к шаблону' }))).toBe(
      'Нет доступа к шаблону',
    )
  })

  it('falls back to response.data.message', () => {
    expect(extractServerMessage(axiosError(403, { message: 'Нет доступа к версии шаблона' }))).toBe(
      'Нет доступа к версии шаблона',
    )
  })

  it('prefers detail over message', () => {
    expect(
      extractServerMessage(axiosError(400, { detail: 'detail wins', message: 'message loses' })),
    ).toBe('detail wins')
  })

  it('falls back to message when detail is blank', () => {
    expect(extractServerMessage(axiosError(400, { detail: '   ', message: 'message wins' }))).toBe(
      'message wins',
    )
  })

  it('returns null for an axios-like error without a response', () => {
    expect(extractServerMessage({ isAxiosError: true, message: 'Network Error' })).toBeNull()
  })

  it('returns null for errors without a usable payload', () => {
    expect(extractServerMessage(null)).toBeNull()
    expect(extractServerMessage(undefined)).toBeNull()
    expect(extractServerMessage(new Error('boom'))).toBeNull()
    expect(extractServerMessage({ response: { data: null } })).toBeNull()
    expect(extractServerMessage({ response: {} })).toBeNull()
    expect(extractServerMessage({ response: { data: { detail: 42 } } })).toBeNull()
  })
})

describe('isForbiddenError / isNotFoundError', () => {
  it('detects 403 and 404 responses', () => {
    expect(isForbiddenError(axiosError(403))).toBe(true)
    expect(isNotFoundError(axiosError(404))).toBe(true)
  })

  it('does not confuse other statuses with 403/404', () => {
    expect(isForbiddenError(axiosError(500))).toBe(false)
    expect(isNotFoundError(axiosError(403))).toBe(false)
    expect(isForbiddenError(new Error('boom'))).toBe(false)
  })
})
