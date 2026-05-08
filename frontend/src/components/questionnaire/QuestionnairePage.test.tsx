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
  default: () => <div data-testid="workspace" />,
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
})
