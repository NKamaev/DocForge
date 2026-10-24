import { render, screen, waitFor } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import VersionHistoryPanel from './VersionHistoryPanel'
import { templatesApi } from '../../api/templatesApi'
import type { TemplateVersionDTO } from '../../types/template'

vi.mock('../../api/templatesApi', () => ({
  templatesApi: {
    getVersions: vi.fn(),
  },
}))

function version(
  bumpType: TemplateVersionDTO['bumpType'],
  versionNumber: number,
): TemplateVersionDTO {
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

describe('VersionHistoryPanel', () => {
  beforeEach(() => {
    vi.mocked(templatesApi.getVersions).mockReset()
  })

  it('offers rollback to the template owner', async () => {
    vi.mocked(templatesApi.getVersions).mockResolvedValue([
      version('MINOR', 2),
      version('PUBLISH', 1),
    ])

    render(
      <VersionHistoryPanel templateId="t-1" onRollback={vi.fn()} canRollback={true} />,
    )

    await waitFor(() => expect(screen.getByText('v1.1')).toBeInTheDocument())
    expect(screen.getByRole('button', { name: /Откатить/ })).toBeInTheDocument()
    // У владельца самая свежая версия — текущая, её откатывать нельзя
    expect(screen.getAllByRole('button', { name: /Откатить/ })).toHaveLength(1)
    expect(screen.getByText('Текущая')).toBeInTheDocument()
  })

  it('does not offer rollback to a non-owner and explains why', async () => {
    vi.mocked(templatesApi.getVersions).mockResolvedValue([
      version('PUBLISH', 1),
    ])

    render(
      <VersionHistoryPanel templateId="t-1" onRollback={vi.fn()} canRollback={false} />,
    )

    await waitFor(() => expect(screen.getByText('v1.1')).toBeInTheDocument())
    expect(screen.queryByRole('button', { name: /Откатить/ })).toBeNull()
    expect(screen.queryByText('Текущая')).toBeNull()
    expect(screen.getByText(/Показаны только опубликованные версии/)).toBeInTheDocument()
  })

  it('shows an access error instead of a generic failure on 403', async () => {
    vi.mocked(templatesApi.getVersions).mockRejectedValue({
      response: { status: 403, data: { detail: 'Нет доступа к версии шаблона' } },
    })

    render(
      <VersionHistoryPanel templateId="t-1" onRollback={vi.fn()} canRollback={false} />,
    )

    await waitFor(() =>
      expect(screen.getByText('Нет доступа к истории версий шаблона')).toBeInTheDocument(),
    )
  })

  it('shows the server message for other failures', async () => {
    vi.mocked(templatesApi.getVersions).mockRejectedValue({
      response: { status: 500, data: { detail: 'Внутренняя ошибка сервера' } },
    })

    render(
      <VersionHistoryPanel templateId="t-1" onRollback={vi.fn()} canRollback={false} />,
    )

    await waitFor(() =>
      expect(screen.getByText('Внутренняя ошибка сервера')).toBeInTheDocument(),
    )
  })
})
