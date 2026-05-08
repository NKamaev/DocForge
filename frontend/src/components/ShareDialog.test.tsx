import { render, screen, waitFor } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import ShareDialog from './ShareDialog'
import type { FolderMember } from '../types/member'

vi.mock('../api/membersApi', () => ({
  membersApi: {
    getFolderMembers: vi.fn(),
    getDocumentMembers: vi.fn(),
    updateFolderRole: vi.fn(),
    revokeFolderMember: vi.fn(),
    grantFolderRole: vi.fn(),
  },
}))

vi.mock('../api/usersApi', () => ({
  usersApi: {
    searchByLogin: vi.fn(),
  },
}))

import { membersApi } from '../api/membersApi'

const members: FolderMember[] = [
  {
    userId: 'user-1',
    userLogin: 'alice',
    userDisplayName: 'Alice Smith',
    role: 'OWNER',
    grantedAt: '2024-01-01T00:00:00Z',
  },
  {
    userId: 'user-2',
    userLogin: 'bob',
    userDisplayName: null,
    role: 'EDITOR',
    grantedAt: '2024-01-02T00:00:00Z',
  },
]

describe('ShareDialog', () => {
  beforeEach(() => {
    vi.mocked(membersApi.getFolderMembers).mockResolvedValue(members)
  })

  it('renders members list after load', async () => {
    render(
      <ShareDialog
        open={true}
        onClose={vi.fn()}
        resourceType="folder"
        resourceId="folder-1"
        resourceName="Тестовая папка"
        myRole="OWNER"
      />
    )

    await waitFor(() => {
      expect(screen.getByText('Alice Smith')).toBeInTheDocument()
      expect(screen.getByText('@alice')).toBeInTheDocument()
      expect(screen.getByText('@bob')).toBeInTheDocument()
    })
  })

  it('shows title with resource name', () => {
    render(
      <ShareDialog
        open={true}
        onClose={vi.fn()}
        resourceType="folder"
        resourceId="folder-1"
        resourceName="Тестовая папка"
        myRole="OWNER"
      />
    )

    expect(screen.getByText(/Доступ к «Тестовая папка»/)).toBeInTheDocument()
  })

  it('shows add form for MANAGER role', async () => {
    render(
      <ShareDialog
        open={true}
        onClose={vi.fn()}
        resourceType="folder"
        resourceId="folder-1"
        resourceName="Тест"
        myRole="MANAGER"
      />
    )

    await waitFor(() => {
      expect(screen.getByPlaceholderText('Введите логин')).toBeInTheDocument()
    })
  })

  it('hides add form for VIEWER role', async () => {
    render(
      <ShareDialog
        open={true}
        onClose={vi.fn()}
        resourceType="folder"
        resourceId="folder-1"
        resourceName="Тест"
        myRole="VIEWER"
      />
    )

    await waitFor(() => {
      expect(screen.queryByPlaceholderText('Введите логин')).not.toBeInTheDocument()
    })
  })

  it('hides add form for EDITOR role', async () => {
    render(
      <ShareDialog
        open={true}
        onClose={vi.fn()}
        resourceType="folder"
        resourceId="folder-1"
        resourceName="Тест"
        myRole="EDITOR"
      />
    )

    await waitFor(() => {
      expect(screen.queryByPlaceholderText('Введите логин')).not.toBeInTheDocument()
    })
  })
})
