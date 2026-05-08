import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Dropdown, Input, Modal, Popconfirm, Tag, notification } from 'antd'
import type { MenuProps } from 'antd'
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

// ── Color presets ─────────────────────────────────────────────────────────────

const PRESET_COLORS = [
  '#1677ff', '#52c41a', '#fa8c16', '#f5222d',
  '#722ed1', '#13c2c2', '#eb2f96', '#8c8c8c',
]

// ── Role badge ────────────────────────────────────────────────────────────────

function RoleBadge({ role }: { role: MemberRole | null }) {
  if (!role || role === 'OWNER') return null
  const config: Record<string, { color: string; label: string }> = {
    VIEWER: { color: 'blue', label: 'Просмотр' },
    EDITOR: { color: 'green', label: 'Редактор' },
    MANAGER: { color: 'orange', label: 'Менеджер' },
  }
  const c = config[role]
  return c ? <Tag color={c.color} style={{ marginLeft: 4, fontSize: 11, pointerEvents: 'none' }}>{c.label}</Tag> : null
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
    <Modal title="Цвет папки" open={open} onOk={() => onOk(selected)} onCancel={onCancel}
      okText="Сохранить" cancelText="Отмена" width={320} destroyOnHidden>
      <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap', padding: '12px 0' }}>
        {PRESET_COLORS.map(c => (
          <button key={c} onClick={() => setSelected(c)} style={{
            width: 32, height: 32, borderRadius: '50%', background: c, cursor: 'pointer',
            border: selected === c ? '3px solid #000' : '2px solid transparent', outline: 'none',
          }} />
        ))}
      </div>
    </Modal>
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

  return (
    <Modal title={title} open={open} onOk={() => { if (name.trim()) onOk(name.trim()) }}
      onCancel={onCancel} okText="Сохранить" cancelText="Отмена" width={380} destroyOnHidden>
      <Input value={name} onChange={e => setName(e.target.value)}
        onPressEnter={() => { if (name.trim()) onOk(name.trim()) }}
        autoFocus style={{ marginTop: 12 }} />
    </Modal>
  )
}

// ── Shared folder action callbacks type ──────────────────────────────────────

interface FolderCallbacks {
  onRename: (id: string) => void
  onChangeColor: (id: string) => void
  onDelete: (id: string) => void
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
  onRename, onChangeColor, onDelete, onShare,
}: FolderSidebarItemProps) {
  const myRole = useMyFolderRole(folder.id)
  const canManage = myRole === null || roleImplies(myRole, 'manage')
  const canShare  = myRole === null || roleImplies(myRole, 'share')
  const canDelete = myRole === null || roleImplies(myRole, 'delete')

  const children = allFolders.filter(f => f.parentId === folder.id)
  const hasChildren = children.length > 0
  const isExpanded = expandedFolders.has(folder.id)
  const indent = 8 + depth * 16

  const menuItems: MenuProps['items'] = [
    ...(canManage ? [
      { key: 'rename', label: 'Переименовать', onClick: () => onRename(folder.id) },
      { key: 'color', label: 'Изменить цвет', onClick: () => onChangeColor(folder.id) },
    ] : []),
    ...(canShare ? [
      { key: 'share', label: 'Поделиться', onClick: () => onShare(folder) },
    ] : []),
    ...((canManage || canShare) && canDelete ? [{ type: 'divider' as const }] : []),
    ...(canDelete ? [{
      key: 'delete',
      label: (
        <Popconfirm
          title={`Удалить папку «${folder.name}»?`}
          description="Документы останутся, но перейдут в «Без папки»."
          okText="Удалить" cancelText="Отмена" okButtonProps={{ danger: true }}
          onConfirm={() => onDelete(folder.id)}
          onPopupClick={e => e.stopPropagation()}
        >
          <span style={{ color: '#ff4d4f' }}>Удалить</span>
        </Popconfirm>
      ),
    }] : []),
  ]

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
        <Dropdown menu={{ items: menuItems }} trigger={['click']}>
          <button className="btn xs icon-only tree-more" onClick={e => e.stopPropagation()} title="Действия">
            <Icon name="more-h" size={12} />
          </button>
        </Dropdown>
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
          onDelete={onDelete}
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
  onRename, onChangeColor, onDelete, onShare,
}: FolderTableRowProps) {
  const myRole = useMyFolderRole(folder.id)
  const canManage = myRole === null || roleImplies(myRole, 'manage')
  const canShare  = myRole === null || roleImplies(myRole, 'share')
  const canDelete = myRole === null || roleImplies(myRole, 'delete')

  const menuItems: MenuProps['items'] = [
    ...(canManage ? [
      { key: 'rename', label: 'Переименовать', onClick: () => onRename(folder.id) },
      { key: 'color', label: 'Изменить цвет', onClick: () => onChangeColor(folder.id) },
    ] : []),
    ...(canShare ? [
      { key: 'share', label: 'Поделиться', onClick: () => onShare(folder) },
    ] : []),
    ...((canManage || canShare) && canDelete ? [{ type: 'divider' as const }] : []),
    ...(canDelete ? [{
      key: 'delete',
      label: (
        <Popconfirm
          title={`Удалить папку «${folder.name}»?`}
          description="Документы останутся, но перейдут в «Без папки»."
          okText="Удалить" cancelText="Отмена" okButtonProps={{ danger: true }}
          onConfirm={() => onDelete(folder.id)}
          onPopupClick={e => e.stopPropagation()}
        >
          <span style={{ color: '#ff4d4f' }}>Удалить</span>
        </Popconfirm>
      ),
    }] : []),
  ]

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
          <Dropdown menu={{ items: menuItems }} trigger={['click']}>
            <button className="btn xs icon-only" onClick={e => e.stopPropagation()}>
              <Icon name="more-h" size={14} />
            </button>
          </Dropdown>
        </div>
      </td>
    </tr>
  )
}

// ── Main page ─────────────────────────────────────────────────────────────────

export default function DocumentsPage() {
  const navigate = useNavigate()
  const [docs, setDocs] = useState<DocumentListItem[]>([])
  const [folders, setFolders] = useState<Folder[]>([])
  const [loadingDocs, setLoadingDocs] = useState(false)
  const [currentFolder, setCurrentFolder] = useState<string | null>(null)
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
      notification.error({ message: 'Не удалось загрузить документы' })
    } finally {
      setLoadingDocs(false)
    }
  }, [])

  const loadFolders = useCallback(async () => {
    try { setFolders(await foldersApi.list()) }
    catch { notification.error({ message: 'Не удалось загрузить папки' }) }
  }, [])

  useEffect(() => { loadDocs(); loadFolders() }, [loadDocs, loadFolders])

  // ── Folder CRUD ─────────────────────────────────────────────────────────────

  const handleCreateFolder = async (name: string) => {
    setCreateFolderOpen(false)
    try { await foldersApi.create({ name, color: PRESET_COLORS[0], parentId: currentFolder }); loadFolders() }
    catch { notification.error({ message: 'Не удалось создать папку' }) }
  }

  const handleRenameFolder = async (name: string) => {
    setRenameFolderOpen(false)
    if (!activeFolder) return
    try { await foldersApi.update(activeFolder.id, { name, color: activeFolder.color }); loadFolders() }
    catch { notification.error({ message: 'Не удалось переименовать папку' }) }
  }

  const handleChangeColor = async (color: string) => {
    setColorFolderOpen(false)
    if (!activeFolder) return
    try { await foldersApi.update(activeFolder.id, { name: activeFolder.name, color }); loadFolders() }
    catch { notification.error({ message: 'Не удалось изменить цвет' }) }
  }

  const openFolder = (id: string | null) => {
    setCurrentFolder(id)
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
      if (currentFolder === id) setCurrentFolder(null)
      loadFolders(); loadDocs()
    } catch { notification.error({ message: 'Не удалось удалить папку' }) }
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
    catch { notification.error({ message: 'Не удалось переместить документ' }) }
  }

  // ── Misc ─────────────────────────────────────────────────────────────────────

  const handleDelete = async (id: string) => {
    try {
      await documentsApi.delete(id)
      notification.success({ message: 'Документ удалён', duration: 2 })
      loadDocs(); loadFolders()
    } catch { notification.error({ message: 'Не удалось удалить документ' }) }
  }

  const toggleSel = (id: string) => setSelected(prev => {
    const next = new Set(prev); next.has(id) ? next.delete(id) : next.add(id); return next
  })

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
        onClick={() => setCurrentFolder(null)}
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
          onDelete={handleDeleteFolder}
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

  const allChecked = (visibleFolders.length + visibleDocs.length) > 0
    && visibleDocs.every(d => selected.has(d.id))

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
            <span className="bulk-count">Выбрано: {selected.size}</span>
            <button className="btn xs"><Icon name="download" size={12} /> Скачать</button>
            <button className="btn xs danger"><Icon name="trash" size={12} /> Удалить</button>
            <button className="btn xs ghost" onClick={() => setSelected(new Set())} style={{ marginLeft: 'auto' }}>Снять выбор</button>
          </div>
        )}

        <div style={{ flex: 1, overflowY: 'auto', padding: '0 16px 16px' }}>
          <div className="tbl">
            <table>
              <thead>
                <tr>
                  <th style={{ width: 36 }}>
                    <input type="checkbox" checked={allChecked}
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
                    onDelete={handleDeleteFolder}
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
                          <Popconfirm title="Удалить документ?" description="Это действие нельзя отменить."
                            okText="Удалить" cancelText="Отмена" okButtonProps={{ danger: true }}
                            onConfirm={() => handleDelete(d.id)}>
                            <button className="btn xs icon-only"><Icon name="trash" size={13} /></button>
                          </Popconfirm>
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
