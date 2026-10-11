import { useEffect, useState } from 'react'
import { Loader2, Eye } from 'lucide-react'
import { documentsApi } from '../../api/documentsApi'
import type { DiffChunk, RevisionDiff } from '../../types/document'

interface DiffViewerProps {
  diff: DiffChunk[]
}

/**
 * Pure chunk renderer for a revision diff.
 *
 * Rendered inline inside the document-style page (`.editor-canvas` →
 * `.tiptap-page`) so additions/removals read as part of the document text:
 * INSERT — green + underline, DELETE — red + strikethrough.
 */
export function DiffViewer({ diff }: DiffViewerProps) {
  const hasChanges = diff.some((c) => c.type === 'INSERT' || c.type === 'DELETE')

  if (!hasChanges) {
    return (
      <div className="text-center py-8 text-muted-foreground">
        <Eye className="mx-auto mb-2 size-8 opacity-50" />
        <div>Изменений нет</div>
      </div>
    )
  }

  return (
    <div className="whitespace-pre-wrap">
      {diff.map((chunk, i) =>
        chunk.type === 'INSERT' ? (
          <ins
            key={i}
            data-testid={`diff-chunk-${chunk.type}`}
            data-chunk-type={chunk.type}
            className={chunkClass(chunk.type)}
          >
            {chunk.text}
          </ins>
        ) : chunk.type === 'DELETE' ? (
          <del
            key={i}
            data-testid={`diff-chunk-${chunk.type}`}
            data-chunk-type={chunk.type}
            className={chunkClass(chunk.type)}
          >
            {chunk.text}
          </del>
        ) : (
          <span
            key={i}
            data-testid={`diff-chunk-${chunk.type}`}
            data-chunk-type={chunk.type}
            className={chunkClass(chunk.type)}
          >
            {chunk.text}
          </span>
        )
      )}
    </div>
  )
}

function chunkClass(type: DiffChunk['type']): string {
  switch (type) {
    case 'INSERT':
      return 'bg-green-100 text-green-800 underline dark:bg-green-950 dark:text-green-300'
    case 'DELETE':
      return 'bg-red-100 text-red-800 line-through dark:bg-red-950 dark:text-red-300'
    default:
      return 'text-foreground'
  }
}

type DiffBaseline = 'previous' | 'current'

interface DocumentDiffViewProps {
  documentId: string
  revisionNumber: number
  previousRevisionNumber: number | null
  onClose: () => void
}

/**
 * Inline revision diff, rendered in the right-hand document pane in place of
 * the live editor (`.editor-canvas` → `.tiptap-page`), so the diff looks like
 * the document page instead of a modal.
 */
export function DocumentDiffView({
  documentId,
  revisionNumber,
  previousRevisionNumber,
  onClose,
}: DocumentDiffViewProps) {
  const hasPrevious = previousRevisionNumber != null
  const [baseline, setBaseline] = useState<DiffBaseline>(hasPrevious ? 'previous' : 'current')
  const [loading, setLoading] = useState(false)
  const [diffData, setDiffData] = useState<RevisionDiff | null>(null)
  const [error, setError] = useState<string | null>(null)

  // When the selected revision changes, default the baseline to the previous
  // revision whenever one exists.
  useEffect(() => {
    setBaseline(hasPrevious ? 'previous' : 'current')
  }, [revisionNumber, hasPrevious])

  const compareTo = baseline === 'previous' && previousRevisionNumber != null
    ? previousRevisionNumber
    : undefined

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setError(null)
    documentsApi
      .getRevisionDiff(documentId, revisionNumber, compareTo)
      .then((data) => {
        if (cancelled) return
        setDiffData(data)
        setLoading(false)
      })
      .catch(() => {
        if (cancelled) return
        setError('Не удалось загрузить изменения')
        setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [documentId, revisionNumber, compareTo])

  const baselineLabel = baseline === 'previous' && previousRevisionNumber != null
    ? `Сравнение с редакцией ${previousRevisionNumber}`
    : 'Сравнение с текущим состоянием'

  return (
    <div style={{ flex: 1, display: 'flex', flexDirection: 'column', minHeight: 0 }}>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 10,
          padding: '8px 16px',
          background: 'var(--bg-soft)',
          borderBottom: '1px solid var(--line)',
          flexShrink: 0,
        }}
      >
        <div style={{ display: 'flex', flexDirection: 'column', minWidth: 0 }}>
          <span style={{ fontSize: 12.5, fontWeight: 550 }}>Изменения в редакции {revisionNumber}</span>
          <span style={{ fontSize: 11, color: 'var(--ink-3)' }}>{baselineLabel}</span>
        </div>
        <div style={{ marginLeft: 'auto', display: 'flex', alignItems: 'center', gap: 6 }}>
          <div
            style={{ display: 'flex', gap: 6 }}
            role="group"
            aria-label="Базовая редакция для сравнения"
          >
            <button
              type="button"
              className={'btn xs' + (baseline === 'previous' ? ' primary' : '')}
              aria-pressed={baseline === 'previous'}
              disabled={!hasPrevious}
              onClick={() => setBaseline('previous')}
            >
              С предыдущей редакцией
            </button>
            <button
              type="button"
              className={'btn xs' + (baseline === 'current' ? ' primary' : '')}
              aria-pressed={baseline === 'current'}
              onClick={() => setBaseline('current')}
            >
              С текущим состоянием
            </button>
          </div>
          <div style={{ width: 1, height: 18, background: 'var(--line)' }} />
          <button type="button" className="btn xs ghost" onClick={onClose}>
            Вернуться к документу
          </button>
        </div>
      </div>

      <div className="editor-canvas">
        <div className="tiptap-page">
          <div className="ProseMirror">
            {loading ? (
              <div className="flex items-center justify-center py-12">
                <Loader2 className="size-6 animate-spin text-muted-foreground" />
                <span className="ml-2 text-muted-foreground">Загрузка...</span>
              </div>
            ) : error ? (
              <div className="text-center py-8 text-destructive">{error}</div>
            ) : diffData ? (
              <DiffViewer diff={diffData.diff} />
            ) : null}
          </div>
        </div>
      </div>
    </div>
  )
}
