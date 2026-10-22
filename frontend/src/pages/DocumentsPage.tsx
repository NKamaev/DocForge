import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
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
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
  Input,
} from '@/components/ui'
import { Edit, Trash2, Share2 } from 'lucide-react'
import { documentsApi } from '../api/documentsApi'
import { foldersApi } from '../api/foldersApi'
import type { DocumentListItem } from '../types/document'
import type { Folder } from '../types/folder'
import type { MemberRole } from '../types/member'
import { roleImplies } from '../types/member'
import { useMyFolderRole } from '../hooks/useMyRole'
import CreateDocumentModal from '../components/document-list/CreateDocumentModal'
import ShareDialog from '../components/ShareDialog'
import { Icon } from '../components/Icon'
import { showToast } from '@/components/ui/toast'

function showNotification(type: 'success' | 'error' | 'warning', message: string) {
  // User-visible toast (app-wide mechanism, see BUG-029).
  showToast(message, type)
}

/** Strip characters that are invalid in a download filename. */
function sanitizeFileName(name: string): string {
  return name.replace(/[/\\:*?"<>|]/g, '_').replace(/\s+/g, ' ').trim() || 'document'
}

/** Build a unique `*.docx` name, disambiguating collisions with the document id. */
function uniqueDocxName(name: string, id: string, used: Set<string>): string {
  const base = sanitizeFileName(name)
  let candidate = `${base}.docx`
  if (used.has(candidate.toLowerCase())) candidate = `${base} (${id}).docx`
  used.add(candidate.toLowerCase())
  return candidate
}

// ── Color presets ─────────────────────────────────────────────────────────────

const PRESET_COLORS = [
  '#1677ff', '#52c41a', '#fa8c16', '#f5222d',
  '#722ed1', '#13c2c2', '#eb2f96', '#8c8c8c',
]

// ── Role badge ────────────────────────────────────────────────────────────────

function RoleBadge({ role }: { role: MemberRole | null }) {
  if (!role || role === 'OWNER') return null
  const config: Record<string, { className: string; label: string }> = {
    VIEWER: { className: 'bg-blue-100 text-blue-700', label: 'Просмотр' },
    EDITOR: { className: 'bg-green-100 text-green-700', label: 'Редактор' },
    MANAGER: { className: 'bg-orange-100 text-orange-700', label: 'Менеджер' },
  }
  const c = config[role]
  return c ? (
    <Badge variant="outline" className={`ml-1 text-[11px] pointer-events-none ${c.className}`}>
      {c.label}
    </Badge>
  ) : null
}

// ── FolderColorModal ──────────────────────────────────────────────────────────

function FolderColorModal({
  open, current, onOk, onCancel,
}: {
  open: boolean; current: string | null
  onOk: (color: string) => void; onCancel: () => void
}) {
  const [selected, setSelected] = useState(current ?? PRESET_COLORS[0])
  useEffect(() => { setSelected(current ?? PRESET_COLORS[0]) }, [current, open])

  return (
    <Dialog open={open} onOpenChange={(o) => !o && onCancel()}>
      <DialogContent className="sm:max-w-[320px]">
        <DialogHeader>
          <DialogTitle>Цвет папки</DialogTitle>
        </DialogHeader>
        <div className="flex gap-2.5 flex-wrap py-3">
          {PRESET_COLORS.map(c => (
            <button
              key={c}
              type="button"
              onClick={() => setSelected(c)}
              className="rounded-full transition-all"
              style={{
                width: 32, height: 32, background: c, cursor: 'pointer',
                border: selected === c ? '3px solid #000' : '2px solid transparent',
                outline: 'none',
              }}
            />
          ))}
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={onCancel}>Отмена</Button>
          <Button onClick={() => onOk(selected)}>Сохранить</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

// ── FolderNameModal ───────────────────────────────────────────────────────────

function FolderNameModal({
  open, title, initialName, onOk, onCancel,
}: {
  open: boolean; title: string; initialName: string
  onOk: (name: string) => void; onCancel: () => void
}) {
  const [name, setName] = useState(initialName)
  useEffect(() => { setName(initialName) }, [initialName, open])

  const handleOk = () => { if (name.trim()) onOk(name.trim()) }

  return (
    <Dialog open={open} onOpenChange={(o) => !o && onCancel()}>
      <DialogContent className="sm:max-w-[380px]">
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
        </DialogHeader>
        <div className="mt-3">
          <Input
            value={name}
            onChange={e => setName(e.target.value)}
            onKeyDown={e => { if (e.key === 'Enter') handleOk() }}
            autoFocus
          />
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={onCancel}>Отмена</Button>
          <Button onClick={handleOk}>Сохранить</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

// ── Shared folder action callbacks type ──────────────────────────────────────

interface FolderCallbacks {
  onRename: (id: string) => void
  onChangeColor: (id: string) => void
  onDeleteRequest: (id: string) => void
  onShare: (folder: Folder) => void
}

// ── FolderSidebarItem ─────────────────────────────────────────────────────────

interface FolderSidebarItemProps extends FolderCallbacks {
  folder: Folder
  allFolders: Folder[]
  depth: number
  currentFolder: string | null
  expandedFolders: Set<string>
  dragOverFolder: string | null
  onOpenFolder: (id: string) => void
  onToggleExpand: (id: string) => void
  onDragOver: (id: string) => void
  onDragLeave: (e: React.DragEvent) => void
  onDrop: (id: string) => void
}

function FolderSidebarItem({
  folder, allFolders, depth, currentFolder, expandedFolders, dragOverFolder,
  onOpenFolder, onToggleExpand, onDragOver, onDragLeave, onDrop,
  onRename, onChangeColor, onDeleteRequest, onShare,
}: FolderSidebarItemProps) {
  const myRole = useMyFolderRole(folder.id)
  const canManage = myRole === null || roleImplies(myRole, 'manage')
  const canShare  = myRole === null || roleImplies(myRole, 'share')
  const canDelete = myRole === null || roleImplies(myRole, 'delete')

  const children = allFolders.filter(f => f.parentId === folder.id)
  const hasChildren = children.length > 0
  const isExpanded = expandedFolders.has(folder.id)
  const indent = 8 + depth * 16

  return (
    <div>
      <div
        className={'tree-row' + (currentFolder === folder.id ? ' current' : '') + (dragOverFolder === folder.id ? ' drag-over' : '')}
        style={{ paddingLeft: indent }}
        onClick={() => onOpenFolder(folder.id)}
        onDragOver={e => { e.preventDefault(); onDragOver(folder.id) }}
        onDragLeave={e => { if (!e.currentTarget.contains(e.relatedTarget as Node)) onDragLeave(e) }}
        onDrop={() => onDrop(folder.id)}
      >
        <span
          className="tree-tri"
          style={{ visibility: hasChildren ? 'visible' : 'hidden', cursor: 'pointer', transform: isExpanded ? 'rotate(90deg)' : undefined }}
          onClick={e => { e.stopPropagation(); onToggleExpand(folder.id) }}
        >
          <Icon name="chev-right" size={10} />
        </span>
        <span className="tree-folder-icon" style={{ color: folder.color ?? '#1677ff', pointerEvents: 'none' }}>
          <Icon name="folder" size={14} />
        </span>
        <span className="tree-label" style={{ pointerEvents: 'none' }}>{folder.name}</span>
        <RoleBadge role={myRole} />
        <span className="tree-count" style={{ pointerEvents: 'none' }}>{folder.documentCount}</span>

        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <button className="btn xs icon-only tree-more" onClick={e => e.stopPropagation()} title="Действия">
              <Icon name="more-h" size={12} />
            </button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end">
            {canManage && (
              <>
                <DropdownMenuItem onClick={() => onRename(folder.id)}>
                  <Edit className="mr-2 size-3.5" /> Переименовать
                </DropdownMenuItem>
                <DropdownMenuItem onClick={() => onChangeColor(folder.id)}>
                  <Icon name="folder" size={14} /> Изменить цвет
                </DropdownMenuItem>
              </>
            )}
            {canShare && (
              <DropdownMenuItem onClick={() => onShare(folder)}>
                <Share2 className="mr-2 size-3.5" /> Поделиться
              </DropdownMenuItem>
            )}
            {(canManage || canShare) && canDelete && <DropdownMenuSeparator />}
            {canDelete && (
              <>
                <DropdownMenuItem
                  className="text-destructive focus:text-destructive"
                  onClick={(e) => { e.stopPropagation(); onDeleteRequest(folder.id) }}
                >
                  <Trash2 className="mr-2 size-3.5" /> Удалить
                </DropdownMenuItem>
              </>
            )}
          </DropdownMenuContent>
        </DropdownMenu>
      </div>
      {isExpanded && hasChildren && children.map(child => (
        <FolderSidebarItem
          key={child.id}
          folder={child}
          allFolders={allFolders}
          depth={depth + 1}
          currentFolder={currentFolder}
          expandedFolders={expandedFolders}
          dragOverFolder={dragOverFolder}
          onOpenFolder={onOpenFolder}
          onToggleExpand={onToggleExpand}
          onDragOver={onDragOver}
          onDragLeave={onDragLeave}
          onDrop={onDrop}
          onRename={onRename}
          onChangeColor={onChangeColor}
          onDeleteRequest={onDeleteRequest}
          onShare={onShare}
        />
      ))}
    </div>
  )
}

// ── FolderTableRow ────────────────────────────────────────────────────────────

interface FolderTableRowProps extends FolderCallbacks {
  folder: Folder
  selected: Set<string>
  onToggleSel: (id: string) => void
  onOpenFolder: (id: string) => void
}

function FolderTableRow({
  folder, selected, onToggleSel, onOpenFolder,
  onRename, onChangeColor, onDeleteRequest, onShare,
}: FolderTableRowProps) {
  const myRole = useMyFolderRole(folder.id)
  const canManage = myRole === null || roleImplies(myRole, 'manage')
  const canShare  = myRole === null || roleImplies(myRole, 'share')
  const canDelete = myRole === null || roleImplies(myRole, 'delete')

  return (
    <tr className="row-folder">
      <td onClick={e => e.stopPropagation()}>
        <input type="checkbox" checked={selected.has('f:' + folder.id)}
          onChange={() => onToggleSel('f:' + folder.id)} />
      </td>
      <td style={{ cursor: 'pointer' }} onClick={() => onOpenFolder(folder.id)}>
        <div style={{ display: 'flex', gap: 10, alignItems: 'center' }}>
          <span style={{
            width: 28, height: 28, borderRadius: 6,
            background: `${folder.color ?? '#1677ff'}22`,
            color: folder.color ?? '#1677ff',
            display: 'grid', placeItems: 'center', flexShrink: 0,
          }}>
            <Icon name="folder" size={15} />
          </span>
          <span style={{ fontWeight: 500 }}>{folder.name}</span>
          <RoleBadge role={myRole} />
        </div>
      </td>
      <td className="muted">Папка</td>
      <td className="muted">{folder.documentCount} докум.</td>
      <td className="muted">—</td>
      <td>
        <div className="row-actions">
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <button className="btn xs icon-only" onClick={e => e.stopPropagation()}>
                <Icon name="more-h" size={14} />
              </button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              {canManage && (
                <>
                  <DropdownMenuItem onClick={() => onRename(folder.id)}>
                    <Edit className="mr-2 size-3.5" /> Переименовать
                  </DropdownMenuItem>
                  <DropdownMenuItem onClick={() => onChangeColor(folder.id)}>
                    <Icon name="folder" size={14} /> Изменить цвет
                  </DropdownMenuItem>
                </>
              )}
              {canShare && (
                <DropdownMenuItem onClick={() => onShare(folder)}>
                  <Share2 className="mr-2 size-3.5" /> Поделиться
                </DropdownMenuItem>
              )}
              {(canManage || canShare) && canDelete && <DropdownMenuSeparator />}
              {canDelete && (
                <>
                <DropdownMenuItem
                  className="text-destructive focus:text-destructive"
                  onClick={(e) => { e.stopPropagation(); onDeleteRequest(folder.id) }}
                >
                  <Trash2 className="mr-2 size-3.5" /> Удалить
                </DropdownMenuItem>
                </>
              )}
            </DropdownMenuContent>
          </DropdownMenu>
        </div>
      </td>
    </tr>
  )
}

// ── Main page ─────────────────────────────────────────────────────────────────

export default function DocumentsPage() {
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()
  const [docs, setDocs] = useState<DocumentListItem[]>([])
  const [folders, setFolders] = useState<Folder[]>([])
  const [loadingDocs, setLoadingDocs] = useState(false)
  const [currentFolder, setCurrentFolder] = useState<string | null>(() => searchParams.get('folder') ?? null)
  const [selected, setSelected] = useState<Set<string>>(new Set())
  const [view, setView] = useState<'list' | 'grid'>('list')
  const [createOpen, setCreateOpen] = useState(false)

  const [expandedFolders, setExpandedFolders] = useState<Set<string>>(new Set())
  const [createFolderOpen, setCreateFolderOpen] = useState(false)
  const [renameFolderOpen, setRenameFolderOpen] = useState(false)
  const [colorFolderOpen, setColorFolderOpen] = useState(false)
  const [activeFolderId, setActiveFolderId] = useState<string | null>(null)

  const [shareOpen, setShareOpen] = useState(false)
  const [shareFolder, setShareFolder] = useState<Folder | null>(null)
  const [shareMyRole, setShareMyRole] = useState<MemberRole>('OWNER')

  const [deleteDocId, setDeleteDocId] = useState<string | null>(null)
  const [deleteFolderId, setDeleteFolderId] = useState<string | null>(null)

  const [bulkDeleteOpen, setBulkDeleteOpen] = useState(false)
  const [bulkDownloading, setBulkDownloading] = useState(false)
  const [bulkDeleting, setBulkDeleting] = useState(false)
  const [bulkProgress, setBulkProgress] = useState<{ done: number; total: number } | null>(null)

  const dragDocId = useRef<string | null>(null)
  const [dragOverFolder, setDragOverFolder] = useState<string | null>(null)

  const activeFolder = folders.find(f => f.id === activeFolderId) ?? null
  const currentFolderObj = folders.find(f => f.id === currentFolder) ?? null

  const folderPath = useMemo(() => {
    if (!currentFolder) return []
    const path: Folder[] = []
    let cur: Folder | undefined = folders.find(f => f.id === currentFolder)
    while (cur) {
      path.unshift(cur)
      cur = cur.parentId ? folders.find(f => f.id === cur!.parentId) : undefined
    }
    return path
  }, [currentFolder, folders])

  const loadDocs = useCallback(async () => {
    setLoadingDocs(true)
    try {
      const res = await documentsApi.list(0, 200)
      setDocs(res.content)
    } catch {
      showNotification('error', 'Не удалось загрузить документы')
    } finally {
      setLoadingDocs(false)
    }
  }, [])

  const loadFolders = useCallback(async () => {
    try { setFolders(await foldersApi.list()) }
    catch { showNotification('error', 'Не удалось загрузить папки') }
  }, [])

  useEffect(() => { loadDocs(); loadFolders() }, [loadDocs, loadFolders])

  // Sync currentFolder from URL when browser back/forward changes searchParams
  useEffect(() => {
    const folderParam = searchParams.get('folder') ?? null
    if (folderParam !== currentFolder) setCurrentFolder(folderParam)
  }, [searchParams])

  // Bulk selection is scoped to the visible folder. Clear it whenever the folder
  // changes — including browser back/forward, which bypasses `openFolder`.
  useEffect(() => { setSelected(new Set()) }, [currentFolder])

  // Expand ancestors for folder from URL on initial load
  const initialUrlFolderExpanded = useRef(false)
  useEffect(() => {
    if (initialUrlFolderExpanded.current || !currentFolder || folders.length === 0) return
    initialUrlFolderExpanded.current = true
    const ancestors: string[] = []
    let cur: Folder | undefined = folders.find(f => f.id === currentFolder)
    while (cur?.parentId) {
      ancestors.push(cur.parentId)
      cur = folders.find(f => f.id === cur!.parentId)
    }
    if (ancestors.length) {
      setExpandedFolders(prev => { const s = new Set(prev); ancestors.forEach(a => s.add(a)); return s })
    }
  }, [currentFolder, folders])

  // ── Folder CRUD ─────────────────────────────────────────────────────────────

  const handleCreateFolder = async (name: string) => {
    setCreateFolderOpen(false)
    try { await foldersApi.create({ name, color: PRESET_COLORS[0], parentId: currentFolder }); loadFolders() }
    catch { showNotification('error', 'Не удалось создать папку') }
  }

  const handleRenameFolder = async (name: string) => {
    setRenameFolderOpen(false)
    if (!activeFolder) return
    try { await foldersApi.update(activeFolder.id, { name, color: activeFolder.color }); loadFolders() }
    catch { showNotification('error', 'Не удалось переименовать папку') }
  }

  const handleChangeColor = async (color: string) => {
    setColorFolderOpen(false)
    if (!activeFolder) return
    try { await foldersApi.update(activeFolder.id, { name: activeFolder.name, color }); loadFolders() }
    catch { showNotification('error', 'Не удалось изменить цвет') }
  }

  const openFolder = (id: string | null) => {
    setCurrentFolder(id)
    const next = new URLSearchParams(searchParams)
    id ? next.set('folder', id) : next.delete('folder')
    setSearchParams(next)
    if (id) {
      const ancestors: string[] = []
      let cur: Folder | undefined = folders.find(f => f.id === id)
      while (cur?.parentId) {
        ancestors.push(cur.parentId)
        cur = folders.find(f => f.id === cur!.parentId)
      }
      if (ancestors.length) setExpandedFolders(prev => { const s = new Set(prev); ancestors.forEach(a => s.add(a)); return s })
    }
  }

  const handleDeleteFolder = async (id: string) => {
    try {
      await foldersApi.delete(id)
      if (currentFolder === id) openFolder(null)
      loadFolders(); loadDocs()
    } catch { showNotification('error', 'Не удалось удалить папку') }
  }

  // ── Folder action callbacks ──────────────────────────────────────────────────

  const handleFolderRename = useCallback((id: string) => {
    setActiveFolderId(id)
    setRenameFolderOpen(true)
  }, [])

  const handleFolderColor = useCallback((id: string) => {
    setActiveFolderId(id)
    setColorFolderOpen(true)
  }, [])

  const handleFolderShare = useCallback((folder: Folder, role: MemberRole) => {
    setShareFolder(folder)
    setShareMyRole(role)
    setShareOpen(true)
  }, [])

  // ── Drag-and-drop ───────────────────────────────────────────────────────────

  const handleDropOnFolder = async (folderId: string | null) => {
    setDragOverFolder(null)
    const docId = dragDocId.current
    dragDocId.current = null
    if (!docId) return
    try { await documentsApi.moveToFolder(docId, folderId); loadDocs(); loadFolders() }
    catch { showNotification('error', 'Не удалось переместить документ') }
  }

  // ── Misc ─────────────────────────────────────────────────────────────────────

  const handleDeleteDoc = async () => {
    if (!deleteDocId) return
    try {
      await documentsApi.delete(deleteDocId)
      showNotification('success', 'Документ удалён')
      loadDocs(); loadFolders()
    } catch { showNotification('error', 'Не удалось удалить документ') }
    finally { setDeleteDocId(null) }
  }

  const toggleSel = (id: string) => setSelected(prev => {
    const next = new Set(prev); next.has(id) ? next.delete(id) : next.add(id); return next
  })

  // ── Bulk actions (BUG-049) ───────────────────────────────────────────────────

  /** Selection keys that are folders (`f:<id>`) are out of scope for bulk actions. */
  const selectedDocIds = useMemo(
    () => Array.from(selected).filter(id => !id.startsWith('f:')),
    [selected],
  )

  const docsById = useMemo(() => new Map(docs.map(d => [d.id, d])), [docs])

  const handleBulkDownload = useCallback(async () => {
    if (bulkDownloading || bulkDeleting) return
    if (selectedDocIds.length === 0) return

    const total = selectedDocIds.length
    setBulkDownloading(true)
    setBulkProgress({ done: 0, total })

    // Each export is independent: one failing document must not abort the rest,
    // so the failure is collected per item instead of thrown.
    const usedNames = new Set<string>()
    const failed: string[] = []
    let downloaded = 0

    try {
      for (const id of selectedDocIds) {
        try {
          const blob = await documentsApi.exportDocx(id)
          const url = URL.createObjectURL(blob)
          const a = document.createElement('a')
          a.href = url
          a.download = uniqueDocxName(docsById.get(id)?.name ?? 'document', id, usedNames)
          // The anchor must be attached to the DOM for the click to be honoured
          // by Firefox/Safari.
          document.body.appendChild(a)
          a.click()
          document.body.removeChild(a)
          // Revoking synchronously can cancel the download in some browsers.
          setTimeout(() => URL.revokeObjectURL(url), 0)
          downloaded += 1
        } catch {
          failed.push(id)
        } finally {
          setBulkProgress({ done: downloaded + failed.length, total })
        }
      }

      if (failed.length === 0) {
        showNotification(
          'success',
          total === 1 ? 'Документ экспортирован' : `Экспортировано документов: ${downloaded}`,
        )
      } else if (downloaded === 0) {
        showNotification('error', 'Не удалось скачать документы')
      } else {
        showNotification(
          'warning',
          `Скачано: ${downloaded} из ${total}. Не удалось скачать: ${failed.length}`,
        )
      }
    } finally {
      setBulkDownloading(false)
      setBulkProgress(null)
    }
  }, [bulkDownloading, bulkDeleting, selectedDocIds, docsById])

  const handleBulkDelete = useCallback(async () => {
    if (bulkDeleting || bulkDownloading) return
    if (selectedDocIds.length === 0) return
    setBulkDeleting(true)

    // Partial-failure resilience: every id is deleted on its own, so one failure
    // neither aborts the remaining deletes nor hides the ones that succeeded.
    // The outcome is collected (not thrown) so the `finally` block below can
    // always reconcile the selection and refresh the list.
    const succeeded = new Set<string>()
    const failed: string[] = []

    try {
      for (const id of selectedDocIds) {
        try {
          await documentsApi.delete(id)
          succeeded.add(id)
        } catch {
          failed.push(id)
        }
      }

      const total = selectedDocIds.length
      if (failed.length === 0) {
        showNotification(
          'success',
          total === 1 ? 'Документ удалён' : `Удалено документов: ${succeeded.size}`,
        )
      } else if (succeeded.size === 0) {
        showNotification('error', 'Не удалось удалить документы')
      } else {
        showNotification(
          'warning',
          `Удалено: ${succeeded.size} из ${total}. Не удалось удалить: ${failed.length}`,
        )
      }
    } finally {
      // Always drop the ids that were actually deleted from the selection, so the
      // bulk bar count stays truthful after a partial failure. The failed ids stay
      // selected and can be retried by the user.
      if (succeeded.size > 0) {
        setSelected(prev => {
          const next = new Set(prev)
          succeeded.forEach(id => next.delete(id))
          return next
        })
      }

      // Always reload, even when some deletes failed — folders/counters may have
      // shifted. Inner try/finally guarantees the dialog never gets stuck open.
      try {
        await Promise.all([loadDocs(), loadFolders()])
      } finally {
        setBulkDeleting(false)
        setBulkDeleteOpen(false)
      }
    }
  }, [bulkDeleting, bulkDownloading, selectedDocIds, loadDocs, loadFolders])

  const visibleDocs = useMemo(
    () => currentFolder === null
      ? docs.filter(d => !d.folderId)
      : docs.filter(d => d.folderId === currentFolder),
    [docs, currentFolder],
  )
  const visibleFolders = currentFolder === null
    ? folders.filter(f => !f.parentId)
    : folders.filter(f => f.parentId === currentFolder)

  const statusProgress = (s: string) => s === 'EXPORTED' ? 100 : s === 'GENERATED' ? 75 : 30
  const isComplete = (s: string) => s === 'EXPORTED'

  // ── Sidebar ─────────────────────────────────────────────────────────────────

  const sidebar = (
    <aside className="docs-tree">
      <div className="docs-tree-head">
        <span>Папки</span>
        <button className="btn xs ghost" title="Создать папку" onClick={() => setCreateFolderOpen(true)}>
          <Icon name="folder-plus" size={13} />
        </button>
      </div>

      <div
        className={'tree-row' + (currentFolder === null ? ' current' : '') + (dragOverFolder === '__root__' ? ' drag-over' : '')}
        style={{ paddingLeft: 8 }}
        onClick={() => openFolder(null)}
        onDragOver={e => { e.preventDefault(); setDragOverFolder('__root__') }}
        onDragLeave={e => { if (!e.currentTarget.contains(e.relatedTarget as Node)) setDragOverFolder(null) }}
        onDrop={() => handleDropOnFolder(null)}
      >
        <span className="tree-tri" style={{ visibility: 'hidden' }} />
        <span className="tree-folder-icon" style={{ color: 'var(--ink-3)', pointerEvents: 'none' }}><Icon name="home" size={13} /></span>
        <span className="tree-label" style={{ pointerEvents: 'none' }}>Все документы</span>
        <span className="tree-count" style={{ pointerEvents: 'none' }}>{docs.length}</span>
      </div>

      <div className="tree-divider" />

      {folders.filter(f => !f.parentId).map(folder => (
        <FolderSidebarItem
          key={folder.id}
          folder={folder}
          allFolders={folders}
          depth={0}
          currentFolder={currentFolder}
          expandedFolders={expandedFolders}
          dragOverFolder={dragOverFolder}
          onOpenFolder={openFolder}
          onToggleExpand={id => setExpandedFolders(prev => { const s = new Set(prev); s.has(id) ? s.delete(id) : s.add(id); return s })}
          onDragOver={id => setDragOverFolder(id)}
          onDragLeave={() => setDragOverFolder(null)}
          onDrop={handleDropOnFolder}
          onRename={handleFolderRename}
          onChangeColor={handleFolderColor}
          onDeleteRequest={setDeleteFolderId}
          onShare={folder => handleFolderShare(folder, 'OWNER')}
        />
      ))}
    </aside>
  )

  // ── Modals ───────────────────────────────────────────────────────────────────

  const modals = (
    <>
      <FolderNameModal open={createFolderOpen} title="Новая папка" initialName=""
        onOk={handleCreateFolder} onCancel={() => setCreateFolderOpen(false)} />
      <FolderNameModal open={renameFolderOpen} title="Переименовать папку"
        initialName={activeFolder?.name ?? ''}
        onOk={handleRenameFolder} onCancel={() => setRenameFolderOpen(false)} />
      <FolderColorModal open={colorFolderOpen} current={activeFolder?.color ?? null}
        onOk={handleChangeColor} onCancel={() => setColorFolderOpen(false)} />

      <AlertDialog open={deleteFolderId !== null} onOpenChange={(open) => { if (!open) setDeleteFolderId(null) }}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>
              Удалить папку «{folders.find(f => f.id === deleteFolderId)?.name ?? ''}»?
            </AlertDialogTitle>
            <AlertDialogDescription>
              Документы останутся, но перейдут в «Без папки».
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel onClick={() => setDeleteFolderId(null)}>Отмена</AlertDialogCancel>
            <AlertDialogAction variant="destructive" onClick={() => { if (deleteFolderId) handleDeleteFolder(deleteFolderId); setDeleteFolderId(null) }}>
              Удалить
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      <AlertDialog open={deleteDocId !== null} onOpenChange={(open) => { if (!open) setDeleteDocId(null) }}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Удалить документ?</AlertDialogTitle>
            <AlertDialogDescription>
              Это действие нельзя отменить.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel onClick={() => setDeleteDocId(null)}>Отмена</AlertDialogCancel>
            <AlertDialogAction variant="destructive" onClick={handleDeleteDoc}>
              Удалить
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      <AlertDialog open={bulkDeleteOpen} onOpenChange={(open) => { if (!open && !bulkDeleting) setBulkDeleteOpen(false) }}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>
              {selectedDocIds.length === 1
                ? 'Удалить выбранный документ?'
                : `Удалить выбранные документы (${selectedDocIds.length})?`}
            </AlertDialogTitle>
            <AlertDialogDescription>
              Это действие нельзя отменить.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={bulkDeleting} onClick={() => setBulkDeleteOpen(false)}>Отмена</AlertDialogCancel>
            <AlertDialogAction
              variant="destructive"
              disabled={bulkDeleting}
              // `AlertDialogAction` closes the dialog by default — preventDefault
              // keeps it open with the busy state until handleBulkDelete finishes.
              onClick={(e) => { e.preventDefault(); void handleBulkDelete() }}
            >
              {bulkDeleting ? 'Удаление…' : 'Удалить'}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      {shareFolder && (
        <ShareDialog
          open={shareOpen}
          onClose={() => setShareOpen(false)}
          resourceType="folder"
          resourceId={shareFolder.id}
          resourceName={shareFolder.name}
          myRole={shareMyRole}
        />
      )}
    </>
  )

  // ── Grid view ───────────────────────────────────────────────────────────────

  if (view === 'grid') {
    return (
      <div className="docs-page">
        {sidebar}
        <div className="docs-main">
          <div className="page-head" style={{ padding: '16px 20px 12px', flexShrink: 0 }}>
            <div style={{ minWidth: 0 }}>
              <div className="crumb">
                <Icon name="home" size={12} />
                <span onClick={() => navigate('/')} style={{ cursor: 'pointer' }}>Главная</span>
                <Icon name="chev-right" size={11} />
                <span onClick={() => openFolder(null)} style={{ cursor: 'pointer' }}>Мои документы</span>
                {folderPath.map((f, i) => (
                  <span key={f.id} style={{ display: 'contents' }}>
                    <Icon name="chev-right" size={11} />
                    {i < folderPath.length - 1
                      ? <span onClick={() => openFolder(f.id)} style={{ cursor: 'pointer' }}>{f.name}</span>
                      : <span>{f.name}</span>}
                  </span>
                ))}
              </div>
              <h1>{currentFolderObj ? currentFolderObj.name : 'Мои документы'}</h1>
            </div>
            <div className="page-head-actions">
              <div className="seg">
                <button className="seg-b" onClick={() => setView('list')} title="Список"><Icon name="list-ul" size={14} /></button>
                <button className="seg-b active" title="Плитка"><Icon name="templates" size={14} /></button>
              </div>
              <button className="btn primary" onClick={() => setCreateOpen(true)}><Icon name="plus" size={14} /> Создать документ</button>
            </div>
          </div>

          <div className="docs-grid" style={{ overflowY: 'auto', flex: 1 }}>
            {visibleFolders.map(f => (
              <div key={f.id} className="folder-card" onClick={() => openFolder(f.id)}>
                <div className="fc-icon" style={{ background: `${f.color ?? '#1677ff'}22`, color: f.color ?? '#1677ff' }}>
                  <Icon name="folder" size={22} />
                </div>
                <div className="fc-name">{f.name}</div>
                <div className="fc-meta">{f.documentCount} докум.</div>
              </div>
            ))}
            {visibleDocs.map(d => (
              <div key={d.id} className="doc-card" draggable onDragStart={() => { dragDocId.current = d.id }}
                onClick={() => navigate(`/documents/${d.id}/fill`)}>
                <div className="dc-icon" style={{ background: isComplete(d.status) ? 'var(--accent-green-soft)' : 'var(--brand-soft)', color: isComplete(d.status) ? 'oklch(0.42 0.12 150)' : 'var(--brand-ink)' }}>
                  <Icon name={isComplete(d.status) ? 'check' : 'doc'} size={16} />
                </div>
                <div className="dc-name">{d.name}</div>
                <div className="dc-foot">
                  <span className="muted tabular" style={{ fontSize: 11 }}>{new Date(d.createdAt).toLocaleDateString('ru')}</span>
                  <span className="muted tabular" style={{ fontSize: 11 }}>{statusProgress(d.status)}%</span>
                </div>
              </div>
            ))}
          </div>
        </div>
        <CreateDocumentModal open={createOpen} defaultFolderId={currentFolder} onClose={() => { setCreateOpen(false); loadDocs(); loadFolders() }} />
        {modals}
      </div>
    )
  }

  // ── List view ───────────────────────────────────────────────────────────────

  const allChecked = visibleDocs.length > 0 && visibleDocs.every(d => selected.has(d.id))

  return (
    <div className="docs-page">
      {sidebar}

      <div className="docs-main">
        <div className="page-head" style={{ padding: '16px 20px 12px', flexShrink: 0 }}>
          <div style={{ minWidth: 0 }}>
            <div className="crumb">
              <Icon name="home" size={12} />
              <span onClick={() => navigate('/')} style={{ cursor: 'pointer' }}>Главная</span>
              <Icon name="chev-right" size={11} />
              <span onClick={() => openFolder(null)} style={{ cursor: 'pointer' }}>Мои документы</span>
              {folderPath.map((f, i) => (
                <span key={f.id} style={{ display: 'contents' }}>
                  <Icon name="chev-right" size={11} />
                  {i < folderPath.length - 1
                    ? <span onClick={() => openFolder(f.id)} style={{ cursor: 'pointer' }}>{f.name}</span>
                    : <span>{f.name}</span>}
                </span>
              ))}
            </div>
            <h1>{currentFolderObj ? currentFolderObj.name : 'Мои документы'}</h1>
            <p>Перетаскивайте документы на папки в боковой панели для перемещения.</p>
          </div>
          <div className="page-head-actions">
            <div className="seg">
              <button className="seg-b active" title="Список"><Icon name="list-ul" size={14} /></button>
              <button className="seg-b" onClick={() => setView('grid')} title="Плитка"><Icon name="templates" size={14} /></button>
            </div>
            <button className="btn" onClick={() => setCreateFolderOpen(true)}><Icon name="folder-plus" size={14} /> Папка</button>
            <button className="btn primary" onClick={() => setCreateOpen(true)}><Icon name="plus" size={14} /> Создать документ</button>
          </div>
        </div>

        {selected.size > 0 && (
          <div className="bulk-bar" style={{ flexShrink: 0 }}>
            <span className="bulk-count">
              Выбрано: {selectedDocIds.length}
              {selected.size > selectedDocIds.length
                ? ` (папок: ${selected.size - selectedDocIds.length})`
                : ''}
            </span>
            <button
              className="btn xs"
              disabled={selectedDocIds.length === 0 || bulkDownloading || bulkDeleting}
              onClick={handleBulkDownload}
            >
              <Icon name="download" size={12} />{' '}
              {bulkDownloading
                ? bulkProgress
                  ? `Скачивание ${bulkProgress.done}/${bulkProgress.total}…`
                  : 'Скачивание…'
                : 'Скачать'}
            </button>
            <button
              className="btn xs danger"
              disabled={selectedDocIds.length === 0 || bulkDownloading || bulkDeleting}
              onClick={() => setBulkDeleteOpen(true)}
            >
              <Icon name="trash" size={12} /> Удалить
            </button>
            <button className="btn xs ghost" onClick={() => setSelected(new Set())} style={{ marginLeft: 'auto' }}>Снять выбор</button>
          </div>
        )}

        <div style={{ flex: 1, overflowY: 'auto', padding: '0 16px 16px' }}>
          <div className="tbl">
            <table>
              <thead>
                <tr>
                  <th style={{ width: 36 }}>
                    <input type="checkbox" aria-label="Выбрать все документы" checked={allChecked}
                      onChange={e => {
                        if (e.target.checked) setSelected(new Set(visibleDocs.map(d => d.id)))
                        else setSelected(new Set())
                      }} />
                  </th>
                  <th style={{ width: '40%' }}>Название</th>
                  <th>Статус</th>
                  <th>Прогресс</th>
                  <th>Дата создания</th>
                  <th style={{ width: 130 }}></th>
                </tr>
              </thead>
              <tbody>
                {visibleFolders.map(folder => (
                  <FolderTableRow
                    key={`f-${folder.id}`}
                    folder={folder}
                    selected={selected}
                    onToggleSel={toggleSel}
                    onOpenFolder={openFolder}
                    onRename={handleFolderRename}
                    onChangeColor={handleFolderColor}
                    onDeleteRequest={setDeleteFolderId}
                    onShare={folder => handleFolderShare(folder, 'OWNER')}
                  />
                ))}

                {loadingDocs && (
                  <tr><td colSpan={6} style={{ textAlign: 'center', padding: 32, color: 'var(--ink-3)' }}>Загрузка…</td></tr>
                )}

                {!loadingDocs && visibleFolders.length === 0 && visibleDocs.length === 0 && (
                  <tr>
                    <td colSpan={6}>
                      <div className="empty-folder">
                        <Icon name="folder" size={28} />
                        <div>Пусто. Создайте документ или новую папку.</div>
                      </div>
                    </td>
                  </tr>
                )}

                {visibleDocs.map(d => {
                  const sel = selected.has(d.id)
                  const progress = statusProgress(d.status)
                  const complete = isComplete(d.status)
                  return (
                    <tr key={d.id} className={sel ? 'selected' : ''} draggable
                      onDragStart={() => { dragDocId.current = d.id }}
                      style={{ cursor: 'grab' }}>
                      <td><input type="checkbox" checked={sel} onChange={() => toggleSel(d.id)} /></td>
                      <td>
                        <div style={{ display: 'flex', gap: 10, alignItems: 'center' }}>
                          <div style={{
                            width: 28, height: 28, borderRadius: 6, flexShrink: 0,
                            background: complete ? 'var(--accent-green-soft)' : 'var(--brand-soft)',
                            color: complete ? 'oklch(0.42 0.12 150)' : 'var(--brand-ink)',
                            display: 'grid', placeItems: 'center',
                          }}>
                            <Icon name={complete ? 'check' : 'doc'} size={14} />
                          </div>
                          <span style={{ fontWeight: 500, cursor: 'pointer' }}
                            onClick={() => navigate(`/documents/${d.id}/fill`)}>
                            {d.name}
                          </span>
                        </div>
                      </td>
                      <td>
                        {complete
                          ? <span className="tag green"><span className="dot" />Готово</span>
                          : d.status === 'GENERATED'
                            ? <span className="tag brand"><span className="dot" />Сгенерирован</span>
                            : <span className="tag amber"><span className="dot" />Черновик</span>}
                      </td>
                      <td>
                        <div style={{ display: 'flex', flexDirection: 'column', gap: 4, width: 140 }}>
                          <div style={{ fontSize: 11, display: 'flex', justifyContent: 'space-between' }}>
                            <span className="muted">{complete ? 'Готово' : 'В работе'}</span>
                            <span className="tabular muted">{progress}%</span>
                          </div>
                          <div className="q-progress" style={{ marginTop: 0 }}>
                            <div style={{ width: progress + '%', background: complete ? 'var(--accent-green)' : 'var(--brand)' }} />
                          </div>
                        </div>
                      </td>
                      <td className="muted tabular">{new Date(d.createdAt).toLocaleDateString('ru')}</td>
                      <td>
                        <div className="row-actions">
                          <button className="btn xs" onClick={() => navigate(`/documents/${d.id}/fill`)}>Открыть</button>
                          <button className="btn xs icon-only" onClick={(e) => { e.stopPropagation(); setDeleteDocId(d.id) }}>
                            <Icon name="trash" size={13} />
                          </button>
                        </div>
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
            <div className="tbl-foot">
              <span>Папок: {visibleFolders.length} · Документов: {visibleDocs.length}</span>
              <span className="muted">200 / стр.</span>
            </div>
          </div>
        </div>
      </div>

      <CreateDocumentModal open={createOpen} defaultFolderId={currentFolder}
        onClose={() => { setCreateOpen(false); loadDocs(); loadFolders() }} />
      {modals}
    </div>
  )
}
