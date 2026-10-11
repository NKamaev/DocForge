import { render, screen, fireEvent, act } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import * as Y from 'yjs'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import QuestionnairePage from './QuestionnairePage'
import { documentsApi } from '../../api/documentsApi'
import { templatesApi } from '../../api/templatesApi'
import type { Template } from '../../types/template'
import type { Document } from '../../types/document'

vi.mock('../../api/documentsApi', () => ({
  documentsApi: {
    getQuestionnaire: vi.fn(),
    getById: vi.fn(),
    saveAnswers: vi.fn(),
    saveRevision: vi.fn(),
    updateBody: vi.fn(),
    exportDocx: vi.fn(),
    exportPdf: vi.fn(),
    listRevisions: vi.fn(),
    restoreRevision: vi.fn(),
  },
}))

vi.mock('../../api/templatesApi', () => ({
  templatesApi: {
    getTemplate: vi.fn(),
    getSnapshot: vi.fn(),
  },
}))

// vi.hoisted runs before all vi.mock factories (and before imports are resolved).
// It lets us share mutable state between the factory and the tests.
const collabState = vi.hoisted(() => ({
  ydoc: null as unknown,
  yAnswers: null as unknown,
}))

// Mutable ref shared between DocumentWorkspace mock and tests to capture onBodyChange
const workspaceRef = vi.hoisted(() => ({ onBodyChange: null as ((body: unknown) => void) | null }))

vi.mock('../../hooks/useCollaboration', async () => {
  const { Doc } = await import('yjs')
  collabState.ydoc = new Doc()
  collabState.yAnswers = (collabState.ydoc as InstanceType<typeof Doc>).getMap<unknown>('answers')
  return {
    useCollaboration: () => ({
      ydoc: collabState.ydoc,
      yAnswers: collabState.yAnswers,
      isConnected: false,
      connectedUsers: [],
      provider: null,
    }),
  }
})

vi.mock('./DocumentWorkspace', () => ({
  default: (props: { onBodyChange?: (body: unknown) => void }) => {
    workspaceRef.onBodyChange = props.onBodyChange ?? null
    return <div data-testid="workspace" />
  },
}))

// DiffViewer is mocked: the page only decides *where* to render it (right pane,
// replacing the workspace). Behaviour of the diff view itself is covered by
// DiffViewer.test.tsx.
vi.mock('./DiffViewer', () => ({
  DocumentDiffView: ({
    revisionNumber,
    onClose,
  }: {
    revisionNumber: number
    onClose: () => void
  }) => (
    <div data-testid="document-diff-view" data-revision={revisionNumber}>
      <button onClick={onClose}>Вернуться к документу</button>
    </div>
  ),
}))

vi.mock('../ShareDialog', () => ({
  default: ({ open }: { open: boolean }) =>
    open ? <div data-testid="share-dialog">ShareDialog Content</div> : null,
}))

const mockDoc: Document = {
  id: 'doc-1',
  name: 'Doc',
  templateId: 'tmpl-1',
  templateVersionNumber: null,
  templateSnapshotId: null,
  ownerId: 'u1',
  folderId: null,
  status: 'DRAFT',
  createdAt: '',
  updatedAt: '',
  answers: null,
  generatedBody: null,
  currentBody: null,
  questionnaire: null,
}

const mockQuestionnaire = {
  title: 'Q',
  pages: [{
    id: 'p1',
    title: 'P1',
    questions: [{
      id: 'q1',
      parameterCode: 'name',
      label: 'Имя',
      hint: null,
      order: 1,
      type: 'text' as const,
      options: null,
      visible: true,
      required: false,
      currentValue: '',
    }],
  }],
}

const mockTemplate = {
  id: 'tmpl-1',
  code: null,
  name: 'T',
  description: null,
  category: null,
  version: 1,
  majorVersion: 1,
  minorVersion: 0,
  published: false,
  ownerId: 'u1',
  defaultFolderId: null,
  createdAt: '',
  updatedAt: '',
  parameters: [],
  questionnaire: null,
  body: null,
  formatting: null,
  scripts: null,
  phrasings: [],
} satisfies Template

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/documents/doc-1/fill']}>
      <Routes>
        <Route path="/documents/:id/fill" element={<QuestionnairePage />} />
      </Routes>
    </MemoryRouter>
  )
}

// Typed helpers to avoid casting throughout tests
function getSharedYdoc() { return collabState.ydoc as Y.Doc }
function getSharedYAnswers() { return collabState.yAnswers as Y.Map<unknown> }

describe('QuestionnairePage', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.mocked(documentsApi.getQuestionnaire).mockResolvedValue(mockQuestionnaire)
    vi.mocked(documentsApi.getById).mockResolvedValue(mockDoc)
    vi.mocked(templatesApi.getTemplate).mockResolvedValue(mockTemplate)
    vi.mocked(documentsApi.saveAnswers).mockResolvedValue({ answers: {}, questionnaire: mockQuestionnaire })
    vi.mocked(documentsApi.saveRevision).mockResolvedValue(undefined)
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.clearAllMocks()
  })

  it('exportDocx calls exportDocx API and triggers download', async () => {
    const mockBlob = new Blob(['docx'], { type: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document' })
    vi.mocked(documentsApi.exportDocx).mockResolvedValue(mockBlob)
    vi.mocked(documentsApi.updateBody).mockResolvedValue(undefined)

    const createObjectURL = vi.fn().mockReturnValue('blob:test-docx')
    const revokeObjectURL = vi.fn()
    vi.stubGlobal('URL', { createObjectURL, revokeObjectURL })
    const clickSpy = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})

    renderPage()
    await act(async () => { await vi.runAllTimersAsync() })

    fireEvent.click(screen.getByText('DOCX'))
    await act(async () => { await vi.runAllTimersAsync() })

    expect(documentsApi.exportDocx).toHaveBeenCalledWith('doc-1')
    expect(createObjectURL).toHaveBeenCalledWith(mockBlob)
    expect(clickSpy).toHaveBeenCalled()
    clickSpy.mockRestore()
  })

  it('exportPdf calls exportPdf API and triggers download', async () => {
    const mockBlob = new Blob(['pdf'], { type: 'application/pdf' })
    vi.mocked(documentsApi.exportPdf).mockResolvedValue(mockBlob)
    vi.mocked(documentsApi.updateBody).mockResolvedValue(undefined)

    const createObjectURL = vi.fn().mockReturnValue('blob:test-pdf')
    const revokeObjectURL = vi.fn()
    vi.stubGlobal('URL', { createObjectURL, revokeObjectURL })
    const clickSpy = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})

    renderPage()
    await act(async () => { await vi.runAllTimersAsync() })

    fireEvent.click(screen.getByText('PDF'))
    await act(async () => { await vi.runAllTimersAsync() })

    expect(documentsApi.exportPdf).toHaveBeenCalledWith('doc-1')
    expect(createObjectURL).toHaveBeenCalledWith(mockBlob)
    expect(clickSpy).toHaveBeenCalled()
    clickSpy.mockRestore()
  })

  it('render shows edition history with authorName', async () => {
    const mockRevisions = [
      { id: 'r1', editionNumber: 1, savedAt: '2026-04-01T10:00:00Z', savedBy: 'u1', authorName: 'Иван Иванов', comment: 'Первый вариант' },
      { id: 'r2', editionNumber: 2, savedAt: '2026-04-02T12:00:00Z', savedBy: 'u2', authorName: null, comment: null },
    ]
    vi.mocked(documentsApi.listRevisions).mockResolvedValue(mockRevisions)

    renderPage()
    await act(async () => { await vi.runAllTimersAsync() })

    fireEvent.click(screen.getByText('История'))
    await act(async () => { await vi.runAllTimersAsync() })

    expect(screen.getByText('История редакций')).toBeInTheDocument()
    expect(screen.getByText('Редакция 1')).toBeInTheDocument()
    expect(screen.getByText('Редакция 2')).toBeInTheDocument()
    expect(screen.getByText('Первый вариант')).toBeInTheDocument()
    expect(screen.getByText((content, element) =>
      element?.tagName === 'SPAN' && content.includes('Иван Иванов')
    )).toBeInTheDocument()
  })

  it('scheduleSave calls saveRevision after saveAnswers', async () => {
    renderPage()

    await act(async () => {
      await vi.runAllTimersAsync()
    })

    const input = screen.getByRole('textbox')
    fireEvent.change(input, { target: { value: 'Иван' } })

    await act(async () => {
      await vi.runAllTimersAsync()
    })

    expect(documentsApi.saveAnswers).toHaveBeenCalledWith('doc-1', { answers: { name: 'Иван' } })
    expect(documentsApi.saveRevision).toHaveBeenCalledWith('doc-1', 'Автосохранение')
  })

  it('scheduleBodySave calls updateBody and saveRevision through debounce when body changes', async () => {
    vi.mocked(documentsApi.updateBody).mockResolvedValue(undefined)

    renderPage()
    await act(async () => { await vi.runAllTimersAsync() })

    const mockBody = { type: 'doc', content: [{ type: 'paragraph', content: [{ type: 'text', text: 'Hello' }] }] }

    // Simulate body change via the DocumentWorkspace mock callback
    await act(async () => {
      workspaceRef.onBodyChange!(mockBody)
    })

    // Should NOT be called immediately (debounce has not elapsed)
    expect(documentsApi.updateBody).not.toHaveBeenCalled()
    expect(documentsApi.saveRevision).not.toHaveBeenCalled()

    // Advance past the 500ms debounce
    await act(async () => { await vi.runAllTimersAsync() })

    expect(documentsApi.updateBody).toHaveBeenCalledWith('doc-1', mockBody)
    expect(documentsApi.saveRevision).toHaveBeenCalledWith('doc-1', 'Автосохранение')
  })

  it('scheduleBodySave does NOT call saveRevision immediately — only after debounce delay', async () => {
    vi.mocked(documentsApi.updateBody).mockResolvedValue(undefined)

    renderPage()
    await act(async () => { await vi.runAllTimersAsync() })

    const saveRevCallsBefore = vi.mocked(documentsApi.saveRevision).mock.calls.length
    const updateBodyCallsBefore = vi.mocked(documentsApi.updateBody).mock.calls.length

    // Trigger body change
    await act(async () => {
      workspaceRef.onBodyChange!({ type: 'doc' })
    })

    // Check immediately — no debounced calls yet
    expect(vi.mocked(documentsApi.saveRevision).mock.calls.length).toBe(saveRevCallsBefore)
    expect(vi.mocked(documentsApi.updateBody).mock.calls.length).toBe(updateBodyCallsBefore)

    // Advance past debounce
    await act(async () => { await vi.runAllTimersAsync() })

    // Now both should have been called
    expect(documentsApi.saveRevision).toHaveBeenCalledWith('doc-1', 'Автосохранение')
    expect(documentsApi.updateBody).toHaveBeenCalledWith('doc-1', { type: 'doc' })
  })

  it('handleAnswerChange writes value into yAnswers', async () => {
    renderPage()
    await act(async () => { await vi.runAllTimersAsync() })

    const input = screen.getByRole('textbox')
    fireEvent.change(input, { target: { value: 'Тест' } })
    await act(async () => { await vi.runAllTimersAsync() })

    expect(getSharedYAnswers().get('name')).toBe('Тест')
  })

  it('yAnswers.observe updates questionnaire on remote change', async () => {
    renderPage()
    await act(async () => { await vi.runAllTimersAsync() })

    vi.mocked(documentsApi.getQuestionnaire).mockResolvedValue({
      ...mockQuestionnaire,
      title: 'Remote Q',
    })

    // Simulate a remote peer: create a separate Y.Doc, make a change,
    // then apply its update to the shared doc — produces transaction.local === false
    await act(async () => {
      const remoteDoc = new Y.Doc()
      const remoteAnswers = remoteDoc.getMap<unknown>('answers')
      remoteAnswers.set('name', 'RemoteValue')
      const update = Y.encodeStateAsUpdate(remoteDoc)
      Y.applyUpdate(getSharedYdoc(), update)
    })

    await act(async () => { await vi.runAllTimersAsync() })

    // Handler fired → re-fetched questionnaire
    expect(documentsApi.getQuestionnaire).toHaveBeenCalledTimes(2)
  })

  it('shows ShareDialog button in toolbar and opens dialog on click', async () => {
    renderPage()
    await act(async () => { await vi.runAllTimersAsync() })

    const shareButton = screen.getByText('Доступ')
    expect(shareButton).toBeInTheDocument()

    expect(screen.queryByTestId('share-dialog')).not.toBeInTheDocument()

    fireEvent.click(shareButton)
    await act(async () => { await vi.runAllTimersAsync() })

    expect(screen.getByTestId('share-dialog')).toBeInTheDocument()
  })

  it('local yAnswers change does not trigger remote refetch', async () => {
    renderPage()
    await act(async () => { await vi.runAllTimersAsync() })

    const callsBefore = vi.mocked(documentsApi.getQuestionnaire).mock.calls.length

    // Direct set = local transaction → handler returns early, no refetch
    await act(async () => {
      getSharedYAnswers().set('name', 'LocalValue')
    })

    await act(async () => { await vi.runAllTimersAsync() })

    expect(vi.mocked(documentsApi.getQuestionnaire).mock.calls.length).toBe(callsBefore)
  })

  it('renders DocumentDiffView in the right pane on "Показать изменения" and restores DocumentWorkspace on close', async () => {
    const mockRevisions = [
      { id: 'r1', editionNumber: 1, savedAt: '2026-04-01T10:00:00Z', savedBy: 'u1', authorName: 'Иван Иванов', comment: null },
      { id: 'r2', editionNumber: 2, savedAt: '2026-04-02T12:00:00Z', savedBy: 'u2', authorName: null, comment: null },
    ]
    vi.mocked(documentsApi.listRevisions).mockResolvedValue(mockRevisions)

    renderPage()
    await act(async () => { await vi.runAllTimersAsync() })

    // open the revision history drawer
    fireEvent.click(screen.getByText('История'))
    await act(async () => { await vi.runAllTimersAsync() })

    // not in diff mode yet — the editable workspace is shown
    expect(screen.getByTestId('workspace')).toBeInTheDocument()
    expect(screen.queryByTestId('document-diff-view')).not.toBeInTheDocument()

    // normal mode — editing and export controls are available
    expect(screen.getByText('Сохранить редакцию')).toBeInTheDocument()
    expect(screen.getByText('DOCX')).toBeInTheDocument()
    expect(screen.getByText('PDF')).toBeInTheDocument()

    // click the "Показать изменения" icon button of the latest revision (2)
    const diffButtons = screen.getAllByRole('button', { name: 'Показать изменения' })
    fireEvent.click(diffButtons[diffButtons.length - 1])
    await act(async () => { await vi.runAllTimersAsync() })

    // the right pane now renders the inline diff instead of the workspace
    expect(screen.getByTestId('document-diff-view')).toHaveAttribute('data-revision', '2')
    expect(screen.queryByTestId('workspace')).not.toBeInTheDocument()

    // entering diff mode closes the history drawer so its overlay cannot cover the diff
    expect(screen.queryByText('История редакций')).not.toBeInTheDocument()

    // diff mode is view-only — editing and export controls are removed from the DOM
    expect(screen.queryByText('Сохранить редакцию')).not.toBeInTheDocument()
    expect(screen.queryByText('DOCX')).not.toBeInTheDocument()
    expect(screen.queryByText('PDF')).not.toBeInTheDocument()

    // closing diff mode brings the editable workspace back
    fireEvent.click(screen.getByRole('button', { name: 'Вернуться к документу' }))
    await act(async () => { await vi.runAllTimersAsync() })

    expect(screen.queryByTestId('document-diff-view')).not.toBeInTheDocument()
    expect(screen.getByTestId('workspace')).toBeInTheDocument()

    // and the editing and export controls reappear
    expect(screen.getByText('Сохранить редакцию')).toBeInTheDocument()
    expect(screen.getByText('DOCX')).toBeInTheDocument()
    expect(screen.getByText('PDF')).toBeInTheDocument()
  })
})
