import { useCallback, useEffect, useMemo, useRef, useState, type RefObject } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { EditorContent, useEditor } from '@tiptap/react'
import type { JSONContent } from '@tiptap/core'
import StarterKit from '@tiptap/starter-kit'
import { Table } from '@tiptap/extension-table'
import { TableRow } from '@tiptap/extension-table-row'
import { ExtendedTableCell, ExtendedTableHeader } from './extensions/TableCellExtension'
import { Strike } from '@tiptap/extension-strike'
import { Subscript } from '@tiptap/extension-subscript'
import { Superscript } from '@tiptap/extension-superscript'
import { Color } from '@tiptap/extension-color'
import { Highlight } from '@tiptap/extension-highlight'
import { FontFamily } from '@tiptap/extension-font-family'
import { TextStyle } from '@tiptap/extension-text-style'
import { TextAlign } from '@tiptap/extension-text-align'
import { Link } from '@tiptap/extension-link'
import { HorizontalRule } from '@tiptap/extension-horizontal-rule'
import { Image } from '@tiptap/extension-image'
import { ParameterFieldNode } from './extensions/ParameterFieldNode'
import { ConditionalBlockNode } from './extensions/ConditionalBlockNode'
import { NumberedClauseNode } from './extensions/NumberedClauseNode'
import { ConditionalInlineNode } from './extensions/ConditionalInlineNode'
import { ClauseRefNode } from './extensions/ClauseRefNode'
import { IndentExtension } from './extensions/IndentExtension'
import { FontSizeExtension } from './extensions/FontSizeExtension'
import { LineSpacingExtension } from './extensions/LineSpacingExtension'
import { ParagraphSpacingExtension } from './extensions/ParagraphSpacingExtension'
import { SearchReplaceExtension } from './extensions/SearchReplaceExtension'
import { PageBreakNode } from './extensions/PageBreakNode'
import { SlashCommandExtension, buildSlashCommands, type SlashCommand } from './extensions/SlashCommandExtension'
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
  Alert,
  AlertDescription,
  AlertTitle,
  Button,
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
  showToast,
} from '@/components/ui'
import { templatesApi } from '../../api/templatesApi'
import { foldersApi } from '../../api/foldersApi'
import { extractServerMessage, getErrorStatus } from '../../api/errorMessages'
import { canEditTemplate, getReadOnlyHint, getTemplateMutationRights } from '../../utils/templateAccess'
import { useAuthStore } from '../../store/authStore'
import { formattingToCssVars } from '../../utils/formattingCssVars'
import type { DocumentFormatting, Parameter, Phrasing, Questionnaire, Script, Template } from '../../types/template'
import type { Folder } from '../../types/folder'
import { DesignerContext } from './DesignerContext'
import EditorToolbar from './EditorToolbar'
import TableBubbleMenu from './TableBubbleMenu'
import TextBubbleMenu from './TextBubbleMenu'
import FloatingInsertMenu from './FloatingInsertMenu'
import ImageUploadModal from './ImageUploadModal'
import SearchReplacePanel from './SearchReplacePanel'
import PageSettingsDrawer from './PageSettingsDrawer'
import { useEditorContextMenu, ContextMenuPortal } from './EditorContextMenu'
import ParameterPanel from './ParameterPanel'
import QuestionnairePanel from './QuestionnairePanel'
import PhrasingsPanel from './PhrasingsPanel'
import { PhrasingsRefNode } from './extensions/PhrasingsRefNode'
import SlashCommandMenu from './SlashCommandMenu'
import SaveBumpDialog from './SaveBumpDialog'
import VersionHistoryPanel from './VersionHistoryPanel'
import { Icon } from '../Icon'

function showNotification(type: 'success' | 'error' | 'warning', message: string) {
  showToast(message, type === 'error' ? 'error' : 'success')
}
import './TemplateDesigner.css'

// ── ScrollableTabs ────────────────────────────────────────────────────────────

interface ScrollableTabsProps {
  items: [string, string][]
  activeKey: string
  onChange: (key: string) => void
}

function ScrollableTabs({ items, activeKey, onChange }: ScrollableTabsProps) {
  const scRef = useRef<HTMLDivElement>(null)
  const [overflows, setOverflows] = useState(false)
  const [canL, setCanL] = useState(false)
  const [canR, setCanR] = useState(false)

  const recheck = useCallback(() => {
    const el = scRef.current
    if (!el) return
    const ov = el.scrollWidth > el.clientWidth + 1
    setOverflows(ov)
    setCanL(el.scrollLeft > 1)
    setCanR(el.scrollLeft < el.scrollWidth - el.clientWidth - 1)
  }, [])

  useEffect(() => {
    recheck()
    const el = scRef.current
    if (!el) return
    const ro = new ResizeObserver(recheck)
    ro.observe(el)
    el.addEventListener('scroll', recheck)
    return () => { ro.disconnect(); el.removeEventListener('scroll', recheck) }
  }, [recheck, items.length])

  const nudge = (dir: number) => {
    scRef.current?.scrollBy({ left: dir * 120, behavior: 'smooth' })
  }

  return (
    <div className="tabs">
      <div className="tabs-scroller" ref={scRef}>
        {items.map(([k, l]) => (
          <div
            key={k}
            className={'tab' + (activeKey === k ? ' active' : '')}
            onClick={() => onChange(k)}
          >
            {l}
          </div>
        ))}
      </div>
      {overflows && (
        <div className="tabs-nav">
          <button disabled={!canL} onClick={() => nudge(-1)}><Icon name="chev-left" size={13} /></button>
          <button disabled={!canR} onClick={() => nudge(1)}><Icon name="chev-right" size={13} /></button>
        </div>
      )}
    </div>
  )
}

const DEFAULT_FORMATTING: DocumentFormatting = {
  pageSize: 'A4',
  orientation: 'portrait',
  margins: { top: 20, right: 15, bottom: 20, left: 30 },
  defaultFont: 'Times New Roman, serif',
  defaultFontSize: 14,
  lineSpacing: 1.5,
  paragraphDefaults: {},
}

function buildExtensions(paramsRef: RefObject<Parameter[]>, onImageClickRef: RefObject<(() => void) | null>) {
  return [
    StarterKit.configure({ link: false, strike: false }),
    Table.configure({ resizable: true }),
    TableRow,
    ExtendedTableCell,
    ExtendedTableHeader,
    Strike,
    Subscript,
    Superscript,
    TextStyle,
    Color,
    Highlight.configure({ multicolor: true }),
    FontFamily,
    FontSizeExtension,
    LineSpacingExtension,
    TextAlign.configure({ types: ['heading', 'paragraph', 'numberedClause'] }),
    Link.configure({ openOnClick: false }),
    HorizontalRule,
    Image.configure({ inline: false, allowBase64: false }),
    IndentExtension,
    ParagraphSpacingExtension,
    SearchReplaceExtension,
    PageBreakNode,
    ParameterFieldNode,
    ConditionalBlockNode,
    NumberedClauseNode,
    ConditionalInlineNode,
    ClauseRefNode,
    PhrasingsRefNode,
    SlashCommandExtension.configure({
      suggestion: {
        items: ({ query }: { query: string }) => {
          const cmds = buildSlashCommands(paramsRef.current ?? [], onImageClickRef.current ?? undefined)
          const q = query.toLowerCase()
          if (!q) return cmds
          return cmds.filter(
            (c) =>
              c.title.toLowerCase().includes(q) ||
              c.aliases.some((a) => a.includes(q)),
          )
        },
        render: () => {
          let root: import('react-dom/client').Root | null = null
          let el: HTMLDivElement | null = null
          let menuRef: import('./SlashCommandMenu').SlashCommandMenuRef | null = null

          return {
            onStart(props: { items: SlashCommand[]; command: (item: SlashCommand) => void; clientRect?: (() => DOMRect | null) | null }) {
              el = document.createElement('div')
              el.style.position = 'absolute'
              el.style.zIndex = '9999'
              document.body.appendChild(el)

              import('react-dom/client').then(({ createRoot }) => {
                root = createRoot(el!)
                root.render(
                  <SlashCommandMenu
                    ref={(ref) => { menuRef = ref }}
                    items={props.items}
                    command={props.command}
                  />,
                )
              })

              const rect = props.clientRect?.()
              if (rect && el) {
                el.style.left = `${rect.left}px`
                el.style.top = `${rect.bottom + 4}px`
              }
            },
            onUpdate(props: { items: SlashCommand[]; command: (item: SlashCommand) => void; clientRect?: (() => DOMRect | null) | null }) {
              if (!root || !el) return
              root.render(
                <SlashCommandMenu
                  ref={(ref) => { menuRef = ref }}
                  items={props.items}
                  command={props.command}
                />,
              )
              const rect = props.clientRect?.()
              if (rect) {
                el.style.left = `${rect.left}px`
                el.style.top = `${rect.bottom + 4}px`
              }
            },
            onKeyDown(props: { event: KeyboardEvent }) {
              if (props.event.key === 'Escape') return true
              return menuRef?.onKeyDown(props.event) ?? false
            },
            onExit() {
              root?.unmount()
              el?.remove()
              root = null
              el = null
              menuRef = null
            },
          }
        },
      },
    }),
  ]
}

const AUTOSAVE_DELAY = 30_000

// Collect all parameter_field codes referenced in a TipTap JSON tree
function collectUsedCodes(node: JSONContent): Set<string> {
  const codes = new Set<string>()
  const walk = (n: JSONContent) => {
    if (n.type === 'parameterField' && n.attrs?.parameterCode) {
      codes.add(n.attrs.parameterCode as string)
    }
    n.content?.forEach(walk)
  }
  walk(node)
  return codes
}

export default function TemplateDesigner() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const currentUser = useAuthStore((s) => s.user)
  const isUserResolved = useAuthStore((s) => s.isUserResolved)
  const [template, setTemplate] = useState<Template | null>(null)
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<'access_denied' | 'not_found' | 'unknown' | null>(null)
  const [reloadKey, setReloadKey] = useState(0)
  const [saving, setSaving] = useState(false)
  const [isDirty, setIsDirty] = useState(false)
  const [parameters, setParameters] = useState<Parameter[]>([])
  const [questionnaire, setQuestionnaire] = useState<Questionnaire | null>(null)
  const [scripts, setScripts] = useState<Script[]>([])
  const [phrasings, setPhrasings] = useState<Phrasing[]>([])
  const [editorJson, setEditorJson] = useState<JSONContent>({ type: 'doc', content: [] })
  const [published, setPublished] = useState(false)
  const [publishing, setPublishing] = useState(false)
  const [searchOpen, setSearchOpen] = useState(false)
  const [pageSettingsOpen, setPageSettingsOpen] = useState(false)
  const [imageOpen, setImageOpen] = useState(false)
  const [formatting, setFormatting] = useState<DocumentFormatting>(DEFAULT_FORMATTING)
  const [defaultFolderId, setDefaultFolderId] = useState<string | null>(null)
  const [folders, setFolders] = useState<Folder[]>([])
  const [sideW, setSideW] = useState(340)
  const [sidebarTab, setSidebarTab] = useState('parameters')
  const resizerRef = useRef<HTMLDivElement>(null)
  const { contextMenu, handleContextMenu, closeMenu } = useEditorContextMenu()
  const paramsRef = useRef<Parameter[]>([])
  const onImageClickRef = useRef<(() => void) | null>(null)

  // Versioning state
  const [bumpDialogOpen, setBumpDialogOpen] = useState(false)
  const [versionRefreshKey, setVersionRefreshKey] = useState(0)

  // Ref to always hold current state for autosave closure
  // Keep paramsRef in sync for slash commands closure
  useEffect(() => { paramsRef.current = parameters }, [parameters])

  // Keep image click ref in sync so slash command always opens the modal
  onImageClickRef.current = () => setImageOpen(true)

  const stateRef = useRef({ parameters, questionnaire, scripts, phrasings, editorJson, template, formatting, defaultFolderId })
  useEffect(() => {
    stateRef.current = { parameters, questionnaire, scripts, phrasings, editorJson, template, formatting, defaultFolderId }
  })

  const autosaveTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  const usedCodes = useMemo(() => collectUsedCodes(editorJson), [editorJson])

  // Права на управление шаблоном: владелец или администратор. Остальным
  // сервер закрывает черновики и черновые версии (403), поэтому действия
  // записи им не предлагаем.
  const mutationRights = getTemplateMutationRights(template, currentUser)
  const canPublish = mutationRights.canPublish
  // Редактирование доступно только владельцу (или админу) и только пока
  // шаблон не опубликован: опубликованный шаблон сервер на запись закрывает
  // (PUT → 409). Для всех остальных конструктор открыт только для чтения.
  const canEdit = canEditTemplate(template, currentUser, published)
  // Сохранение версии и откат — тоже операции записи (BUG-068): сервер
  // требует владельца шаблона или администратора.
  const canSaveVersion = mutationRights.canSaveVersion
  const canRollback = mutationRights.canRollback
  // Подсказка «вы не владелец» показывается только когда личность
  // пользователя уже известна — иначе владелец увидел бы её на миг-долю
  // секунды, пока едет ответ `GET /users/me`.
  const readOnlyHint = getReadOnlyHint(template, currentUser)

  const handleParametersChange = useCallback((params: Parameter[]) => {
    setParameters(params)
    setIsDirty(true)
  }, [])

  const handleQuestionnaireChange = useCallback((q: Questionnaire | null) => {
    setQuestionnaire(q)
    setIsDirty(true)
  }, [])

  const handlePhrasingsChange = useCallback((p: Phrasing[]) => {
    setPhrasings(p)
    setIsDirty(true)
  }, [])

  // eslint-disable-next-line react-hooks/exhaustive-deps
  const extensions = useMemo(() => buildExtensions(paramsRef, onImageClickRef), [])

  const editor = useEditor({
    extensions,
    onUpdate: ({ editor: e }) => {
      const json = e.getJSON()
      setEditorJson(json)
      setIsDirty(true)
    },
  })

  // ── Load template ──────────────────────────────────────────────────────────
  useEffect(() => {
    foldersApi.list().then(setFolders).catch(() => {/* non-critical */})
  }, [])

  useEffect(() => {
    if (!id) return
    setLoading(true)
    setLoadError(null)
    templatesApi.getTemplate(id)
      .then((t) => {
        setTemplate(t)
        setPublished(t.published ?? false)
        setParameters(t.parameters ?? [])
        setQuestionnaire(t.questionnaire)
        setScripts(t.scripts ?? [])
        setPhrasings(t.phrasings ?? [])
        setDefaultFolderId(t.defaultFolderId ?? null)
        if (t.formatting) setFormatting({ ...DEFAULT_FORMATTING, ...t.formatting })
        if (editor && t.body) {
          editor.commands.setContent(
            t.body as Parameters<typeof editor.commands.setContent>[0]
          )
          setEditorJson(editor.getJSON())
        }
        setIsDirty(false)
      })
      .catch((err: unknown) => {
        // Чужие черновики сервер закрывает (BUG-059) — показываем понятное
        // сообщение вместо общей ошибки загрузки.
        const status = getErrorStatus(err)
        if (status === 403) {
          setLoadError('access_denied')
        } else if (status === 404) {
          setLoadError('not_found')
        } else {
          setLoadError('unknown')
          showNotification('error', extractServerMessage(err) ?? 'Не удалось загрузить шаблон')
        }
      })
      .finally(() => setLoading(false))
  }, [id, editor, reloadKey])

  // ── Save (silent, no version — for autosave) ───────────────────────────────
  // Возвращает `true`, если содержимое сохранено. Ошибка показывается
  // пользователю всегда (в т.ч. для автосейва): раньше 409/400 от сервера
  // проглатывался молча и правки выглядели потерянными.
  const save = useCallback(async (silent = false): Promise<boolean> => {
    const { parameters, questionnaire, scripts, phrasings, editorJson, template, formatting, defaultFolderId } = stateRef.current
    if (!id || !template) return false
    // Запись закрыта для не-владельцев: сервер всё равно отклонил бы PUT
    // (409/403), а «проглоченный» автосейв выглядел бы как потеря правок.
    if (!canEdit) return false
    setSaving(true)
    try {
      await templatesApi.updateTemplate(id, {
        name: template.name,
        description: template.description ?? undefined,
        category: template.category ?? undefined,
        parameters,
        questionnaire: questionnaire ?? undefined,
        body: editorJson as Record<string, unknown>,
        scripts,
        formatting,
        phrasings,
        defaultFolderId,
      })
      setIsDirty(false)
      if (!silent) showNotification('success', 'Шаблон сохранён')
      return true
    } catch (err: unknown) {
      showNotification('error', extractServerMessage(err) ?? 'Ошибка при сохранении')
      return false
    } finally {
      setSaving(false)
    }
  }, [id, canEdit])

  // ── Manual save with version bump dialog ────────────────────────────────────
  const handleManualSave = useCallback(() => {
    if (!id || published || !canEdit) return
    // First silently save current content
    save(true)
      .then((saved) => {
        if (saved) setBumpDialogOpen(true)
      })
  }, [id, published, canEdit, save])

  // ── Confirm version save ────────────────────────────────────────────────────
  const handleVersionConfirm = useCallback(async (bumpType: 'MAJOR' | 'MINOR', comment: string) => {
    if (!id || !canEdit || !canSaveVersion) return
    const { parameters, questionnaire, scripts, phrasings, editorJson, template, formatting, defaultFolderId } = stateRef.current
    if (!template) return
    setSaving(true)
    try {
      // First save the content
      await templatesApi.updateTemplate(id, {
        name: template.name,
        description: template.description ?? undefined,
        category: template.category ?? undefined,
        parameters,
        questionnaire: questionnaire ?? undefined,
        body: editorJson as Record<string, unknown>,
        scripts,
        formatting,
        phrasings,
        defaultFolderId,
      })
      // Then create the version
      const result = await templatesApi.saveVersion(id, { bumpType, comment: comment || undefined })
      setTemplate(result.template)
      setPublished(result.template.published)
      setIsDirty(false)
      setBumpDialogOpen(false)
      setVersionRefreshKey((k) => k + 1)
      showNotification('success', `Сохранена версия v${result.version.majorVersion}.${result.version.minorVersion}`)
    } catch (err: unknown) {
      // Текст сервера (например, «Нет прав на изменение шаблона») объясняет
      // причину отказа лучше общего сообщения.
      showNotification('error', extractServerMessage(err) ?? 'Ошибка при сохранении версии')
    } finally {
      setSaving(false)
    }
  }, [id, canEdit, canSaveVersion])

  // ── Rollback ────────────────────────────────────────────────────────────────
  const handleRollback = useCallback(async (versionNumber: number) => {
    if (!id || !canRollback) return
    const result = await templatesApi.rollback(id, versionNumber)
    setTemplate(result.template)
    setParameters(result.template.parameters ?? [])
    setQuestionnaire(result.template.questionnaire)
    setScripts(result.template.scripts ?? [])
    setPhrasings(result.template.phrasings ?? [])
    if (result.template.formatting) setFormatting({ ...DEFAULT_FORMATTING, ...result.template.formatting })
    if (editor && result.template.body) {
      editor.commands.setContent(result.template.body as Parameters<typeof editor.commands.setContent>[0])
      setEditorJson(editor.getJSON())
    }
    setIsDirty(false)
    setVersionRefreshKey((k) => k + 1)
  }, [id, editor, canRollback])

  const handlePublish = useCallback(async () => {
    if (!id || !canPublish) return
    setPublishing(true)
    try {
      const updated = published
        ? await templatesApi.unpublishTemplate(id)
        : await templatesApi.publishTemplate(id)
      setPublished(updated.published ?? false)
      showNotification('success', published ? 'Шаблон снят с публикации — создание документов закрыто' : 'Шаблон опубликован — теперь можно создавать документы')
    } catch (err: unknown) {
      // Сервер отвечает 403 «Нет прав на изменение шаблона» — показываем его
      // текст, а не общий отказ.
      showNotification('error', extractServerMessage(err) ?? (published ? 'Не удалось снять шаблон с публикации' : 'Не удалось опубликовать шаблон'))
    } finally {
      setPublishing(false)
    }
  }, [id, published, canPublish])

  const handleExport = useCallback(async (format: 'docx' | 'pdf') => {
    if (!id) return
    try {
      const blob = format === 'docx'
        ? await templatesApi.exportDocx(id)
        : await templatesApi.exportPdf(id)
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `template-${id}.${format}`
      a.click()
      URL.revokeObjectURL(url)
    } catch {
      showNotification('error', `Ошибка экспорта в ${format.toUpperCase()}`)
    }
  }, [id])

  // ── Sync editor editable state with edit rights ────────────────────────────
  useEffect(() => {
    if (!editor) return
    editor.setEditable(canEdit, false)
  }, [editor, canEdit])

  // ── Autosave (debounce 30s) ────────────────────────────────────────────────
  // Автосейв запускается только у того, кому разрешена запись: у не-владельца
  // он только тратил бы запросы, которые сервер отклонит (409/403).
  useEffect(() => {
    if (!isDirty || !canEdit) return
    if (autosaveTimerRef.current) clearTimeout(autosaveTimerRef.current)
    autosaveTimerRef.current = setTimeout(() => save(true), AUTOSAVE_DELAY)
    return () => {
      if (autosaveTimerRef.current) clearTimeout(autosaveTimerRef.current)
    }
  }, [isDirty, editorJson, parameters, questionnaire, scripts, phrasings, save, canEdit])

  // ── Keyboard shortcuts ─────────────────────────────────────────────────────
  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key === 's') {
        e.preventDefault()
        if (!published) handleManualSave()
      }
      if (canEdit && (e.ctrlKey || e.metaKey) && e.key === 'f') {
        e.preventDefault()
        setSearchOpen((v) => !v)
      }
    }
    window.addEventListener('keydown', handler)
    return () => window.removeEventListener('keydown', handler)
  }, [handleManualSave, published, canEdit])

  // ── Resizable side panel ──────────────────────────────────────────────────
  const startDrag = useCallback((e: React.MouseEvent) => {
    e.preventDefault()
    const startX = e.clientX
    const startW = sideW
    const onMove = (ev: MouseEvent) => {
      const delta = startX - ev.clientX
      setSideW(Math.max(260, Math.min(560, startW + delta)))
    }
    const onUp = () => {
      window.removeEventListener('mousemove', onMove)
      window.removeEventListener('mouseup', onUp)
      resizerRef.current?.classList.remove('dragging')
    }
    window.addEventListener('mousemove', onMove)
    window.addEventListener('mouseup', onUp)
    resizerRef.current?.classList.add('dragging')
  }, [sideW])

  // ── Sidebar tabs ───────────────────────────────────────────────────────────
  const sidebarTabs = useMemo(() => [
    {
      key: 'parameters',
      label: 'Параметры',
      children: (
        <ParameterPanel
          parameters={parameters}
          onChange={handleParametersChange}
          usedCodes={usedCodes}
          readOnly={!canEdit}
        />
      ),
    },
    {
      key: 'questionnaire',
      label: 'Опросный лист',
      children: (
        <QuestionnairePanel
          questionnaire={questionnaire}
          parameters={parameters}
          onChange={handleQuestionnaireChange}
          readOnly={!canEdit}
        />
      ),
    },
    {
      key: 'phrasings',
      label: 'Фразы',
      children: (
        <PhrasingsPanel
          phrasings={phrasings}
          parameters={parameters}
          editor={editor}
          onChange={handlePhrasingsChange}
          readOnly={!canEdit}
        />
      ),
    },
    {
      key: 'history',
      label: 'История',
      children: id ? (
        <VersionHistoryPanel
          templateId={id}
          onRollback={handleRollback}
          refreshKey={versionRefreshKey}
          canRollback={canRollback}
        />
      ) : (
        <div className="empty" style={{ padding: 24 }}>
          <div style={{ fontSize: 12, color: 'var(--ink-3)' }}>Нет данных</div>
        </div>
      ),
    },
    {
      key: 'settings',
      label: 'Настройки',
      children: (
        <div style={{ padding: '16px 12px' }}>
          <div style={{ marginBottom: 8, fontWeight: 500, fontSize: 13 }}>Папка по умолчанию</div>
          <Select
            value={defaultFolderId ?? ''}
            onValueChange={v => {
              setDefaultFolderId(v === '' ? null : v)
              setIsDirty(true)
            }}
            disabled={!canEdit}
          >
            <SelectTrigger className="w-full">
              <SelectValue placeholder="Выберите папку" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="">Не задана</SelectItem>
              {folders.map(f => (
                <SelectItem key={f.id} value={f.id}>{f.name}</SelectItem>
              ))}
            </SelectContent>
          </Select>
          <div style={{ marginTop: 6, fontSize: 12, color: 'var(--ink-3)' }}>
            При создании документа из этого шаблона папка будет предзаполнена.
          </div>
        </div>
      ),
    },
  ], [parameters, handleParametersChange, usedCodes, questionnaire, handleQuestionnaireChange, phrasings, handlePhrasingsChange, editor, canEdit, defaultFolderId, folders, id, handleRollback, versionRefreshKey, canRollback])

  // Ошибка загрузки шаблона: чужой черновик сервер закрывает (BUG-059),
  // показываем понятное объяснение вместо пустого конструктора.
  // Проверка идёт ДО проверки прав: 403 должен быть виден сразу, не дожидаясь
  // ответа `GET /users/me`.
  if (loadError) {
    const isAccessDenied = loadError === 'access_denied'
    const title = isAccessDenied
      ? 'Нет доступа к шаблону'
      : loadError === 'not_found'
        ? 'Шаблон не найден'
        : 'Ошибка загрузки шаблона'
    const description = isAccessDenied
      ? 'Этот шаблон недоступен — вы не его владелец. Черновики шаблонов доступны только их автору.'
      : loadError === 'not_found'
        ? 'Шаблон был удалён или ссылка устарела.'
        : 'Не удалось загрузить шаблон. Попробуйте повторить попытку.'
    return (
      <div className="m-6 space-y-3">
        <Alert variant={isAccessDenied ? 'default' : 'destructive'}>
          <AlertTitle>{title}</AlertTitle>
          <AlertDescription>{description}</AlertDescription>
        </Alert>
        <div className="flex gap-2">
          <Button variant="outline" onClick={() => navigate('/templates')}>
            К списку шаблонов
          </Button>
          {loadError === 'unknown' && (
            <Button onClick={() => setReloadKey((k) => k + 1)}>Повторить</Button>
          )}
        </div>
      </div>
    )
  }

  // Пока личность пользователя не выяснена, права определить нельзя:
  // показать действия владельца до ответа `GET /users/me` нельзя (у владельца
  // они мигнули бы и исчезли), а объявлять «вы не владелец» — рано. Поэтому
  // до выяснения личности конструктор не рисуем вовсе.
  if (loading || !isUserResolved) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100%', padding: 48 }}>
        <div style={{ color: 'var(--ink-3)', fontSize: 14 }}>
          {loading ? 'Загрузка шаблона…' : 'Проверка прав доступа…'}
        </div>
      </div>
    )
  }

  const activeTabContent = sidebarTabs.find((t) => t.key === sidebarTab)?.children

  return (
    <DesignerContext.Provider value={{ parameters, phrasings, published }}>
      <div className="designer">

        {/* Designer Bar */}
        <div className="designer-bar">
          <button className="icon-btn" onClick={() => navigate('/templates')} title="Назад к шаблонам">
            <Icon name="chev-left" size={16} />
          </button>
          <h2>{template?.name ?? 'Шаблон'}</h2>
          <span className="tag" style={{ flexShrink: 0 }}>
            v{template?.majorVersion ?? 1}.{template?.minorVersion ?? 0}
          </span>
          {isDirty && !published && (
            <span className="unsaved"><Icon name="alert" size={11} /> Не сохранено</span>
          )}
          {published && <span className="tag green"><span className="dot" />Опубликован</span>}

          <div className="actions">
            <div style={{ width: 1, height: 18, background: 'var(--line)', margin: '0 4px' }} />
            {canEdit && (
              <button
                className={'btn sm icon-only' + (searchOpen ? ' active' : '')}
                onClick={() => setSearchOpen((v) => !v)}
                title="Найти и заменить · ⌘F"
              >
                <Icon name="search" size={13} />
              </button>
            )}
            {canEdit && (
              <button
                className="btn sm icon-only"
                onClick={() => setPageSettingsOpen(true)}
                title="Параметры страницы"
              >
                <Icon name="settings" size={13} />
              </button>
            )}
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <button className="btn sm icon-only" title="Экспорт DOCX/PDF">
                  <Icon name="download" size={13} />
                </button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end">
                <DropdownMenuItem onClick={() => handleExport('docx')}>
                  <Icon name="download" size={13} /> Экспорт в DOCX
                </DropdownMenuItem>
                <DropdownMenuItem onClick={() => handleExport('pdf')}>
                  <Icon name="download" size={13} /> Экспорт в PDF
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
            {!published ? (
              <>
                {canEdit && canSaveVersion && (
                  <button
                    className="btn sm primary"
                    disabled={!isDirty || saving}
                    onClick={handleManualSave}
                    title="Сохранить · ⌘S"
                  >
                    <Icon name="save" size={13} /> Сохранить
                  </button>
                )}
                {canPublish && (
                  <AlertDialog>
                    <AlertDialogTrigger asChild>
                      <button className="btn sm success icon-only" disabled={publishing} title="Опубликовать шаблон">
                        <Icon name="publish" size={13} />
                      </button>
                    </AlertDialogTrigger>
                    <AlertDialogContent>
                      <AlertDialogHeader>
                        <AlertDialogTitle>Опубликовать шаблон?</AlertDialogTitle>
                        <AlertDialogDescription>
                          По шаблону можно будет создавать документы.
                        </AlertDialogDescription>
                      </AlertDialogHeader>
                      <AlertDialogFooter>
                        <AlertDialogCancel>Отмена</AlertDialogCancel>
                        <AlertDialogAction onClick={handlePublish}>Опубликовать</AlertDialogAction>
                      </AlertDialogFooter>
                    </AlertDialogContent>
                  </AlertDialog>
                )}
              </>
            ) : (
              canPublish && (
                <AlertDialog>
                  <AlertDialogTrigger asChild>
                    <button className="btn sm danger icon-only" disabled={publishing} title="Снять с публикации">
                      <Icon name="lock" size={13} />
                    </button>
                  </AlertDialogTrigger>
                  <AlertDialogContent>
                    <AlertDialogHeader>
                      <AlertDialogTitle>Снять с публикации?</AlertDialogTitle>
                      <AlertDialogDescription>
                        По шаблону нельзя будет создавать документы.
                      </AlertDialogDescription>
                    </AlertDialogHeader>
                    <AlertDialogFooter>
                      <AlertDialogCancel>Отмена</AlertDialogCancel>
                      <AlertDialogAction onClick={handlePublish}>Да, снять</AlertDialogAction>
                    </AlertDialogFooter>
                  </AlertDialogContent>
                </AlertDialog>
              )
            )}
          </div>
        </div>

        {/* Статус доступа: опубликован / нет прав на редактирование */}
        {(published || readOnlyHint) && (
          <div className="banner">
            <Icon name="info" size={14} />
            <span>
              {published && (
                <>
                  <b className="b-strong">Шаблон опубликован.</b> Редактирование заблокировано — все документы, созданные после, наследуют эту версию.{' '}
                </>
              )}
              {readOnlyHint && <b className="b-strong">{readOnlyHint}</b>}
            </span>
            {published && canPublish && (
              <AlertDialog>
                <AlertDialogTrigger asChild>
                  <button className="btn xs">Снять с публикации</button>
                </AlertDialogTrigger>
                <AlertDialogContent>
                  <AlertDialogHeader>
                    <AlertDialogTitle>Снять с публикации?</AlertDialogTitle>
                    <AlertDialogDescription>
                      По шаблону нельзя будет создавать документы.
                    </AlertDialogDescription>
                  </AlertDialogHeader>
                  <AlertDialogFooter>
                    <AlertDialogCancel>Отмена</AlertDialogCancel>
                    <AlertDialogAction onClick={handlePublish}>Да, снять</AlertDialogAction>
                  </AlertDialogFooter>
                </AlertDialogContent>
              </AlertDialog>
            )}
          </div>
        )}

        {/* Designer Body */}
        <div
          className="designer-body"
          style={{ '--side-w': sideW + 'px' } as React.CSSProperties}
        >
          {/* Editor pane */}
          <div className="editor-pane">
            {editor && canEdit && (
              <EditorToolbar
                editor={editor}
                parameters={parameters}
                phrasings={phrasings}
                onImageClick={() => setImageOpen(true)}
                formatting={formatting}
              />
            )}
            {editor && canEdit && (
              <SearchReplacePanel editor={editor} open={searchOpen} onClose={() => setSearchOpen(false)} />
            )}
            {editor && canEdit && <TableBubbleMenu editor={editor} />}
            {editor && canEdit && <TextBubbleMenu editor={editor} parameters={parameters} />}
            {editor && canEdit && <FloatingInsertMenu editor={editor} parameters={parameters} />}
            <div
              className="editor-canvas"
              onContextMenu={editor && canEdit ? (e) => handleContextMenu(e, editor, parameters) : undefined}
            >
              <div className="tiptap-page" style={formattingToCssVars(formatting)}>
                <EditorContent editor={editor} />
              </div>
            </div>
          </div>

          {/* Resizer */}
          <div
            ref={resizerRef}
            className="side-resizer"
            onMouseDown={startDrag}
            title="Потяните, чтобы изменить размер"
          />

          {/* Side panel */}
          <div className="designer-side">
            <ScrollableTabs
              items={[
                ['parameters', 'Параметры'],
                ['questionnaire', 'Опросный лист'],
                ['phrasings', 'Формулировки'],
                ['history', 'История'],
                ['scripts', 'Скрипты'],
              ]}
              activeKey={sidebarTab}
              onChange={setSidebarTab}
            />
            <div className="tab-body">
              {sidebarTab === 'scripts' ? (
                <div className="empty">
                  <div className="ill"><Icon name="code" size={22} /></div>
                  <div style={{ fontSize: 13.5, fontWeight: 500, color: 'var(--ink-1)', marginBottom: 4 }}>Скриптов пока нет</div>
                  <div style={{ fontSize: 12, color: 'var(--ink-3)', textAlign: 'center', maxWidth: 220 }}>
                    Скрипты позволят вычислять значения параметров на лету. Скоро появятся в DocForge.
                  </div>
                </div>
              ) : (
                activeTabContent
              )}
            </div>
          </div>
        </div>
      </div>

      {editor && (
        <ImageUploadModal open={imageOpen} onClose={() => setImageOpen(false)} editor={editor} />
      )}
      <SaveBumpDialog
        open={bumpDialogOpen}
        onClose={() => setBumpDialogOpen(false)}
        templateId={id!}
        templateName={template?.name ?? ''}
        majorVersion={template?.majorVersion ?? 1}
        minorVersion={template?.minorVersion ?? 0}
        onConfirm={handleVersionConfirm}
      />
      <PageSettingsDrawer
        open={pageSettingsOpen}
        onClose={() => setPageSettingsOpen(false)}
        formatting={formatting}
        onChange={(f) => { setFormatting(f); setIsDirty(true) }}
      />
      <ContextMenuPortal ctx={contextMenu} onClose={closeMenu} />
    </DesignerContext.Provider>
  )
}
