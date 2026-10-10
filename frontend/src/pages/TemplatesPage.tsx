import { useEffect, useRef, useState, startTransition } from 'react'
import { useNavigate } from 'react-router-dom'
import {
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
  showToast,
} from '@/components/ui'
import { templatesApi } from '../api/templatesApi'
import type { TemplateListItem } from '../types/template'
import CreateTemplateModal from '../components/template-list/CreateTemplateModal'
import { Icon } from '../components/Icon'

function showNotification(type: 'success' | 'error', message: string) {
  showToast(message, type)
}

type Filter = 'all' | 'published' | 'draft'

export default function TemplatesPage() {
  const navigate = useNavigate()
  const [items, setItems] = useState<TemplateListItem[]>([])
  const [loading, setLoading] = useState(false)
  const [filter, setFilter] = useState<Filter>('all')
  const [search, setSearch] = useState('')
  const [modalOpen, setModalOpen] = useState(false)
  const publishingRef = useRef(false)
  const [dialogState, setDialogState] = useState<{ type: 'publish' | 'delete'; template: TemplateListItem } | null>(null)
  const dialogOpen = dialogState !== null
  const dialogType = dialogState?.type
  const dialogTemplate = dialogState?.template

  const load = async () => {
    setLoading(true)
    try {
      const data = await templatesApi.getTemplates({ page: 0, size: 100 })
      setItems(data.content)
    } catch {
      showNotification('error', 'Не удалось загрузить шаблоны')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { load() }, [])

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
      showToast(template.published ? 'Шаблон снят с публикации' : 'Шаблон опубликован', 'success')
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
    } catch (err: unknown) {
      const detail = (err as { response?: { data?: { detail?: string } } })?.response?.data?.detail
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

  const published = items.filter((t) => t.published)
  const drafts = items.filter((t) => !t.published)

  const filtered = items
    .filter((t) => filter === 'all' || (filter === 'published' ? t.published : !t.published))
    .filter((t) => !search || t.name.toLowerCase().includes(search.toLowerCase()))

  return (
    <div className="page">
      <div className="page-head">
        <div>
          <div className="crumb">
            <Icon name="home" size={12} />
            <span onClick={() => navigate('/')} style={{ cursor: 'pointer' }}>Главная</span>
            <Icon name="chev-right" size={11} />
            <span>Шаблоны</span>
          </div>
          <h1>Шаблоны</h1>
          <p>Конструируйте, версионируйте и публикуйте многоразовые шаблоны для команды.</p>
        </div>
        <div className="page-head-actions">
          <button className="btn primary" onClick={() => setModalOpen(true)}>
            <Icon name="plus" size={14} /> Создать шаблон
          </button>
        </div>
      </div>

      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 14 }}>
        <div className="tabs pill">
          <div className={'tab' + (filter === 'all' ? ' active' : '')} onClick={() => setFilter('all')}>
            Все · {items.length}
          </div>
          <div className={'tab' + (filter === 'published' ? ' active' : '')} onClick={() => setFilter('published')}>
            Опубликованные · {published.length}
          </div>
          <div className={'tab' + (filter === 'draft' ? ' active' : '')} onClick={() => setFilter('draft')}>
            Черновики · {drafts.length}
          </div>
        </div>
        <div className="input-group" style={{ width: 240 }}>
          <span className="icon"><Icon name="search" size={14} /></span>
          <input
            placeholder="Поиск по названию"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        </div>
      </div>

      <div className="tbl">
        <table>
          <thead>
            <tr>
              <th style={{ width: '38%' }}>Название</th>
              <th>Категория</th>
              <th>Статус</th>
              <th>Дата создания</th>
              <th style={{ width: 160 }}></th>
            </tr>
          </thead>
          <tbody>
            {loading && (
              <tr><td colSpan={5} style={{ textAlign: 'center', padding: 32, color: 'var(--ink-3)' }}>Загрузка…</td></tr>
            )}
            {!loading && filtered.length === 0 && (
              <tr><td colSpan={5} style={{ textAlign: 'center', padding: 32, color: 'var(--ink-3)' }}>Нет шаблонов</td></tr>
            )}
            {filtered.map((t) => (
              <tr key={t.id}>
                <td>
                  <div style={{ display: 'flex', gap: 10, alignItems: 'center' }}>
                    <div style={{ width: 28, height: 28, borderRadius: 6, background: 'var(--bg-soft)', color: 'var(--ink-2)', display: 'grid', placeItems: 'center', border: '1px solid var(--line)', flexShrink: 0 }}>
                      <Icon name="doc" size={14} />
                    </div>
                    <div>
                      <div style={{ fontWeight: 500, cursor: 'pointer' }} onClick={() => navigate(`/templates/${t.id}/edit`)}>
                        {t.name}
                      </div>
                      <div style={{ fontSize: 11.5, color: 'var(--ink-3)' }}>
                        v{t.majorVersion ?? 1}.{t.minorVersion ?? 0}
                      </div>
                    </div>
                  </div>
                </td>
                <td>
                  {t.category ? <span className="tag">{t.category}</span> : <span className="muted">—</span>}
                </td>
                <td>
                  {t.published
                    ? <span className="tag green"><span className="dot" />Опубликован</span>
                    : <span className="tag amber"><span className="dot" />Черновик</span>}
                </td>
                <td className="muted tabular">{new Date(t.createdAt).toLocaleDateString('ru-RU')}</td>
                <td>
                  <div className="row-actions">
                    <Tooltip>
                      <TooltipTrigger asChild>
                        <button className="btn xs icon-only" onClick={() => navigate(`/templates/${t.id}/edit`)}>
                          <Icon name="edit" size={14} />
                        </button>
                      </TooltipTrigger>
                      <TooltipContent>Открыть редактор</TooltipContent>
                    </Tooltip>

                    <Tooltip>
                      <TooltipTrigger asChild>
                        <button
                          className={`btn xs icon-only ${t.published ? 'warning' : 'success'}`}
                          onClick={() => setDialogState({ type: 'publish', template: t })}
                        >
                          <Icon name={t.published ? 'x' : 'check'} size={14} />
                        </button>
                      </TooltipTrigger>
                      <TooltipContent>
                        {t.published ? 'Снять с публикации' : 'Опубликовать шаблон'}
                      </TooltipContent>
                    </Tooltip>

                    <Tooltip>
                      <TooltipTrigger asChild>
                        <button
                          className="btn xs icon-only danger"
                          onClick={() => setDialogState({ type: 'delete', template: t })}
                        >
                          <Icon name="trash" size={14} />
                        </button>
                      </TooltipTrigger>
                      <TooltipContent>Удалить шаблон</TooltipContent>
                    </Tooltip>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        <div className="tbl-foot">
          <span>Всего: {filtered.length}</span>
          <span style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <button className="btn xs icon-only" disabled><Icon name="chev-left" size={13} /></button>
            <span className="tabular">1 / 1</span>
            <button className="btn xs icon-only" disabled><Icon name="chev-right" size={13} /></button>
            <span style={{ color: 'var(--ink-3)', marginLeft: 8 }}>20 / стр.</span>
          </span>
        </div>
      </div>

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
                : 'Шаблон и все связанные с ним данные будут безвозвратно удалены.'}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Отмена</AlertDialogCancel>
            <AlertDialogAction
              className={dialogType === 'delete' ? 'bg-destructive hover:bg-destructive/90' : ''}
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
