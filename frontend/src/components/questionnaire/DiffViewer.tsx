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
      {diff.map((chunk, i) => (
        <span key={i} className={chunkClass(chunk.type)}>
          {chunk.text}
        </span>
      ))}
    </div>
  )
}

function chunkClass(type: DiffChunk['type']): string {
  switch (type) {
    case 'INSERT':
      return 'bg-green-100 text-green-800 dark:bg-green-950 dark:text-green-300'
    case 'DELETE':
      return 'bg-red-100 text-red-800 line-through dark:bg-red-950 dark:text-red-300'
    default:
      return 'text-foreground'
  }
}

interface RevisionDiffDialogProps {
  open: boolean
  onClose: () => void
  documentId: string
  revisionNumber: number
}

export function RevisionDiffDialog({
  open,
  onClose,
  documentId,
  revisionNumber,
}: RevisionDiffDialogProps) {
  const [loading, setLoading] = useState(false)
  const [diffData, setDiffData] = useState<RevisionDiff | null>(null)
  const [error, setError] = useState<string | null>(null)

  const loadData = useCallback(async () => {
    const { documentsApi } = await import('../../api/documentsApi')
    return documentsApi.getRevisionDiff(documentId, revisionNumber)
  }, [documentId, revisionNumber])

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

  return (
    <Dialog open={open} onOpenChange={(o) => !o && onClose()}>
      <DialogContent
        className="sm:max-w-4xl sm:max-h-[80vh] flex flex-col"
        aria-describedby={undefined}
      >
        <DialogHeader>
          <DialogTitle>Изменения в редакции {revisionNumber}</DialogTitle>
        </DialogHeader>

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
