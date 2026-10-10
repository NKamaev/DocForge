import { useCallback, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Plus, Edit, Trash2 } from 'lucide-react'
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  Badge,
  Button,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui'
import { documentsApi } from '../../api/documentsApi'
import type { DocumentListItem } from '../../types/document'
import CreateDocumentModal from './CreateDocumentModal'

function showNotification(type: 'success' | 'error' | 'warning', message: string) {
  console.log(`[${type.toUpperCase()}]`, message)
  if (type === 'error') {
    alert(message)
  }
}

const STATUS_LABELS: Record<string, { label: string; className: string }> = {
  DRAFT:     { label: 'Черновик',   className: 'bg-muted text-muted-foreground hover:bg-muted' },
  GENERATED: { label: 'Сгенерирован', className: 'bg-blue-100 text-blue-700 hover:bg-blue-100' },
  EXPORTED:  { label: 'Экспортирован', className: 'bg-green-100 text-green-700 hover:bg-green-100' },
}

export default function MyDocuments() {
  const navigate = useNavigate()
  const [documents, setDocuments] = useState<DocumentListItem[]>([])
  const [loading, setLoading] = useState(false)
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [createOpen, setCreateOpen] = useState(false)
  const [deleteConfirmId, setDeleteConfirmId] = useState<string | null>(null)

  const loadDocuments = useCallback(async (pageNum = 1) => {
    setLoading(true)
    try {
      const res = await documentsApi.list(pageNum - 1, 20)
      setDocuments(res.content)
      setTotal(res.totalElements)
    } catch {
      showNotification('error', 'Не удалось загрузить документы')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { loadDocuments(page) }, [page, loadDocuments])

  const handleDeleteConfirm = async () => {
    if (!deleteConfirmId) return
    try {
      await documentsApi.delete(deleteConfirmId)
      showNotification('success', 'Документ удалён')
      loadDocuments(page)
    } catch {
      showNotification('error', 'Не удалось удалить документ')
    } finally {
      setDeleteConfirmId(null)
    }
  }

  const totalPages = Math.ceil(total / 20)

  return (
    <div className="p-6 md:p-8">
      <div className="flex justify-between items-center mb-5">
        <div className="flex items-center gap-3">
          <h3 className="text-lg font-semibold m-0">Мои документы</h3>
          <Badge variant="secondary">{total}</Badge>
        </div>
        <Button onClick={() => setCreateOpen(true)}>
          <Plus className="mr-1.5 size-4" /> Создать документ
        </Button>
      </div>

      <div className="rounded-lg border">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead className="w-[40%]">Название</TableHead>
              <TableHead>Статус</TableHead>
              <TableHead>Дата создания</TableHead>
              <TableHead className="w-[100px]"></TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {loading ? (
              <TableRow>
                <TableCell colSpan={4} className="text-center text-muted-foreground py-8">
                  Загрузка…
                </TableCell>
              </TableRow>
            ) : documents.length === 0 ? (
              <TableRow>
                <TableCell colSpan={4} className="text-center text-muted-foreground py-8">
                  Документов пока нет. Создайте первый!
                </TableCell>
              </TableRow>
            ) : (
              documents.map(doc => {
                const s = STATUS_LABELS[doc.status] ?? { label: doc.status, className: 'bg-muted text-muted-foreground' }
                return (
                  <TableRow
                    key={doc.id}
                    className="cursor-pointer"
                    onClick={(e) => {
                      if ((e.target as HTMLElement).closest('button, [role="button"]')) return
                      navigate(`/documents/${doc.id}/fill`)
                    }}
                  >
                    <TableCell>
                      <button
                        className="font-medium text-left hover:underline bg-transparent border-none cursor-pointer p-0 text-inherit"
                        onClick={() => navigate(`/documents/${doc.id}/fill`)}
                      >
                        {doc.name}
                      </button>
                    </TableCell>
                    <TableCell>
                      <Badge className={s.className} variant="outline">
                        {s.label}
                      </Badge>
                    </TableCell>
                    <TableCell className="text-muted-foreground tabular-nums">
                      {new Date(doc.createdAt).toLocaleDateString('ru-RU', {
                        day: '2-digit', month: '2-digit', year: 'numeric',
                        hour: '2-digit', minute: '2-digit',
                      })}
                    </TableCell>
                    <TableCell>
                      <div className="flex gap-1">
                        <Button
                          variant="ghost"
                          size="icon-sm"
                          onClick={(e) => { e.stopPropagation(); navigate(`/documents/${doc.id}/fill`) }}
                        >
                          <Edit className="size-4" />
                        </Button>
                        <Button
                          variant="ghost"
                          size="icon-sm"
                          onClick={(e) => { e.stopPropagation(); setDeleteConfirmId(doc.id) }}
                        >
                          <Trash2 className="size-4 text-destructive" />
                        </Button>
                      </div>
                    </TableCell>
                  </TableRow>
                )
              })
            )}
          </TableBody>
        </Table>
      </div>

      {/* Pagination */}
      {totalPages > 1 && (
        <div className="flex justify-end items-center gap-2 mt-4 text-sm text-muted-foreground">
          <button
            className="px-2 py-1 rounded border hover:bg-muted disabled:opacity-50 disabled:cursor-not-allowed"
            disabled={page <= 1}
            onClick={() => setPage(p => Math.max(1, p - 1))}
          >
            ← Назад
          </button>
          <span>
            {page} / {totalPages}
          </span>
          <button
            className="px-2 py-1 rounded border hover:bg-muted disabled:opacity-50 disabled:cursor-not-allowed"
            disabled={page >= totalPages}
            onClick={() => setPage(p => Math.min(totalPages, p + 1))}
          >
            Вперед →
          </button>
        </div>
      )}

      <AlertDialog open={deleteConfirmId !== null} onOpenChange={(open) => { if (!open) setDeleteConfirmId(null) }}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Удалить документ?</AlertDialogTitle>
            <AlertDialogDescription>
              Это действие нельзя отменить.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel onClick={() => setDeleteConfirmId(null)}>
              Отмена
            </AlertDialogCancel>
            <AlertDialogAction
              variant="destructive"
              onClick={handleDeleteConfirm}
            >
              Удалить
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      <CreateDocumentModal
        open={createOpen}
        onClose={() => {
          setCreateOpen(false)
          loadDocuments(page)
        }}
      />
    </div>
  )
}
