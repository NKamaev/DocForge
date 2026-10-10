import { useState } from 'react'
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
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from '@/components/ui'
import { Pencil, Plus, Trash2, ArrowUpFromLine } from 'lucide-react'
import type { Editor } from '@tiptap/react'
import PhrasingEditorModal from './PhrasingEditorModal'
import PhrasingsRefInsertModal from './PhrasingsRefInsertModal'
import type { Phrasing } from '../../types/templateModel'
import type { Parameter } from '../../types/template'

interface Props {
  phrasings: Phrasing[]
  parameters: Parameter[]
  editor?: Editor | null
  onChange: (phrasings: Phrasing[]) => void
  readOnly?: boolean
}

export default function PhrasingsPanel({ phrasings, parameters, editor, onChange, readOnly }: Props) {
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<Phrasing | null>(null)
  const [insertModalOpen, setInsertModalOpen] = useState(false)
  const [insertInitialIds, setInsertInitialIds] = useState<string[]>([])
  const [deleteConfirmId, setDeleteConfirmId] = useState<string | null>(null)

  const openCreate = () => { setEditing(null); setModalOpen(true) }
  const openEdit   = (p: Phrasing) => { setEditing(p); setModalOpen(true) }

  const handleSave = (updated: Phrasing) => {
    const idx = phrasings.findIndex(p => p.id === updated.id)
    if (idx >= 0) {
      onChange(phrasings.map((p, i) => i === idx ? updated : p))
    } else {
      onChange([...phrasings, updated])
    }
    setModalOpen(false)
  }

  const handleDelete = () => {
    if (!deleteConfirmId) return
    onChange(phrasings.filter(p => p.id !== deleteConfirmId))
    setDeleteConfirmId(null)
  }

  const handleInsert = (p: Phrasing) => {
    if (!editor) return
    setInsertInitialIds([p.id])
    setInsertModalOpen(true)
  }

  const handleInsertConfirm = (phrasingsIds: string[], displayNames: string[], separator: string) => {
    if (!editor) return
    editor.chain().focus().insertPhrasingsRef({ phrasingsIds, displayNames, separator }).run()
  }

  return (
    <div style={{ padding: '12px 16px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
        <span style={{ fontWeight: 600, fontSize: 14 }}>Формулировки</span>
        {!readOnly && (
          <Button size="sm" onClick={openCreate}>
            <Plus className="mr-1 size-3.5" /> Добавить
          </Button>
        )}
      </div>

      {phrasings.length === 0 ? (
        <span style={{ fontSize: 12, color: 'var(--ink-3)' }}>
          Нет формулировок. Нажмите «Добавить», чтобы создать именованный список вариантов текста с условиями.
        </span>
      ) : (
        <div>
          {phrasings.map(p => (
            <div key={p.id} style={{ padding: '6px 0', display: 'flex', alignItems: 'center', gap: 8, borderBottom: '1px solid var(--line)' }}>
              <div style={{ flex: 1, overflow: 'hidden' }}>
                <span style={{ fontSize: 13, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', maxWidth: 180, display: 'block' }}>
                  {p.name}
                </span>
                <div style={{ display: 'flex', gap: 4, flexWrap: 'wrap', alignItems: 'center' }}>
                  <Badge variant="outline" style={{ fontSize: 11 }}>{p.variants.length} вар.</Badge>
                  {p.description && (
                    <span style={{ fontSize: 11, color: 'var(--ink-3)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                      {p.description}
                    </span>
                  )}
                </div>
              </div>

              {!readOnly && (
                <div className="flex gap-0.5 shrink-0">
                  <TooltipProvider>
                    <Tooltip>
                      <TooltipTrigger asChild>
                        <Button variant="ghost" size="icon-sm" onClick={() => handleInsert(p)} disabled={!editor}>
                          <ArrowUpFromLine className="size-3.5" />
                        </Button>
                      </TooltipTrigger>
                      <TooltipContent>Вставить в текст</TooltipContent>
                    </Tooltip>
                  </TooltipProvider>

                  <TooltipProvider>
                    <Tooltip>
                      <TooltipTrigger asChild>
                        <Button variant="ghost" size="icon-sm" onClick={() => openEdit(p)}>
                          <Pencil className="size-3.5" />
                        </Button>
                      </TooltipTrigger>
                      <TooltipContent>Редактировать</TooltipContent>
                    </Tooltip>
                  </TooltipProvider>

                  <Button variant="ghost" size="icon-sm" onClick={() => setDeleteConfirmId(p.id)}>
                    <Trash2 className="size-3.5 text-destructive" />
                  </Button>
                </div>
              )}
            </div>
          ))}
        </div>
      )}

      <AlertDialog open={deleteConfirmId !== null} onOpenChange={(open) => { if (!open) setDeleteConfirmId(null) }}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Удалить формулировку?</AlertDialogTitle>
            <AlertDialogDescription>
              Ноды phrasingsRef в шаблоне потеряют ссылку.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel onClick={() => setDeleteConfirmId(null)}>Отмена</AlertDialogCancel>
            <AlertDialogAction variant="destructive" onClick={handleDelete}>Удалить</AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      <PhrasingEditorModal
        open={modalOpen}
        initial={editing}
        parameters={parameters}
        onSave={handleSave}
        onClose={() => setModalOpen(false)}
      />

      <PhrasingsRefInsertModal
        open={insertModalOpen}
        phrasings={phrasings}
        initialIds={insertInitialIds}
        onInsert={handleInsertConfirm}
        onClose={() => setInsertModalOpen(false)}
      />
    </div>
  )
}
