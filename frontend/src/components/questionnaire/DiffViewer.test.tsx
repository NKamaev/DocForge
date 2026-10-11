import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { DiffViewer, DocumentDiffView } from './DiffViewer'
import { documentsApi } from '../../api/documentsApi'
import type { DiffChunk, RevisionDiff } from '../../types/document'

vi.mock('../../api/documentsApi', () => ({
  documentsApi: {
    getRevisionDiff: vi.fn(),
  },
}))

describe('DiffViewer', () => {
  it('renders INSERT green and DELETE red with strikethrough', () => {
    // given — mixed diff with unchanged context, insertion and deletion
    const diff: DiffChunk[] = [
      { type: 'EQUAL', text: 'Общий текст ' },
      { type: 'INSERT', text: 'добавлено' },
      { type: 'DELETE', text: 'удалено' },
    ]

    // when
    render(<DiffViewer diff={diff} />)

    // then — all texts are present
    expect(screen.getByTestId('diff-chunk-EQUAL')).toHaveTextContent('Общий текст')
    expect(screen.getByTestId('diff-chunk-INSERT')).toHaveTextContent('добавлено')
    expect(screen.getByTestId('diff-chunk-DELETE')).toHaveTextContent('удалено')

    // then — INSERT is highlighted green
    const inserted = screen.getByTestId('diff-chunk-INSERT')
    expect(inserted.className).toContain('bg-green-100')

    // then — DELETE is highlighted red and struck through
    const deleted = screen.getByTestId('diff-chunk-DELETE')
    expect(deleted.className).toContain('bg-red-100')
    expect(deleted.className).toContain('line-through')

    // then — no empty-state stub is rendered
    expect(screen.queryByText('Изменений нет')).not.toBeInTheDocument()
  })

  it('shows "Изменений нет" when diff contains only EQUAL chunks', () => {
    // given
    const diff: DiffChunk[] = [{ type: 'EQUAL', text: 'Текст без изменений' }]

    // when
    render(<DiffViewer diff={diff} />)

    // then — stub is shown, no highlighted chunks
    expect(screen.getByText('Изменений нет')).toBeInTheDocument()
    expect(screen.queryByTestId('diff-chunk-EQUAL')).not.toBeInTheDocument()
  })

  it('does not crash and shows stub for empty diff array', () => {
    // given / when
    render(<DiffViewer diff={[]} />)

    // then
    expect(screen.getByText('Изменений нет')).toBeInTheDocument()
    expect(screen.queryByTestId(/diff-chunk-/)).not.toBeInTheDocument()
  })
})

const mixedDiff: RevisionDiff = {
  revisionId: 'rev-2',
  revisionNumber: 2,
  compareToRevisionNumber: 1,
  compareToCurrent: false,
  diff: [
    { type: 'EQUAL', text: 'Одинаково\n' },
    { type: 'INSERT', text: 'Новое\n' },
    { type: 'DELETE', text: 'Старое\n' },
  ],
}

describe('DocumentDiffView', () => {
  beforeEach(() => {
    vi.mocked(documentsApi.getRevisionDiff).mockReset()
  })

  it('defaults to previous-revision baseline when previousRevisionNumber is given', async () => {
    // given — previous revision 1 exists
    vi.mocked(documentsApi.getRevisionDiff).mockResolvedValue(mixedDiff)

    // when — mounted inline (no `open` prop) in the document pane
    render(
      <DocumentDiffView
        onClose={() => {}}
        documentId="doc-1"
        revisionNumber={2}
        previousRevisionNumber={1}
      />,
    )

    // then — API is called with (documentId, revisionNumber, previousRevisionNumber)
    expect(await screen.findByText('Новое')).toBeInTheDocument()
    expect(vi.mocked(documentsApi.getRevisionDiff)).toHaveBeenCalledWith('doc-1', 2, 1)

    // then — header labels the comparison against revision 1
    expect(screen.getByText('Сравнение с редакцией 1')).toBeInTheDocument()
  })

  it('switching to current-state baseline re-calls with compareTo undefined', async () => {
    // given — the view opens with previous-revision baseline
    vi.mocked(documentsApi.getRevisionDiff).mockResolvedValue(mixedDiff)
    render(
      <DocumentDiffView
        onClose={() => {}}
        documentId="doc-1"
        revisionNumber={2}
        previousRevisionNumber={1}
      />,
    )
    await screen.findByText('Новое')

    // when — user switches to compare with the current document state
    fireEvent.click(screen.getByRole('button', { name: 'С текущим состоянием' }))

    // then — a second request is issued without compareTo
    await waitFor(() => {
      expect(vi.mocked(documentsApi.getRevisionDiff)).toHaveBeenLastCalledWith('doc-1', 2, undefined)
    })
    expect(screen.getByText('Сравнение с текущим состоянием')).toBeInTheDocument()
  })

  it('disables previous-revision button and defaults to current when previousRevisionNumber is null', async () => {
    // given — no previous revision exists
    vi.mocked(documentsApi.getRevisionDiff).mockResolvedValue({
      ...mixedDiff,
      compareToRevisionNumber: null,
      compareToCurrent: true,
    })

    // when
    render(
      <DocumentDiffView
        onClose={() => {}}
        documentId="doc-1"
        revisionNumber={2}
        previousRevisionNumber={null}
      />,
    )

    // then — the previous-revision toggle is disabled
    expect(screen.getByRole('button', { name: 'С предыдущей редакцией' })).toBeDisabled()

    // then — baseline defaults to current state (compareTo undefined)
    expect(await screen.findByText('Новое')).toBeInTheDocument()
    expect(screen.getByText('Сравнение с текущим состоянием')).toBeInTheDocument()
    expect(vi.mocked(documentsApi.getRevisionDiff)).toHaveBeenCalledWith('doc-1', 2, undefined)
  })

  it('shows "Не удалось загрузить изменения" when the API rejects', async () => {
    // given — the diff request fails
    vi.mocked(documentsApi.getRevisionDiff).mockRejectedValue(new Error('network down'))

    // when
    render(
      <DocumentDiffView
        onClose={() => {}}
        documentId="doc-1"
        revisionNumber={2}
        previousRevisionNumber={1}
      />,
    )

    // then — a friendly error is shown instead of the diff
    expect(await screen.findByText('Не удалось загрузить изменения')).toBeInTheDocument()
    expect(screen.queryByText('Новое')).not.toBeInTheDocument()
  })

  it('shows "Изменений нет" when the diff contains only EQUAL chunks', async () => {
    // given — both revisions have identical text
    vi.mocked(documentsApi.getRevisionDiff).mockResolvedValue({
      ...mixedDiff,
      diff: [{ type: 'EQUAL', text: 'Без изменений\n' }],
    })

    // when
    render(
      <DocumentDiffView
        onClose={() => {}}
        documentId="doc-1"
        revisionNumber={2}
        previousRevisionNumber={1}
      />,
    )

    // then — the empty-state stub is rendered, no chunk elements
    expect(await screen.findByText('Изменений нет')).toBeInTheDocument()
    expect(screen.queryByTestId(/diff-chunk-/)).not.toBeInTheDocument()
  })

  it('calls onClose when "Вернуться к документу" is clicked', async () => {
    // given — the inline diff view is shown
    const onClose = vi.fn()
    vi.mocked(documentsApi.getRevisionDiff).mockResolvedValue(mixedDiff)

    // when
    render(
      <DocumentDiffView
        onClose={onClose}
        documentId="doc-1"
        revisionNumber={2}
        previousRevisionNumber={1}
      />,
    )
    await screen.findByText('Новое')

    fireEvent.click(screen.getByRole('button', { name: 'Вернуться к документу' }))

    // then — the parent is told to leave diff mode
    expect(onClose).toHaveBeenCalledTimes(1)
  })
})
