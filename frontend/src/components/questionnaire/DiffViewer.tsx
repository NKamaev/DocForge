import { useState, useEffect, useCallback } from 'react'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  Button,
  ScrollArea,
} from '@/components/ui'
import { Loader2, Eye } from 'lucide-react'
import { documentsApi } from '../../api/documentsApi'
import type { DiffChunk, RevisionDiff } from '../../types/document'

interface DiffViewerProps {
  diff: DiffChunk[]
}

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
    <div className="font-mono text-sm whitespace-pre-wrap">
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

interface RevisionDiffDialogProps {
  open: boolean
  onClose: () => void
  documentId: string
  revisionNumber: number
  previousRevisionNumber: number | null
}

export function RevisionDiffDialog({
  open,
  onClose,
  documentId,
  revisionNumber,
  previousRevisionNumber,
}: RevisionDiffDialogProps) {
  const hasPrevious = previousRevisionNumber != null
  const [baseline, setBaseline] = useState<DiffBaseline>(hasPrevious ? 'previous' : 'current')
  const [loading, setLoading] = useState(false)
  const [diffData, setDiffData] = useState<RevisionDiff | null>(null)
  const [error, setError] = useState<string | null>(null)

  // When the dialog is (re)opened or the selected revision changes, default
  // the baseline to the previous revision whenever one exists.
  useEffect(() => {
    setBaseline(hasPrevious ? 'previous' : 'current')
  }, [open, revisionNumber, hasPrevious])

  const compareTo = baseline === 'previous' && previousRevisionNumber != null
    ? previousRevisionNumber
    : undefined

  const loadData = useCallback(
    () => documentsApi.getRevisionDiff(documentId, revisionNumber, compareTo),
    [documentId, revisionNumber, compareTo],
  )

  useEffect(() => {
    if (!open) {
      setDiffData(null)
      setError(null)
      return
    }
    setLoading(true)
    setError(null)
    loadData()
      .then((data) => {
        setDiffData(data)
        setLoading(false)
      })
      .catch(() => {
        setError('Не удалось загрузить изменения')
        setLoading(false)
      })
  }, [open, loadData])

  const baselineLabel = baseline === 'previous' && previousRevisionNumber != null
    ? `Сравнение с редакцией ${previousRevisionNumber}`
    : 'Сравнение с текущим состоянием'

  return (
    <Dialog open={open} onOpenChange={(o) => !o && onClose()}>
      <DialogContent
        className="sm:max-w-4xl sm:max-h-[80vh] flex flex-col"
        aria-describedby={undefined}
      >
        <DialogHeader>
          <DialogTitle>Изменения в редакции {revisionNumber}</DialogTitle>
          <p className="text-sm text-muted-foreground">{baselineLabel}</p>
        </DialogHeader>

        <div
          className="flex flex-wrap gap-1"
          role="group"
          aria-label="Базовая редакция для сравнения"
        >
          <Button
            type="button"
            size="sm"
            variant={baseline === 'previous' ? 'default' : 'outline'}
            disabled={!hasPrevious}
            onClick={() => setBaseline('previous')}
          >
            С предыдущей редакцией
          </Button>
          <Button
            type="button"
            size="sm"
            variant={baseline === 'current' ? 'default' : 'outline'}
            onClick={() => setBaseline('current')}
          >
            С текущим состоянием
          </Button>
        </div>

        {loading ? (
          <div className="flex items-center justify-center py-12">
            <Loader2 className="size-6 animate-spin text-muted-foreground" />
            <span className="ml-2 text-muted-foreground">Загрузка...</span>
          </div>
        ) : error ? (
          <div className="text-center py-8 text-destructive">{error}</div>
        ) : diffData ? (
          <ScrollArea className="flex-1 min-h-[300px] max-h-[60vh] pr-4">
            <DiffViewer diff={diffData.diff} />
          </ScrollArea>
        ) : null}

        <div className="flex justify-end gap-2 mt-4 pt-2 border-t">
          <Button variant="outline" onClick={onClose}>
            Закрыть
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  )
}
