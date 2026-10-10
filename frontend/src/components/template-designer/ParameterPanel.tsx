import { useEffect, useState } from 'react'
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
  Checkbox,
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
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from '@/components/ui'
import {
  DndContext, closestCenter, PointerSensor, useSensor, useSensors,
} from '@dnd-kit/core'
import type { DragEndEvent } from '@dnd-kit/core'
import {
  SortableContext, useSortable, verticalListSortingStrategy, arrayMove,
} from '@dnd-kit/sortable'
import { CSS } from '@dnd-kit/utilities'
import { GripVertical, Pencil, Plus, Trash2 } from 'lucide-react'
import type { Parameter } from '../../types/template'
import type { ParameterType, ParameterFormat } from '../../types/templateModel'
import type { DirectoryListItem, DirectoryField } from '../../types/directory'
import { directoriesApi } from '../../api/directoriesApi'
import ParameterFormatSelector from './ParameterFormatSelector'

// ── Constants ─────────────────────────────────────────────────────────────────

const TYPE_OPTIONS: { value: ParameterType; label: string }[] = [
  { value: 'TEXT',      label: 'Текст'      },
  { value: 'NUMBER',    label: 'Число'       },
  { value: 'DATE',      label: 'Дата'        },
  { value: 'FIO',       label: 'ФИО'         },
  { value: 'BOOLEAN',   label: 'Да/Нет'      },
  { value: 'SELECT',    label: 'Список'      },
  { value: 'DIRECTORY', label: 'Справочник'  },
]

const TYPE_CLASSES: Record<string, string> = {
  TEXT: 'bg-blue-100 text-blue-700',
  NUMBER: 'bg-orange-100 text-orange-700',
  DATE: 'bg-green-100 text-green-700',
  FIO: 'bg-purple-100 text-purple-700',
  BOOLEAN: 'bg-cyan-100 text-cyan-700',
  SELECT: 'bg-pink-100 text-pink-700',
  DIRECTORY: 'bg-indigo-100 text-indigo-700',
}

const CODE_PATTERN = /^[a-z][a-z0-9_]*$/

// ── Form values type ──────────────────────────────────────────────────────────

interface ParameterFormValues {
  code: string
  label: string
  type: ParameterType
  required: boolean
  defaultValue?: string
  options: string[]
  validationMin?: number
  validationMax?: number
  validationPattern?: string
  directoryId?: string
  directoryFields: string[]
  format?: ParameterFormat | null
}

function formToParameter(v: ParameterFormValues): Parameter {
  return {
    code: v.code,
    label: v.label,
    type: v.type,
    required: v.required ?? false,
    defaultValue: v.defaultValue ?? null,
    options: v.type === 'SELECT' ? (v.options ?? []) : null,
    validation: (v.validationMin != null || v.validationMax != null || v.validationPattern)
      ? { min: v.validationMin ?? null, max: v.validationMax ?? null, pattern: v.validationPattern ?? null }
      : null,
    directoryId: v.type === 'DIRECTORY' ? (v.directoryId ?? null) : null,
    directoryFields: v.type === 'DIRECTORY' ? (v.directoryFields ?? []) : null,
    format: v.format ?? null,
  }
}

function parameterToForm(p: Parameter): ParameterFormValues {
  return {
    code: p.code,
    label: p.label,
    type: p.type,
    required: p.required,
    defaultValue: p.defaultValue != null ? String(p.defaultValue) : undefined,
    options: p.options ?? [],
    validationMin: p.validation?.min ?? undefined,
    validationMax: p.validation?.max ?? undefined,
    validationPattern: p.validation?.pattern ?? undefined,
    directoryId: p.directoryId ?? undefined,
    directoryFields: p.directoryFields ?? [],
    format: p.format ?? null,
  }
}

// ── Sortable row ──────────────────────────────────────────────────────────────

interface SortableRowProps {
  parameter: Parameter
  onEdit: () => void
  onDeleteRequest: () => void
  readOnly?: boolean
}

function SortableRow({ parameter, onEdit, onDeleteRequest, readOnly }: SortableRowProps) {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } =
    useSortable({ id: parameter.code })

  const style = {
    transform: CSS.Transform.toString(transform),
    transition,
    opacity: isDragging ? 0.5 : 1,
    background: isDragging ? '#f0f5ff' : 'transparent',
  }

  return (
    <div
      ref={setNodeRef}
      style={{ ...style, padding: '8px 0', display: 'flex', alignItems: 'flex-start', gap: 8 }}
    >
      {/* Drag handle */}
      <span
        {...attributes}
        {...(readOnly ? {} : listeners)}
        data-testid="drag-handle"
        style={{ cursor: readOnly ? 'default' : 'grab', color: '#bbb', padding: '2px 4px', display: 'flex', alignItems: 'center', flexShrink: 0 }}
      >
        <GripVertical className="size-4" />
      </span>

      <div style={{ flex: 1, minWidth: 0 }}>
        <div className="flex items-center gap-1.5 flex-wrap">
          <span style={{ fontSize: 13, fontWeight: 600 }}>{parameter.label}</span>
          {parameter.required && <span className="size-2 rounded-full bg-red-500 inline-block" title="Обязательный" />}
        </div>
        <div className="flex items-center gap-1.5 flex-wrap mt-0.5">
          <Badge variant="outline" className={`text-[11px] ${TYPE_CLASSES[parameter.type] ?? ''}`}>
            {parameter.type}
          </Badge>
          <span style={{ fontSize: 12, fontFamily: 'monospace', color: 'var(--ink-3)' }}>
            {parameter.code}
          </span>
        </div>
      </div>

      {!readOnly && (
        <div className="flex gap-0.5 shrink-0">
          <TooltipProvider>
            <Tooltip>
              <TooltipTrigger asChild>
                <Button variant="ghost" size="icon-sm" onClick={onEdit}>
                  <Pencil className="size-3.5" />
                </Button>
              </TooltipTrigger>
              <TooltipContent>Редактировать</TooltipContent>
            </Tooltip>
          </TooltipProvider>

          <Button variant="ghost" size="icon-sm" onClick={onDeleteRequest}>
            <Trash2 className="size-3.5 text-destructive" />
          </Button>
        </div>
      )}
    </div>
  )
}

// ── Parameter form (inside modal) ─────────────────────────────────────────────

interface ParameterFormInnerProps {
  values: ParameterFormValues
  onChange: (v: ParameterFormValues) => void
  existingCodes: string[]
  editingCode?: string
  errors: Record<string, string>
}

function ParameterFormFields({ values, onChange, existingCodes, editingCode, errors }: ParameterFormInnerProps) {
  const [directories, setDirectories] = useState<DirectoryListItem[]>([])
  const [dirFields, setDirFields] = useState<DirectoryField[]>([])
  const [fieldsLoading, setFieldsLoading] = useState(false)

  // Load directory list once when DIRECTORY type is selected
  useEffect(() => {
    if (values.type !== 'DIRECTORY') return
    directoriesApi.list()
      .then(setDirectories)
  }, [values.type])

  // Load directory fields when a directory is selected
  useEffect(() => {
    if (!values.directoryId) { setDirFields([]); return }
    setFieldsLoading(true)
    directoriesApi.getById(values.directoryId)
      .then(d => setDirFields(d.fields))
      .finally(() => setFieldsLoading(false))
  }, [values.directoryId])

  const set = (partial: Partial<ParameterFormValues>) => onChange({ ...values, ...partial })

  const codeError =
    !values.code
      ? 'Введите код'
      : !CODE_PATTERN.test(values.code)
        ? 'Только строчные латинские буквы, цифры и _'
        : existingCodes.includes(values.code) && values.code !== editingCode
          ? 'Код уже используется'
          : null

  return (
    <div className="space-y-4">
      {/* Code */}
      <div className="space-y-1.5">
        <Label htmlFor="param-code">
          Код <span className="text-destructive">*</span>
          <span className="text-xs text-muted-foreground ml-1">(snake_case, только латиница: client_name)</span>
        </Label>
        <Input
          id="param-code"
          value={values.code}
          onChange={e => set({ code: e.target.value })}
          placeholder="client_name"
          className={`font-mono ${codeError ? 'border-destructive' : ''}`}
        />
        {codeError && <p className="text-xs text-destructive">{codeError}</p>}
      </div>

      {/* Label */}
      <div className="space-y-1.5">
        <Label htmlFor="param-label">Название <span className="text-destructive">*</span></Label>
        <Input
          id="param-label"
          value={values.label}
          onChange={e => set({ label: e.target.value })}
          placeholder="ФИО клиента"
        />
        {errors.label && <p className="text-xs text-destructive">{errors.label}</p>}
      </div>

      {/* Type */}
      <div className="space-y-1.5">
        <Label>Тип <span className="text-destructive">*</span></Label>
        <Select value={values.type} onValueChange={(v: ParameterType) => set({ type: v, options: v === 'SELECT' ? (values.options ?? []) : [] })}>
          <SelectTrigger className="w-full">
            <SelectValue placeholder="Выберите тип" />
          </SelectTrigger>
          <SelectContent>
            {TYPE_OPTIONS.map(t => (
              <SelectItem key={t.value} value={t.value}>{t.label}</SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      {/* Required */}
      <div className="flex items-center gap-2">
        <Checkbox
          id="param-required"
          checked={values.required}
          onCheckedChange={(checked) => set({ required: checked === true })}
        />
        <Label htmlFor="param-required" className="font-normal">Обязательный</Label>
      </div>

      {/* SELECT options */}
      {values.type === 'SELECT' && (
        <div className="space-y-1.5">
          <Label>Варианты</Label>
          {values.options.map((opt, i) => (
            <div key={i} className="flex items-center gap-2">
              <Input
                value={opt}
                onChange={e => {
                  const opts = [...values.options]
                  opts[i] = e.target.value
                  set({ options: opts })
                }}
                placeholder="Вариант"
              />
              <Button variant="ghost" size="icon-sm" onClick={() => set({ options: values.options.filter((_, j) => j !== i) })}>
                <Trash2 className="size-3.5 text-destructive" />
              </Button>
            </div>
          ))}
          <Button variant="outline" size="sm" onClick={() => set({ options: [...values.options, ''] })}>
            <Plus className="mr-1 size-3.5" /> Добавить вариант
          </Button>
        </div>
      )}

      {/* DIRECTORY fields */}
      {values.type === 'DIRECTORY' && (
        <>
          <div className="space-y-1.5">
            <Label>Справочник <span className="text-destructive">*</span></Label>
            <Select
              value={values.directoryId ?? ''}
              onValueChange={v => set({ directoryId: v || undefined, directoryFields: [] })}
            >
              <SelectTrigger className="w-full">
                <SelectValue placeholder="Выберите справочник" />
              </SelectTrigger>
              <SelectContent>
                {directories.map(d => (
                  <SelectItem key={d.id} value={d.id}>{d.name}</SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          {values.directoryId && (
            <div className="space-y-1.5">
              <Label>
                Поля для подстановки
                <span className="text-xs text-muted-foreground ml-1">(будут доступны в теле шаблона как отдельные параметры)</span>
              </Label>
              {fieldsLoading ? (
                <p className="text-sm text-muted-foreground">Загрузка...</p>
              ) : (
                <div className="flex flex-col gap-1.5">
                  {dirFields.map(f => (
                    <label key={f.key} className="flex items-center gap-2 text-sm cursor-pointer">
                      <Checkbox
                        checked={values.directoryFields.includes(f.key)}
                        onCheckedChange={(checked) => {
                          const next = checked
                            ? [...values.directoryFields, f.key]
                            : values.directoryFields.filter(k => k !== f.key)
                          set({ directoryFields: next })
                        }}
                      />
                      <span>{f.label || f.key}</span>
                    </label>
                  ))}
                </div>
              )}
            </div>
          )}
        </>
      )}

      {/* Default value (not for DIRECTORY) */}
      {values.type !== 'DIRECTORY' && (
        <div className="space-y-1.5">
          <Label>Значение по умолчанию</Label>
          {values.type === 'BOOLEAN' ? (
            <Select value={values.defaultValue ?? ''} onValueChange={v => set({ defaultValue: v || undefined })}>
              <SelectTrigger className="w-full">
                <SelectValue placeholder="Выберите..." />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="true">Да</SelectItem>
                <SelectItem value="false">Нет</SelectItem>
              </SelectContent>
            </Select>
          ) : values.type === 'SELECT' ? (
            <Input value={values.defaultValue ?? ''} onChange={e => set({ defaultValue: e.target.value })} placeholder="Один из вариантов выше" />
          ) : (
            <Input value={values.defaultValue ?? ''} onChange={e => set({ defaultValue: e.target.value })} placeholder={values.type === 'DATE' ? '$TODAY или дата' : 'Значение'} />
          )}
        </div>
      )}

      {/* Validation (TEXT/NUMBER) */}
      {(values.type === 'TEXT' || values.type === 'NUMBER') && (
        <>
          {values.type === 'NUMBER' && (
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1.5">
                <Label>Мин</Label>
                <Input
                  type="number"
                  value={values.validationMin ?? ''}
                  onChange={e => set({ validationMin: e.target.value ? Number(e.target.value) : undefined })}
                />
              </div>
              <div className="space-y-1.5">
                <Label>Макс</Label>
                <Input
                  type="number"
                  value={values.validationMax ?? ''}
                  onChange={e => set({ validationMax: e.target.value ? Number(e.target.value) : undefined })}
                />
              </div>
            </div>
          )}
          {values.type === 'TEXT' && (
            <div className="space-y-1.5">
              <Label>Паттерн (regex)</Label>
              <Input
                value={values.validationPattern ?? ''}
                onChange={e => set({ validationPattern: e.target.value || undefined })}
                placeholder="^[А-ЯЁ].*"
                className="font-mono"
              />
            </div>
          )}
        </>
      )}

      {/* Format selector */}
      {(values.type === 'DATE' || values.type === 'NUMBER' || values.type === 'FIO') && (
        <div className="space-y-1.5">
          <Label>
            Формат отображения
            <span className="text-xs text-muted-foreground ml-1">(как значение будет выглядеть в готовом документе)</span>
          </Label>
          <ParameterFormatSelector
            paramType={values.type}
            value={values.format ?? null}
            onChange={fmt => set({ format: fmt })}
          />
        </div>
      )}
    </div>
  )
}

// ── Main component ────────────────────────────────────────────────────────────

interface Props {
  parameters: Parameter[]
  onChange: (params: Parameter[]) => void
  /** Set of parameter codes currently referenced in the editor body */
  usedCodes: Set<string>
  readOnly?: boolean
}

export default function ParameterPanel({ parameters, onChange, usedCodes, readOnly }: Props) {
  const [modalOpen, setModalOpen] = useState(false)
  const [editingCode, setEditingCode] = useState<string | null>(null)
  const [deleteParamCode, setDeleteParamCode] = useState<string | null>(null)
  const [formValues, setFormValues] = useState<ParameterFormValues>({
    code: '', label: '', type: 'TEXT', required: false, options: [], directoryFields: [], format: null,
  })
  const [formErrors, setFormErrors] = useState<Record<string, string>>({})

  const sensors = useSensors(useSensor(PointerSensor, { activationConstraint: { distance: 5 } }))

  const existingCodes = parameters.map((p) => p.code)

  // ── Drag end ──
  const handleDragEnd = (event: DragEndEvent) => {
    const { active, over } = event
    if (!over || active.id === over.id) return
    const oldIdx = parameters.findIndex((p) => p.code === active.id)
    const newIdx = parameters.findIndex((p) => p.code === over.id)
    onChange(arrayMove(parameters, oldIdx, newIdx))
  }

  // ── Open add modal ──
  const openAdd = () => {
    setEditingCode(null)
    setFormValues({ code: '', label: '', type: 'TEXT', required: false, options: [], directoryFields: [], format: null })
    setFormErrors({})
    setModalOpen(true)
  }

  // ── Open edit modal ──
  const openEdit = (param: Parameter) => {
    setEditingCode(param.code)
    setFormValues(parameterToForm(param))
    setFormErrors({})
    setModalOpen(true)
  }

  // ── Save ──
  const handleSave = () => {
    // Validate
    const errors: Record<string, string> = {}
    if (!formValues.code.trim()) errors.code = 'Введите код'
    else if (!CODE_PATTERN.test(formValues.code)) errors.code = 'Только строчные латинские буквы, цифры и _'
    else if (existingCodes.includes(formValues.code) && formValues.code !== editingCode) errors.code = 'Код уже используется'
    if (!formValues.label.trim()) errors.label = 'Введите название'

    if (Object.keys(errors).length > 0) {
      setFormErrors(errors)
      return
    }

    const next = formToParameter({ ...formValues, format: formValues.format })

    if (editingCode) {
      onChange(parameters.map((p) => (p.code === editingCode ? next : p)))
    } else {
      onChange([...parameters, next])
    }
    setModalOpen(false)
  }

  // ── Delete ──
  const handleDelete = (code: string) => {
    onChange(parameters.filter((p) => p.code !== code))
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
        <span style={{ fontWeight: 600, fontSize: 14 }}>Параметры ({parameters.length})</span>
        {!readOnly && (
          <Button size="sm" onClick={openAdd}>
            <Plus className="mr-1 size-3.5" /> Добавить
          </Button>
        )}
      </div>

      {parameters.length === 0 ? (
        <span style={{ fontSize: 13, color: 'var(--ink-3)' }}>
          {readOnly ? 'Нет параметров.' : 'Нет параметров. Нажмите «Добавить» чтобы создать первый.'}
        </span>
      ) : (
        <div style={{ flex: 1, overflow: 'auto' }}>
          <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={handleDragEnd}>
            <SortableContext items={parameters.map((p) => p.code)} strategy={verticalListSortingStrategy}>
              {parameters.map((param) => (
                  <SortableRow
                    key={param.code}
                    parameter={param}
                    onEdit={() => openEdit(param)}
                    onDeleteRequest={() => setDeleteParamCode(param.code)}
                    readOnly={readOnly}
                  />
              ))}
            </SortableContext>
          </DndContext>
        </div>
      )}

      <AlertDialog open={deleteParamCode !== null} onOpenChange={(open) => { if (!open) setDeleteParamCode(null) }}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>
              {deleteParamCode && usedCodes.has(deleteParamCode)
                ? 'Параметр используется в теле шаблона'
                : 'Удалить параметр?'}
            </AlertDialogTitle>
            {deleteParamCode && usedCodes.has(deleteParamCode) && (
              <AlertDialogDescription>
                При удалении ноды parameter_field для этого параметра останутся в тексте. Продолжить?
              </AlertDialogDescription>
            )}
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel onClick={() => setDeleteParamCode(null)}>Отмена</AlertDialogCancel>
            <AlertDialogAction variant="destructive" onClick={() => { if (deleteParamCode) handleDelete(deleteParamCode); setDeleteParamCode(null) }}>
              {deleteParamCode && usedCodes.has(deleteParamCode) ? 'Удалить' : 'Да'}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      <Dialog open={modalOpen} onOpenChange={(open) => { if (!open) setModalOpen(false) }}>
        <DialogContent className="sm:max-w-[480px]">
          <DialogHeader>
            <DialogTitle>{editingCode ? 'Редактировать параметр' : 'Новый параметр'}</DialogTitle>
          </DialogHeader>
          <div className="mt-4">
            <ParameterFormFields
              values={formValues}
              onChange={setFormValues}
              existingCodes={existingCodes}
              editingCode={editingCode ?? undefined}
              errors={formErrors}
            />
          </div>
          <DialogFooter className="mt-4">
            <Button variant="outline" onClick={() => setModalOpen(false)}>Отмена</Button>
            <Button onClick={handleSave}>Сохранить</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
