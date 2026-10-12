import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { DocumentDiffView } from './DiffViewer'
import { documentsApi } from '../../api/documentsApi'
import type { RevisionDiff } from '../../types/document'

vi.mock('../../api/documentsApi', () => ({
  documentsApi: {
    getRevisionDiff: vi.fn(),
  },
}))

// ── fixtures ──────────────────────────────────────────────────────────────────

const tiptap = (...lines: string[]) => ({
  type: 'doc',
  content: lines.map((line) => ({
    type: 'paragraph',
    content: [{ type: 'text', text: line }],
  })),
})

/**
 * target (revision 2):      «Одинаково» / «Новое»
 * compare (revision 1):     «Одинаково» / «Старое» / «Удалённое»
 *
 * Expected structural diff: one inline-edited paragraph («Старое» deleted,
 * «Новое» inserted) plus one deleted block («Удалённое»).
 */
const mixedDiff: RevisionDiff = {
  revisionNumber: 2,
  compareToRevisionNumber: 1,
  compareToCurrent: false,
  targetBody: tiptap('Одинаково', 'Новое'),
  compareBody: tiptap('Одинаково', 'Старое', 'Удалённое'),
}

function renderView(props: Partial<React.ComponentProps<typeof DocumentDiffView>> = {}) {
  return render(
    <DocumentDiffView
      documentId="doc-1"
      revisionNumber={2}
      previousRevisionNumber={1}
      onClose={() => {}}
      {...props}
    />,
  )
}

describe('DocumentDiffView', () => {
  beforeEach(() => {
    vi.mocked(documentsApi.getRevisionDiff).mockReset()
  })

  // ── read-only rendering ─────────────────────────────────────────────────────

  it('renders the structural diff in a read-only editor without an edit toolbar', async () => {
    // given
    vi.mocked(documentsApi.getRevisionDiff).mockResolvedValue(mixedDiff)

    // when
    const { container } = renderView()

    // then — the diff content is rendered
    expect(await screen.findByText('Новое')).toBeInTheDocument()
    expect(screen.getByText('Изменения в редакции 2')).toBeInTheDocument()

    // then — the ProseMirror editor is not editable
    const proseMirror = container.querySelector('.ProseMirror')
    expect(proseMirror).not.toBeNull()
    expect(proseMirror).toHaveAttribute('contenteditable', 'false')

    // then — no formatting toolbar (bold/italic/font controls) is mounted
    expect(container.querySelector('.toolbar')).toBeNull()
    expect(screen.queryByTestId('toolbar-bold')).not.toBeInTheDocument()
    expect(screen.queryByTestId('toolbar-italic')).not.toBeInTheDocument()
    expect(screen.queryByTestId('toolbar-font-family')).not.toBeInTheDocument()
  })

  it('renders inline insert/delete marks and a deleted block from the computed diff', async () => {
    // given
    vi.mocked(documentsApi.getRevisionDiff).mockResolvedValue(mixedDiff)

    // when
    const { container } = renderView()
    await screen.findByText('Новое')

    // then — inline word-level marks are present
    expect(container.querySelector('[data-diff="insert"]')).not.toBeNull()
    expect(container.querySelector('[data-diff="delete"]')).not.toBeNull()

    // then — the removed block is wrapped in a delete diffBlock
    const deletedBlock = container.querySelector('.diff-block-delete')
    expect(deletedBlock).not.toBeNull()
    expect(deletedBlock).toHaveTextContent('Удалённое')
  })

  it('renders no diff markers for identical bodies', async () => {
    // given — target and compare are identical
    vi.mocked(documentsApi.getRevisionDiff).mockResolvedValue({
      ...mixedDiff,
      targetBody: tiptap('Без изменений'),
      compareBody: tiptap('Без изменений'),
    })

    // when
    const { container } = renderView()
    await screen.findByText('Без изменений')

    // then
    expect(container.querySelector('[data-diff]')).toBeNull()
    expect(container.querySelector('.diff-block')).toBeNull()
  })

  // ── baseline toggle ─────────────────────────────────────────────────────────

  it('defaults to the previous-revision baseline and fetches with compareTo', async () => {
    // given
    vi.mocked(documentsApi.getRevisionDiff).mockResolvedValue(mixedDiff)

    // when
    renderView()

    // then — API called with the previous revision number
    await waitFor(() => {
      expect(vi.mocked(documentsApi.getRevisionDiff)).toHaveBeenCalledWith('doc-1', 2, 1)
    })

    // then — header labels the comparison and the previous button is active
    expect(screen.getByText('Сравнение с редакцией 1')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'С предыдущей редакцией' })).toHaveAttribute(
      'aria-pressed',
      'true',
    )
  })

  it('switching to the current-state baseline re-fetches without compareTo', async () => {
    // given — opens on the previous-revision baseline
    vi.mocked(documentsApi.getRevisionDiff).mockResolvedValue(mixedDiff)
    renderView()
    await screen.findByText('Новое')

    // when — user switches to compare with the current document state
    fireEvent.click(screen.getByRole('button', { name: 'С текущим состоянием' }))

    // then — a second request is issued without compareTo
    await waitFor(() => {
      expect(vi.mocked(documentsApi.getRevisionDiff)).toHaveBeenLastCalledWith(
        'doc-1',
        2,
        undefined,
      )
    })
    expect(screen.getByText('Сравнение с текущим состоянием')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'С текущим состоянием' })).toHaveAttribute(
      'aria-pressed',
      'true',
    )
  })

  it('disables the previous-revision toggle and defaults to current when there is no previous revision', async () => {
    // given — the selected revision is the earliest
    vi.mocked(documentsApi.getRevisionDiff).mockResolvedValue({
      ...mixedDiff,
      compareToRevisionNumber: null,
      compareToCurrent: true,
    })

    // when
    renderView({ previousRevisionNumber: null })

    // then — previous toggle disabled
    expect(screen.getByRole('button', { name: 'С предыдущей редакцией' })).toBeDisabled()

    // then — defaults to the current state and fetches without compareTo
    expect(await screen.findByText('Новое')).toBeInTheDocument()
    expect(screen.getByText('Сравнение с текущим состоянием')).toBeInTheDocument()
    await waitFor(() => {
      expect(vi.mocked(documentsApi.getRevisionDiff)).toHaveBeenCalledWith('doc-1', 2, undefined)
    })
  })

  // ── loading / error states ──────────────────────────────────────────────────

  it('shows a loading indicator while the diff request is pending', async () => {
    // given — a request that never resolves
    vi.mocked(documentsApi.getRevisionDiff).mockReturnValue(
      new Promise<RevisionDiff>(() => {}),
    )

    // when
    renderView()

    // then
    expect(await screen.findByText('Загрузка...')).toBeInTheDocument()
  })

  it('shows an error message when the diff request rejects', async () => {
    // given
    vi.mocked(documentsApi.getRevisionDiff).mockRejectedValue(new Error('network down'))

    // when
    const { container } = renderView()

    // then — a friendly error is shown instead of the diff
    expect(await screen.findByText('Не удалось загрузить изменения')).toBeInTheDocument()
    expect(screen.queryByText('Новое')).not.toBeInTheDocument()
    expect(container.querySelector('.ProseMirror')).toBeNull()
  })

  // ── close ───────────────────────────────────────────────────────────────────

  it('calls onClose when "Вернуться к документу" is clicked', async () => {
    // given
    const onClose = vi.fn()
    vi.mocked(documentsApi.getRevisionDiff).mockResolvedValue(mixedDiff)
    renderView({ onClose })
    await screen.findByText('Новое')

    // when
    fireEvent.click(screen.getByRole('button', { name: 'Вернуться к документу' }))

    // then
    expect(onClose).toHaveBeenCalledTimes(1)
  })
})
