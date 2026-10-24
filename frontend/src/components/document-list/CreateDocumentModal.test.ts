import { describe, it, expect } from 'vitest'
import {
  isInstantiableVersion,
  isDraftVersion,
  analyzeTemplateVersions,
  getVersionAvailabilityHint,
  getDraftVersionsNotice,
  extractServerMessage,
} from './CreateDocumentModal'
import type { TemplateVersionDTO } from '../../types/template'

// ── helpers ────────────────────────────────────────────────────────────────────

function version(bumpType: TemplateVersionDTO['bumpType'], versionNumber = 1): TemplateVersionDTO {
  return {
    id: `v-${versionNumber}`,
    templateId: 't-1',
    versionNumber,
    majorVersion: 1,
    minorVersion: versionNumber,
    createdAt: '2026-01-01T00:00:00Z',
    createdBy: 'u-1',
    comment: null,
    bumpType,
    autoDescription: null,
    rollbackFrom: null,
  }
}

// ── tests ──────────────────────────────────────────────────────────────────────

describe('isInstantiableVersion', () => {
  it('allows published versions', () => {
    expect(isInstantiableVersion(version('PUBLISH'))).toBe(true)
  })

  it('allows legacy versions without bumpType (pre-bump_type publications)', () => {
    expect(isInstantiableVersion(version(null))).toBe(true)
  })

  it('rejects drafts created by saveVersion', () => {
    expect(isInstantiableVersion(version('MAJOR'))).toBe(false)
    expect(isInstantiableVersion(version('MINOR'))).toBe(false)
  })

  it('rejects rollbacks — publishedness of the rollback source is server-side only', () => {
    expect(isInstantiableVersion(version('ROLLBACK'))).toBe(false)
  })
})

describe('isDraftVersion', () => {
  it('treats MAJOR/MINOR versions as drafts', () => {
    expect(isDraftVersion(version('MAJOR'))).toBe(true)
    expect(isDraftVersion(version('MINOR'))).toBe(true)
  })

  it('does not treat published or legacy versions as drafts', () => {
    expect(isDraftVersion(version('PUBLISH'))).toBe(false)
    expect(isDraftVersion(version(null))).toBe(false)
  })

  it('does not treat ROLLBACK versions as drafts', () => {
    expect(isDraftVersion(version('ROLLBACK'))).toBe(false)
  })
})

describe('analyzeTemplateVersions', () => {
  it('splits instantiable versions from drafts', () => {
    const analyzed = analyzeTemplateVersions([
      version('PUBLISH', 1),
      version('MAJOR', 2),
      version('ROLLBACK', 3),
    ])

    expect(analyzed.instantiable.map(v => v.versionNumber)).toEqual([1])
    expect(analyzed.hasDrafts).toBe(true)
    expect(analyzed.hasVersionRows).toBe(true)
  })

  it('reports no version rows for an empty list', () => {
    const analyzed = analyzeTemplateVersions([])

    expect(analyzed.instantiable).toEqual([])
    expect(analyzed.hasDrafts).toBe(false)
    expect(analyzed.hasVersionRows).toBe(false)
  })

  it('does not count ROLLBACK versions as drafts', () => {
    const analyzed = analyzeTemplateVersions([version('ROLLBACK', 1)])

    expect(analyzed.hasDrafts).toBe(false)
    expect(analyzed.hasVersionRows).toBe(true)
  })
})

describe('getVersionAvailabilityHint', () => {
  it('has no hint when there is a version to pick', () => {
    const analyzed = analyzeTemplateVersions([version('PUBLISH', 1)])
    expect(getVersionAvailabilityHint(analyzed)).toBeNull()
  })

  it('warns that creation is impossible when version rows exist but none is published', () => {
    const analyzed = analyzeTemplateVersions([version('MAJOR', 1), version('ROLLBACK', 2)])
    expect(getVersionAvailabilityHint(analyzed)).toBe(
      'У шаблона нет опубликованных версий — создать документ нельзя',
    )
  })

  it('falls back to the live template when the template has no version rows at all', () => {
    const analyzed = analyzeTemplateVersions([])
    expect(getVersionAvailabilityHint(analyzed)).toBe(
      'Нет опубликованных версий. Будет использована текущая версия шаблона.',
    )
  })
})

describe('getDraftVersionsNotice', () => {
  it('mentions that drafts are available to the template author only', () => {
    const analyzed = analyzeTemplateVersions([version('PUBLISH', 1), version('MINOR', 2)])
    expect(getDraftVersionsNotice(analyzed)).toBe(
      'Черновики версий доступны только автору шаблона.',
    )
  })

  it('is absent when there are no drafts', () => {
    expect(getDraftVersionsNotice(analyzeTemplateVersions([version('PUBLISH', 1)]))).toBeNull()
    expect(getDraftVersionsNotice(analyzeTemplateVersions([version('ROLLBACK', 1)]))).toBeNull()
    expect(getDraftVersionsNotice(analyzeTemplateVersions([]))).toBeNull()
  })
})

describe('extractServerMessage', () => {
  it('reads RFC-7807 ProblemDetail.detail', () => {
    const error = { response: { data: { detail: 'Нет опубликованной версии шаблона' } } }
    expect(extractServerMessage(error)).toBe('Нет опубликованной версии шаблона')
  })

  it('falls back to response.data.message', () => {
    const error = { response: { data: { message: 'Нет доступа к черновой версии шаблона' } } }
    expect(extractServerMessage(error)).toBe('Нет доступа к черновой версии шаблона')
  })

  it('prefers detail over message', () => {
    const error = {
      response: { data: { detail: 'detail wins', message: 'message loses' } },
    }
    expect(extractServerMessage(error)).toBe('detail wins')
  })

  it('falls back to message when detail is blank', () => {
    const error = { response: { data: { detail: '   ', message: 'message wins' } } }
    expect(extractServerMessage(error)).toBe('message wins')
  })

  it('returns null for an axios-like error without a response', () => {
    const error = { isAxiosError: true, message: 'Network Error', toJSON: () => ({}) }
    expect(extractServerMessage(error)).toBeNull()
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
