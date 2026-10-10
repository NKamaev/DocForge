import { useEffect, useRef, useState, startTransition } from 'react'
import {
  Button,
  Badge,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
  Tooltip,
  TooltipContent,
  TooltipTrigger,
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
  Pagination,
  PaginationContent,
  PaginationItem,
  PaginationLink,
  PaginationNext,
  PaginationPrevious,
  showToast,
} from '@/components/ui'
import { Edit, Trash2, Plus, CheckCircle, CircleX } from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import type { TemplateListItem } from '../../types/template'
import { templatesApi } from '../../api/templatesApi'
import CreateTemplateModal from './CreateTemplateModal'

function showNotification(type: 'success' | 'error', message: string) {
  showToast(message, type)
}

export default function TemplateList() {
  const [items, setItems] = useState<TemplateListItem[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(0)
  const [pageSize, setPageSize] = useState(20)
  const [loading, setLoading] = useState(false)
  const [modalOpen, setModalOpen] = useState(false)
  const publishingRef = useRef(false)
  const [dialogState, setDialogState] = useState<{ type: 'publish' | 'delete'; template: TemplateListItem } | null>(null)
  const dialogOpen = dialogState !== null
  const dialogType = dialogState?.type
  const dialogTemplate = dialogState?.template
  const navigate = useNavigate()

  const load = async (p = page, s = pageSize) => {
    setLoading(true)
    try {
      const data = await templatesApi.getTemplates({ page: p, size: s })
      setItems(data.content)
      setTotal(data.totalElements)
    } catch {
      showNotification('error', 'Не удалось загрузить шаблоны')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { load() }, []) // eslint-disable-line react-hooks/exhaustive-deps

  const handlePageChange = (newPage: number) => {
    const p = newPage - 1
    setPage(p)
    load(p, pageSize)
  }

  const handlePageSizeChange = (newSize: string) => {
    const s = parseInt(newSize, 10)
    setPageSize(s)
    setPage(0)
    load(0, s)
  }

  const handlePublish = async (template: TemplateListItem) => {
    if (publishingRef.current) return
    publishingRef.current = true
    try {
      const updated = template.published
        ? await templatesApi.unpublishTemplate(template.id)
        : await templatesApi.publishTemplate(template.id)

      startTransition(() => {
        setItems(prev => prev.map(t =>
          t.id === updated.id
            ? { ...t, published: updated.published as boolean }
            : t
        ))
      })
      showToast(template.published ? 'Шаблон снят с публикации — создание документов закрыто' : 'Шаблон опубликован — теперь можно создавать документы', 'success')
    } catch (err: unknown) {
      const detail = (err as { response?: { data?: { detail?: string } } })?.response?.data?.detail
      showToast(detail ?? (template.published ? 'Не удалось снять шаблон с публикации' : 'Не удалось опубликовать шаблон'), 'error')
    } finally {
      publishingRef.current = false
    }
  }

  const handleDelete = async (template: TemplateListItem) => {
    try {
      await templatesApi.deleteTemplate(template.id)
      startTransition(() => {
        setItems(prev => prev.filter(t => t.id !== template.id))
      })
      showToast('Шаблон удалён', 'success')
    } catch (err: any) {
      const detail = err?.response?.data?.detail
      showToast(detail ?? 'Не удалось удалить шаблон', 'error')
    }
  }

  const handleDialogConfirm = async () => {
    if (!dialogState) return
    const { type, template } = dialogState
    if (type === 'publish') {
      await handlePublish(template)
    } else {
      await handleDelete(template)
    }
    setDialogState(null)
  }

  const totalPages = Math.ceil(total / pageSize)

  return (
    <div className="p-6">
      <div className="flex justify-between items-center mb-4">
        <h3 className="text-lg font-semibold m-0">Шаблоны</h3>
        <Button onClick={() => setModalOpen(true)}>
          <Plus className="size-4 mr-1" />
          Создать шаблон
        </Button>
      </div>

      {loading ? (
        <div className="text-center py-8 text-muted-foreground">Загрузка...</div>
      ) : (
        <>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Название</TableHead>
                <TableHead>Категория</TableHead>
                <TableHead className="w-[140px]">Статус</TableHead>
                <TableHead className="w-[180px]">Дата создания</TableHead>
                <TableHead className="w-[110px] text-right">Действия</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {items.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={5} className="text-center text-muted-foreground py-8">
                    Нет шаблонов
                  </TableCell>
                </TableRow>
              ) : (
                items.map((item) => (
                  <TableRow key={item.id}>
                    <TableCell className="font-medium">{item.name}</TableCell>
                    <TableCell>{item.category ?? '—'}</TableCell>
                    <TableCell>
                      <Badge variant={item.published ? 'default' : 'secondary'}>
                        {item.published ? 'Опубликован' : 'Черновик'}
                      </Badge>
                    </TableCell>
                    <TableCell>
                      {new Date(item.createdAt).toLocaleDateString('ru-RU')}
                    </TableCell>
                    <TableCell className="text-right">
                      <div className="flex items-center justify-end gap-1">
                        <Tooltip>
                          <TooltipTrigger asChild>
                            <Button
                              variant="ghost"
                              size="icon-sm"
                              className="h-8 w-8"
                              onClick={() => navigate(`/templates/${item.id}/edit`)}
                            >
                              <Edit className="size-4" />
                            </Button>
                          </TooltipTrigger>
                          <TooltipContent>Открыть редактор</TooltipContent>
                        </Tooltip>

                        <Tooltip>
                          <TooltipTrigger asChild>
                            <Button
                              variant="ghost"
                              size="icon-sm"
                              className={`h-8 w-8 ${!item.published ? 'text-green-600' : ''}`}
                              onClick={() => setDialogState({ type: 'publish', template: item })}
                            >
                              {item.published ? <CircleX className="size-4" /> : <CheckCircle className="size-4" />}
                            </Button>
                          </TooltipTrigger>
                          <TooltipContent>
                            {item.published ? 'Снять с публикации' : 'Опубликовать шаблон'}
                          </TooltipContent>
                        </Tooltip>

                        <Tooltip>
                          <TooltipTrigger asChild>
                            <Button
                              variant="ghost"
                              size="icon-sm"
                              className="h-8 w-8 text-destructive"
                              onClick={() => setDialogState({ type: 'delete', template: item })}
                            >
                              <Trash2 className="size-4" />
                            </Button>
                          </TooltipTrigger>
                          <TooltipContent>Удалить шаблон</TooltipContent>
                        </Tooltip>
                      </div>
                    </TableCell>
                  </TableRow>
                ))
              )}
            </TableBody>
          </Table>

          {totalPages > 0 && (
            <div className="flex items-center justify-between mt-4">
              <div className="text-sm text-muted-foreground">
                Всего: {total}
              </div>
              <div className="flex items-center gap-4">
                <Select value={String(pageSize)} onValueChange={handlePageSizeChange}>
                  <SelectTrigger className="w-[100px]">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="10">10 на стр.</SelectItem>
                    <SelectItem value="20">20 на стр.</SelectItem>
                    <SelectItem value="50">50 на стр.</SelectItem>
                    <SelectItem value="100">100 на стр.</SelectItem>
                  </SelectContent>
                </Select>

                <Pagination>
                  <PaginationContent>
                    <PaginationItem>
                      <PaginationPrevious
                        onClick={() => page > 0 && handlePageChange(page)}
                        className={page === 0 ? 'opacity-50 pointer-events-none' : 'cursor-pointer'}
                      />
                    </PaginationItem>
                    {Array.from({ length: totalPages }, (_, i) => i + 1).slice(
                      Math.max(0, page - 2),
                      Math.min(totalPages, page + 3)
                    ).map((p) => (
                      <PaginationItem key={p}>
                        <PaginationLink
                          isActive={p === page + 1}
                          onClick={() => handlePageChange(p)}
                          className="cursor-pointer"
                        >
                          {p}
                        </PaginationLink>
                      </PaginationItem>
                    ))}
                    <PaginationItem>
                      <PaginationNext
                        onClick={() => page < totalPages - 1 && handlePageChange(page + 2)}
                        className={page >= totalPages - 1 ? 'opacity-50 pointer-events-none' : 'cursor-pointer'}
                      />
                    </PaginationItem>
                  </PaginationContent>
                </Pagination>
              </div>
            </div>
          )}
        </>
      )}

      <AlertDialog open={dialogOpen} onOpenChange={(open) => { if (!open) setDialogState(null) }}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>
              {dialogType === 'publish'
                ? (dialogTemplate?.published ? 'Снять с публикации?' : 'Опубликовать шаблон?')
                : 'Удалить шаблон?'}
            </AlertDialogTitle>
            <AlertDialogDescription>
              {dialogType === 'publish'
                ? (dialogTemplate?.published ? 'Шаблон перестанет быть доступен для создания новых документов.' : 'Шаблон станет доступен для просмотра и создания документов.')
                : 'Это действие нельзя отменить.'}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Отмена</AlertDialogCancel>
            <AlertDialogAction
              variant={dialogType === 'delete' ? 'destructive' : undefined}
              onClick={handleDialogConfirm}
            >
              {dialogType === 'publish'
                ? (dialogTemplate?.published ? 'Да, снять' : 'Опубликовать')
                : 'Удалить'}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      <CreateTemplateModal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        onCreated={() => { setModalOpen(false); load() }}
      />
    </div>
  )
}
