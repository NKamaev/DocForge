import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
import DocumentsPage from '../../pages/DocumentsPage'

// Mock API modules
vi.mock('../../api/membersApi', () => ({
  membersApi: {
    getFolderMembers: vi.fn().mockResolvedValue([]),
    getDocumentMembers: vi.fn().mockResolvedValue([]),
  },
}))

vi.mock('../../api/documentsApi', () => ({
  documentsApi: {
    list: vi.fn().mockResolvedValue({ content: [], totalElements: 0, totalPages: 0, size: 20, number: 0 }),
    delete: vi.fn(),
    moveToFolder: vi.fn(),
  },
}))

vi.mock('../../api/foldersApi', () => ({
  foldersApi: {
    list: vi.fn().mockResolvedValue([
      { id: 'f1', name: 'Договоры', color: '#1677ff', documentCount: 3 },
      { id: 'f2', name: 'Кадровые', color: '#52c41a', documentCount: 1 },
    ]),
    create: vi.fn().mockResolvedValue({ id: 'f3', name: 'Новая', color: '#1677ff', documentCount: 0 }),
    update: vi.fn(),
    delete: vi.fn(),
  },
}))

const Wrapper = ({ children }: { children: React.ReactNode }) => (
  <MemoryRouter>{children}</MemoryRouter>
)

describe('DocumentsPage folder tree', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders folders from API', async () => {
    render(<DocumentsPage />, { wrapper: Wrapper })
    await waitFor(() => {
      // folders appear in both sidebar and table — use getAllByText
      expect(screen.getAllByText('Договоры').length).toBeGreaterThan(0)
      expect(screen.getAllByText('Кадровые').length).toBeGreaterThan(0)
    })
  })

  it('renders "Все документы" row', async () => {
    render(<DocumentsPage />, { wrapper: Wrapper })
    await waitFor(() => {
      expect(screen.getByText('Все документы')).toBeInTheDocument()
    })
  })

  it('opens create folder modal on "Новая папка" button click', async () => {
    render(<DocumentsPage />, { wrapper: Wrapper })
    await waitFor(() => screen.getAllByText('Договоры'))

    const addButtons = screen.getAllByTitle('Создать папку')
    fireEvent.click(addButtons[0])

    await waitFor(() => {
      expect(screen.getByText('Новая папка')).toBeInTheDocument()
    })
  })

  it('clicking a folder in sidebar changes current folder', async () => {
    render(<DocumentsPage />, { wrapper: Wrapper })
    await waitFor(() => screen.getAllByText('Договоры'))

    // Click folder in the sidebar (first occurrence = sidebar)
    const folderLinks = screen.getAllByText('Договоры')
    fireEvent.click(folderLinks[0])

    // heading <h1> should now show the folder name
    await waitFor(() => {
      expect(screen.getByRole('heading', { name: 'Договоры' })).toBeInTheDocument()
    })
  })
})
