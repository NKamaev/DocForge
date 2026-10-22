/**
 * Tests for BUG-049 — bulk actions on the documents list.
 *
 * `frontend/src/pages/DocumentsPage.tsx` previously rendered the bulk
 * «Скачать» / «Удалить» buttons with NO `onClick`, so the bulk actions did
 * nothing. These tests pin the contract of the fix:
 *
 * ---------------------------------------------------------------------------
 * ASSUMED (minimal, consistent-with-existing-code) API CONTRACT for the fix
 * ---------------------------------------------------------------------------
 *
 * Bulk download («Скачать»):
 *   For every selected *document* (selection keys that are NOT folder keys
 *   prefixed with `f:`), call the existing `documentsApi.exportDocx(id)` once.
 *   DOCX is the primary/editable deliverable and the toolbar has no format
 *   selector, so DOCX is the default format. Each returned blob is then handed
 *   to the browser via `URL.createObjectURL` + a temporary `<a>` click
 *   (same pattern already used in `QuestionnairePage`).
 *
 * Bulk delete («Удалить»):
 *   Clicking the bulk «Удалить» button opens a confirmation AlertDialog
 *   (the ticket explicitly requires "с подтверждением"). Confirming calls the
 *   existing `documentsApi.delete(id)` once per selected document id, then
 *   reloads the document list. Nothing is deleted before confirmation.
 *
 * Folder selections (`f:<folderId>` keys) are out of scope for both bulk
 * actions here — they cannot be exported and deletion of folders already has
 * its own dedicated flow via the folder row dropdown, so both buttons are
 * disabled for a folder-only selection.
 *
 * Partial failure: one failing id must not abort the remaining deletes; only
 * the succeeded ids leave the selection, the list is still reloaded, and the
 * result is surfaced as a toast («Удалено: X из Y»).
 */
import { render, screen, fireEvent, waitFor, within } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
import DocumentsPage from './DocumentsPage'
import { documentsApi } from '../api/documentsApi'
import { foldersApi } from '../api/foldersApi'
import { ToastContainer } from '@/components/ui/toast'

vi.mock('../api/membersApi', () => ({
  membersApi: {
    getFolderMembers: vi.fn().mockResolvedValue([]),
    getDocumentMembers: vi.fn().mockResolvedValue([]),
  },
}))

vi.mock('../api/usersApi', () => ({
  usersApi: {
    searchByLogin: vi.fn().mockResolvedValue([]),
  },
}))

vi.mock('../api/templatesApi', () => ({
  templatesApi: {
    getTemplates: vi.fn().mockResolvedValue({ content: [], totalElements: 0, totalPages: 0, size: 100, number: 0 }),
    getVersions: vi.fn().mockResolvedValue([]),
  },
}))

vi.mock('../api/documentsApi', () => ({
  documentsApi: {
    list: vi.fn(),
    create: vi.fn(),
    delete: vi.fn(),
    moveToFolder: vi.fn(),
    exportDocx: vi.fn(),
    exportPdf: vi.fn(),
  },
}))

vi.mock('../api/foldersApi', () => ({
  foldersApi: {
    list: vi.fn().mockResolvedValue([]),
    create: vi.fn(),
    update: vi.fn(),
    delete: vi.fn(),
  },
}))

const DOCS = [
  { id: 'doc-1', name: 'Документ А', status: 'DRAFT' as const, templateId: 't-1', folderId: null, createdAt: '2026-01-01T00:00:00Z' },
  { id: 'doc-2', name: 'Документ Б', status: 'EXPORTED' as const, templateId: 't-2', folderId: null, createdAt: '2026-01-02T00:00:00Z' },
]

function mockList(docs: typeof DOCS) {
  vi.mocked(documentsApi.list).mockResolvedValue({
    content: docs,
    totalElements: docs.length,
    totalPages: 1,
    size: 200,
    number: 0,
  })
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/documents']}>
      <DocumentsPage />
      <ToastContainer />
    </MemoryRouter>,
  )
}

/** Wait for the document rows, tick the header "select all" checkbox and
 *  return once the bulk bar is visible. */
async function selectAllDocuments(count = 2) {
  renderPage()
  await screen.findByText('Документ А')
  await screen.findByText('Документ Б')

  const checkboxes = screen.getAllByRole('checkbox')
  // checkboxes[0] is the table header "select all"
  fireEvent.click(checkboxes[0])

  await screen.findByText(`Выбрано: ${count}`)
}

describe('DocumentsPage bulk actions (BUG-049)', () => {
  let clickSpy: ReturnType<typeof vi.spyOn>
  let originalCreateObjectURL: typeof URL.createObjectURL
  let originalRevokeObjectURL: typeof URL.revokeObjectURL
  let originalTitle: string

  beforeEach(() => {
    vi.clearAllMocks()
    mockList(DOCS)
    vi.mocked(foldersApi.list).mockResolvedValue([])
    vi.mocked(documentsApi.delete).mockResolvedValue(undefined)
    vi.mocked(documentsApi.exportDocx).mockResolvedValue(new Blob(['docx']))
    vi.mocked(documentsApi.exportPdf).mockResolvedValue(new Blob(['pdf']))

    originalCreateObjectURL = URL.createObjectURL
    originalRevokeObjectURL = URL.revokeObjectURL
    originalTitle = document.title

    // Allow a correct fix to actually trigger the browser download.
    Object.defineProperty(URL, 'createObjectURL', { writable: true, value: vi.fn(() => 'blob:test') })
    Object.defineProperty(URL, 'revokeObjectURL', { writable: true, value: vi.fn() })
    clickSpy = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
  })

  afterEach(() => {
    clickSpy.mockRestore()
    // Restore globals touched by the tests / showToast (BUG-029 sets document.title).
    Object.defineProperty(URL, 'createObjectURL', { writable: true, value: originalCreateObjectURL })
    Object.defineProperty(URL, 'revokeObjectURL', { writable: true, value: originalRevokeObjectURL })
    document.title = originalTitle
  })

  it('bulk download: «Скачать» exports DOCX for every selected document', async () => {
    await selectAllDocuments()

    fireEvent.click(screen.getByRole('button', { name: /Скачать/ }))

    await waitFor(() => {
      // Contract: DOCX export per selected document id (no format selector).
      expect(documentsApi.exportDocx).toHaveBeenCalledWith('doc-1')
      expect(documentsApi.exportDocx).toHaveBeenCalledWith('doc-2')
    })
    expect(documentsApi.exportDocx).toHaveBeenCalledTimes(2)
  })

  it('bulk delete: «Удалить» asks for confirmation and deletes every selected document', async () => {
    await selectAllDocuments()

    fireEvent.click(screen.getByRole('button', { name: /Удалить/ }))

    // Contract: confirmation dialog, mirroring single-document delete.
    const dialog = await screen.findByRole('alertdialog')
    fireEvent.click(within(dialog).getByRole('button', { name: /^Удалить$/ }))

    await waitFor(() => {
      expect(documentsApi.delete).toHaveBeenCalledWith('doc-1')
      expect(documentsApi.delete).toHaveBeenCalledWith('doc-2')
    })
    expect(documentsApi.delete).toHaveBeenCalledTimes(2)
  })

  it('bulk delete: nothing is deleted before the confirmation is accepted', async () => {
    await selectAllDocuments()

    fireEvent.click(screen.getByRole('button', { name: /Удалить/ }))

    const dialog = await screen.findByRole('alertdialog')
    expect(documentsApi.delete).not.toHaveBeenCalled()

    // Cancelling must also leave everything untouched.
    fireEvent.click(within(dialog).getByRole('button', { name: /Отмена/ }))
    await waitFor(() => expect(screen.queryByRole('alertdialog')).toBeNull())
    expect(documentsApi.delete).not.toHaveBeenCalled()
  })

  it('bulk bar: a folder-only selection disables both bulk actions', async () => {
    vi.mocked(foldersApi.list).mockResolvedValue([
      { id: 'folder-1', name: 'Папка 1', color: null, documentCount: 0, parentId: null },
    ])

    renderPage()
    await screen.findAllByText('Папка 1')

    // The folder is rendered in the sidebar too — target the table row.
    const folderRow = screen.getAllByText('Папка 1')
      .map(el => el.closest('tr'))
      .find((el): el is HTMLTableRowElement => el !== null)
    expect(folderRow).toBeTruthy()
    fireEvent.click(within(folderRow!).getByRole('checkbox'))

    await screen.findByText('Выбрано: 0 (папок: 1)')
    expect(screen.getByRole('button', { name: /Скачать/ })).toBeDisabled()
    expect(screen.getByRole('button', { name: /Удалить/ })).toBeDisabled()
  })

  it('bulk delete: a failing id does not abort the rest, prunes only succeeded ids and still reloads', async () => {
    // Regression for partial-failure resilience of handleBulkDelete:
    //  - every selected id is deleted on its own (one failure must not abort the loop);
    //  - only the succeeded ids are pruned from `selected` (the failed one can be retried);
    //  - the list is reloaded and the dialog closed even though a delete failed.
    const THREE_DOCS = [
      ...DOCS,
      { id: 'doc-3', name: 'Документ В', status: 'DRAFT' as const, templateId: 't-3', folderId: null, createdAt: '2026-01-03T00:00:00Z' },
    ]
    mockList(THREE_DOCS)
    // doc-2 fails, the other two succeed.
    vi.mocked(documentsApi.delete).mockImplementation((id: string) =>
      id === 'doc-2' ? Promise.reject(new Error('boom')) : Promise.resolve(undefined),
    )

    renderPage()
    await screen.findByText('Документ В')

    // checkboxes[0] is the table header "select all"
    fireEvent.click(screen.getAllByRole('checkbox')[0])
    await screen.findByText('Выбрано: 3')

    fireEvent.click(screen.getByRole('button', { name: /Удалить/ }))
    const dialog = await screen.findByRole('alertdialog')
    fireEvent.click(within(dialog).getByRole('button', { name: /^Удалить$/ }))

    // The failure of doc-2 must not stop doc-3 from being attempted.
    await waitFor(() => {
      expect(documentsApi.delete).toHaveBeenCalledTimes(3)
    })
    expect(documentsApi.delete).toHaveBeenCalledWith('doc-1')
    expect(documentsApi.delete).toHaveBeenCalledWith('doc-2')
    expect(documentsApi.delete).toHaveBeenCalledWith('doc-3')

    // Only the succeeded ids leave the selection; the failed id stays selected.
    await screen.findByText('Выбрано: 1')

    // Reload still happened (mount + post-delete reload) and the dialog was closed.
    await waitFor(() => {
      expect(documentsApi.list).toHaveBeenCalledTimes(2)
    })
    expect(screen.queryByRole('alertdialog')).toBeNull()

    // The partial result was surfaced to the user as a toast.
    await screen.findByText(/Удалено: 2 из 3/)
  })
})
