import { useEffect, useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import {
  Alert,
  AlertDescription,
  AlertTitle,
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  Button,
  Card,
  CardContent,
  CardHeader,
  CardTitle,
  Checkbox,
  DatePicker,
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  Input,
  Label,
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
  Skeleton,
  Switch,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui'
import { GripVertical, Pencil, Plus, Trash2 } from 'lucide-react'
import { Icon } from '../components/Icon'
import dayjs from 'dayjs'
import { directoriesApi } from '../api/directoriesApi'
import type { Directory, DirectoryEntry, DirectoryField, DirectoryFieldType } from '../types/directory'

function showNotification(type: 'success' | 'error', message: string) {
  console.log(`[${type.toUpperCase()}]`, message)
  if (type === 'error') {
    alert(message)
  }
}

// ── Constants ─────────────────────────────────────────────────────────────────

const FIELD_TYPE_OPTIONS: { value: DirectoryFieldType; label: string }[] = [
  { value: 'TEXT',    label: 'Текст'    },
  { value: 'NUMBER',  label: 'Число'    },
  { value: 'DATE',    label: 'Дата'     },
  { value: 'FIO',     label: 'ФИО'      },
  { value: 'BOOLEAN', label: 'Да/Нет'   },
  { value: 'SELECT',  label: 'Список'   },
]

const KEY_PATTERN = /^[a-z][a-z0-9_]*$/

// ── Schema field row ──────────────────────────────────────────────────────────

interface FieldRowProps {
  field: DirectoryField
  existingKeys: string[]
  onChange: (updated: DirectoryField) => void
  onDeleteRequest: () => void
}

function FieldRow({ field, existingKeys, onChange, onDeleteRequest }: FieldRowProps) {
  const keyError = !KEY_PATTERN.test(field.key)
    ? 'Только строчные буквы, цифры и _'
    : existingKeys.filter(k => k === field.key).length > 1
    ? 'Код уже используется'
    : null

  return (
    <div className="flex gap-2 items-start mb-3">
      <GripVertical className="size-4 text-muted-foreground mt-1.5 shrink-0" />

      {/* Key */}
      <div className="w-[150px]">
        <Input
          value={field.key}
          onChange={e => onChange({ ...field, key: e.target.value })}
          placeholder="field_key"
          className={`font-mono ${keyError ? 'border-destructive' : ''}`}
        />
        {keyError && <p className="text-destructive text-[11px] mt-0.5">{keyError}</p>}
      </div>

      {/* Label */}
      <Input
        value={field.label}
        onChange={e => onChange({ ...field, label: e.target.value })}
        placeholder="Название поля"
        className="w-[180px]"
      />

      {/* Type */}
      <Select value={field.type} onValueChange={(type: DirectoryFieldType) =>
        onChange({ ...field, type, options: type === 'SELECT' ? (field.options ?? []) : undefined })
      }>
        <SelectTrigger className="w-[120px]">
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          {FIELD_TYPE_OPTIONS.map(opt => (
            <SelectItem key={opt.value} value={opt.value}>{opt.label}</SelectItem>
          ))}
        </SelectContent>
      </Select>

      {/* Options (SELECT only) */}
      {field.type === 'SELECT' && (
        <Select
          value={field.options?.[0] ?? ''}
          onValueChange={() => { /* placeholder for single-select mode */ }}
        >
          <SelectTrigger className="flex-1 min-w-[160px]">
            <SelectValue placeholder="Добавьте варианты..." />
          </SelectTrigger>
          <SelectContent>
            {(field.options ?? []).map((opt, i) => (
              <SelectItem key={i} value={opt}>{opt}</SelectItem>
            ))}
          </SelectContent>
        </Select>
      )}

      <Button variant="ghost" size="icon-sm" className="mt-0.5" onClick={onDeleteRequest}>
        <Trash2 className="size-3.5 text-destructive" />
      </Button>
    </div>
  )
}

// ── Entry modal ───────────────────────────────────────────────────────────────

interface EntryModalProps {
  open: boolean
  fields: DirectoryField[]
  initial?: DirectoryEntry | null
  onSave: (values: Record<string, unknown>) => Promise<void>
  onClose: () => void
}

function FieldInput({ field, value, onChange }: {
  field: DirectoryField
  value: unknown
  onChange: (v: unknown) => void
}) {
  switch (field.type) {
    case 'NUMBER':
      return (
        <Input
          type="number"
          value={value as string ?? ''}
          onChange={e => onChange(e.target.value ? Number(e.target.value) : null)}
        />
      )
    case 'DATE':
      return (
        <DatePicker
          value={value ? String(value) : undefined}
          onChange={(d) => onChange(d || null)}
        />
      )
    case 'BOOLEAN':
      return (
        <Switch
          checked={Boolean(value)}
          onCheckedChange={onChange}
        />
      )
    case 'SELECT':
      return (
        <Select
          value={value as string ?? ''}
          onValueChange={onChange}
        >
          <SelectTrigger className="w-full">
            <SelectValue placeholder="Выберите..." />
          </SelectTrigger>
          <SelectContent>
            {(field.options ?? []).map((o, i) => (
              <SelectItem key={i} value={o}>{o}</SelectItem>
            ))}
          </SelectContent>
        </Select>
      )
    default:
      return (
        <Input
          value={value as string ?? ''}
          onChange={e => onChange(e.target.value)}
          placeholder={field.label}
        />
      )
  }
}

function EntryModal({ open, fields, initial, onSave, onClose }: EntryModalProps) {
  const [values, setValues] = useState<Record<string, unknown>>({})
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (open) setValues(initial?.values ?? {})
  }, [open, initial])

  const handleOk = async () => {
    setSaving(true)
    try {
      await onSave(values)
    } finally {
      setSaving(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={(o) => { if (!o) onClose() }}>
      <DialogContent className="sm:max-w-[520px]">
        <DialogHeader>
          <DialogTitle>{initial ? 'Редактировать запись' : 'Новая запись'}</DialogTitle>
        </DialogHeader>

        <div className="mt-4 space-y-3">
          {fields.map(f => (
            <div key={f.key} className="space-y-1.5">
              <Label>{f.label || f.key}</Label>
              <FieldInput
                field={f}
                value={values[f.key]}
                onChange={v => setValues(prev => ({ ...prev, [f.key]: v }))}
              />
            </div>
          ))}
        </div>

        <DialogFooter className="mt-4">
          <Button variant="outline" onClick={onClose}>Отмена</Button>
          <Button onClick={handleOk} disabled={saving}>
            {saving ? 'Сохранение...' : 'Сохранить'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

// ── Main page ─────────────────────────────────────────────────────────────────

export default function DirectoryDetailPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()

  const [directory, setDirectory] = useState<Directory | null>(null)
  const [loading, setLoading] = useState(true)
  const [schemaFields, setSchemaFields] = useState<DirectoryField[]>([])
  const [schemaDirty, setSchemaDirty] = useState(false)
  const [savingSchema, setSavingSchema] = useState(false)

  const [entryModalOpen, setEntryModalOpen] = useState(false)
  const [editingEntry, setEditingEntry] = useState<DirectoryEntry | null>(null)
  const [deleteFieldIdx, setDeleteFieldIdx] = useState<number | null>(null)
  const [deleteEntryId, setDeleteEntryId] = useState<string | null>(null)

  const load = () => {
    if (!id) return
    directoriesApi.getById(id)
      .then(d => {
        setDirectory(d)
        setSchemaFields(d.fields)
        setSchemaDirty(false)
      })
      .catch(() => showNotification('error', 'Справочник не найден'))
      .finally(() => setLoading(false))
  }

  useEffect(() => { load() }, [id])

  // ── Schema editing ──────────────────────────────────────────────────────────

  const addField = () => {
    const n = schemaFields.length + 1
    setSchemaFields(prev => [...prev, { key: `field_${n}`, label: '', type: 'TEXT', order: prev.length }])
    setSchemaDirty(true)
  }

  const updateField = (idx: number, updated: DirectoryField) => {
    setSchemaFields(prev => prev.map((f, i) => i === idx ? updated : f))
    setSchemaDirty(true)
  }

  const removeField = (idx: number) => {
    setSchemaFields(prev => prev.filter((_, i) => i !== idx))
    setSchemaDirty(true)
  }

  const allKeys = schemaFields.map(f => f.key)
  const schemaValid = schemaFields.every(f => KEY_PATTERN.test(f.key)) &&
    new Set(allKeys).size === allKeys.length

  const saveSchema = async () => {
    if (!id || !directory || !schemaValid) return
    setSavingSchema(true)
    try {
      const updated = await directoriesApi.update(id, { name: directory.name, fields: schemaFields })
      setDirectory(updated)
      setSchemaFields(updated.fields)
      setSchemaDirty(false)
      showNotification('success', 'Схема сохранена')
    } catch {
      showNotification('error', 'Ошибка сохранения схемы')
    } finally {
      setSavingSchema(false)
    }
  }

  // ── Entries ─────────────────────────────────────────────────────────────────

  const handleAddEntry = async (values: Record<string, unknown>) => {
    if (!id) return
    const updated = await directoriesApi.addEntry(id, { values })
    setDirectory(updated)
    setEntryModalOpen(false)
  }

  const handleUpdateEntry = async (values: Record<string, unknown>) => {
    if (!id || !editingEntry) return
    const updated = await directoriesApi.updateEntry(id, editingEntry.id, { values })
    setDirectory(updated)
    setEntryModalOpen(false)
    setEditingEntry(null)
  }

  const handleDeleteEntry = async (entryId: string) => {
    if (!id) return
    try {
      const updated = await directoriesApi.deleteEntry(id, entryId)
      setDirectory(updated)
    } catch {
      showNotification('error', 'Ошибка удаления записи')
    }
  }

  // ── Render ──────────────────────────────────────────────────────────────────

  if (loading) return (
    <div className="flex justify-center items-center py-16">
      <Skeleton className="h-12 w-12 rounded-full" />
    </div>
  )

  if (!directory) return (
    <Alert variant="destructive" className="m-6">
      <AlertTitle>Ошибка</AlertTitle>
      <AlertDescription>Справочник не найден</AlertDescription>
    </Alert>
  )

  const renderEntryCell = (entry: DirectoryEntry, field: DirectoryField) => {
    const v = entry.values[field.key]
    if (v === null || v === undefined || v === '') return <span className="text-muted-foreground">—</span>
    if (field.type === 'BOOLEAN') return <Checkbox checked={Boolean(v)} disabled />
    if (field.type === 'DATE') return <span>{dayjs(v as string).isValid() ? dayjs(v as string).format('DD.MM.YYYY') : String(v)}</span>
    return <span>{String(v)}</span>
  }

  const entryColumns = [
    ...directory.fields.map(f => ({
      title: (
        <span>
          {f.label || f.key}
          <span className="text-muted-foreground text-[11px] ml-1 font-mono">
            ({f.key})
          </span>
        </span>
      ),
      key: f.key,
      render: (_: unknown, entry: DirectoryEntry) => renderEntryCell(entry, f),
    })),
    {
      title: '',
      key: 'actions',
      width: 80,
      render: (_: unknown, entry: DirectoryEntry) => (
        <div className="flex gap-1">
          <Button variant="ghost" size="icon-sm" onClick={() => {
            setEditingEntry(entry)
            setEntryModalOpen(true)
          }}>
            <Pencil className="size-3.5" />
          </Button>
          <Button variant="ghost" size="icon-sm" onClick={() => setDeleteEntryId(entry.id)}>
            <Trash2 className="size-3.5 text-destructive" />
          </Button>
        </div>
      ),
    },
  ]

  return (
    <div className="page">
      <div className="page-head">
        <div>
          <div className="crumb">
            <Icon name="chev-left" size={12} />
            <span onClick={() => navigate('/directories')} style={{ cursor: 'pointer' }}>Справочники</span>
          </div>
          <h1>{directory.name}</h1>
          <p>
            {directory.entries.length} записей · {directory.fields.length} полей
          </p>
        </div>
        <div className="page-head-actions">
          <button className="btn"><Icon name="upload" size={14} /> Импорт CSV</button>
          <button className="btn" onClick={addField}><Icon name="settings" size={14} /> Поля</button>
          <button
            className="btn primary"
            onClick={() => { setEditingEntry(null); setEntryModalOpen(true) }}
            disabled={directory.fields.length === 0}
          >
            <Icon name="plus" size={14} /> Добавить запись
          </button>
        </div>
      </div>

      {/* Schema editor */}
      <Card className="mb-6">
        <CardHeader className="flex flex-row items-center justify-between">
          <CardTitle>Поля справочника</CardTitle>
          <div className="flex gap-2">
            <Button size="sm" variant="outline" onClick={addField}>
              <Plus className="mr-1 size-3.5" /> Добавить поле
            </Button>
            {schemaDirty && (
              <Button size="sm" onClick={saveSchema} disabled={!schemaValid || savingSchema}>
                {savingSchema ? 'Сохранение...' : 'Сохранить схему'}
              </Button>
            )}
          </div>
        </CardHeader>
        <CardContent>
          {schemaFields.length === 0 ? (
            <p className="text-sm text-muted-foreground">Нет полей. Добавьте поля, чтобы начать вносить записи.</p>
          ) : (
            <>
              {/* Header row */}
              <div className="flex gap-2 mb-1.5 pl-5">
                <span className="text-xs text-muted-foreground w-[150px]">Системный ключ</span>
                <span className="text-xs text-muted-foreground w-[180px]">Название</span>
                <span className="text-xs text-muted-foreground w-[120px]">Тип</span>
                <span className="text-xs text-muted-foreground">Варианты (для списка)</span>
              </div>
              {schemaFields.map((f, i) => (
                <FieldRow
                  key={i}
                  field={f}
                  existingKeys={allKeys}
                  onChange={updated => updateField(i, updated)}
                  onDeleteRequest={() => setDeleteFieldIdx(i)}
                />
              ))}
            </>
          )}
        </CardContent>
      </Card>

      {/* Entries */}
      <Card>
        <CardHeader className="flex flex-row items-center justify-between">
          <CardTitle>Записи ({directory.entries.length})</CardTitle>
          <Button
            size="sm"
            onClick={() => { setEditingEntry(null); setEntryModalOpen(true) }}
            disabled={directory.fields.length === 0}
          >
            <Plus className="mr-1 size-3.5" /> Добавить запись
          </Button>
        </CardHeader>
        <CardContent>
          {directory.fields.length === 0 ? (
            <Alert>
              <AlertTitle>Информация</AlertTitle>
              <AlertDescription>Сначала определите поля справочника</AlertDescription>
            </Alert>
          ) : directory.entries.length === 0 ? (
            <p className="text-sm text-muted-foreground">Нет записей</p>
          ) : (
            <div className="rounded-md border">
              <Table>
                <TableHeader>
                  <TableRow>
                    {entryColumns.map((col, i) => (
                      <TableHead key={col.key ?? i} style={{ width: 'width' in col ? col.width : undefined }}>
                        {col.title}
                      </TableHead>
                    ))}
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {directory.entries.map(entry => (
                    <TableRow key={entry.id}>
                      {entryColumns.map((col, i) => (
                        <TableCell key={col.key ?? i}>
                          {col.render(null as unknown as never, entry)}
                        </TableCell>
                      ))}
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
          )}
        </CardContent>
      </Card>

      <AlertDialog open={deleteFieldIdx !== null} onOpenChange={(open) => { if (!open) setDeleteFieldIdx(null) }}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Удалить поле?</AlertDialogTitle>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel onClick={() => setDeleteFieldIdx(null)}>Нет</AlertDialogCancel>
            <AlertDialogAction variant="destructive" onClick={() => { if (deleteFieldIdx !== null) removeField(deleteFieldIdx); setDeleteFieldIdx(null) }}>Да</AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      <AlertDialog open={deleteEntryId !== null} onOpenChange={(open) => { if (!open) setDeleteEntryId(null) }}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Удалить запись?</AlertDialogTitle>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel onClick={() => setDeleteEntryId(null)}>Нет</AlertDialogCancel>
            <AlertDialogAction variant="destructive" onClick={() => { if (deleteEntryId) handleDeleteEntry(deleteEntryId); setDeleteEntryId(null) }}>Да</AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      <EntryModal
        open={entryModalOpen}
        fields={directory.fields}
        initial={editingEntry}
        onSave={editingEntry ? handleUpdateEntry : handleAddEntry}
        onClose={() => { setEntryModalOpen(false); setEditingEntry(null) }}
      />
    </div>
  )
}
