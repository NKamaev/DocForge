import { useEffect, useMemo, useState } from 'react'
import {
  Button,
  Input,
  Switch,
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  Separator,
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from '@/components/ui'
import { Trash2, Plus, Play } from 'lucide-react'
import { DatePicker } from '@/components/ui/date-picker'
import type { Condition, ParameterType } from '../../types/templateModel'
import type { Parameter } from '../../types/template'
import type { DirectoryField } from '../../types/directory'
import { directoriesApi } from '../../api/directoriesApi'

type Operator = '==' | '!=' | '>' | '<' | '>=' | '<=' | 'in' | 'not_in' | 'is_empty' | 'is_not_empty'

const ALL_OPERATORS: { value: Operator; label: string; noValue?: boolean }[] = [
  { value: '==',          label: '= равно' },
  { value: '!=',          label: '≠ не равно' },
  { value: '>',           label: '> больше' },
  { value: '<',           label: '< меньше' },
  { value: '>=',          label: '≥ ≥' },
  { value: '<=',          label: '≤ ≤' },
  { value: 'in',          label: '∈ одно из' },
  { value: 'not_in',      label: '∉ не одно из' },
  { value: 'is_empty',    label: '∅ пусто',    noValue: true },
  { value: 'is_not_empty',label: '◉ не пусто', noValue: true },
]

const NUMERIC_OPERATORS: Operator[] = ['==', '!=', '>', '<', '>=', '<=', 'is_empty', 'is_not_empty']
const BOOLEAN_OPERATORS: Operator[] = ['==', '!=']
const TEXT_OPERATORS: Operator[] = ['==', '!=', 'in', 'not_in', 'is_empty', 'is_not_empty']

function operatorsFor(param: Parameter | undefined): typeof ALL_OPERATORS {
  if (!param) return ALL_OPERATORS
  switch (param.type) {
    case 'NUMBER':                return ALL_OPERATORS.filter((o) => NUMERIC_OPERATORS.includes(o.value))
    case 'DATE':                  return ALL_OPERATORS.filter((o) => NUMERIC_OPERATORS.includes(o.value))
    case 'BOOLEAN':               return ALL_OPERATORS.filter((o) => BOOLEAN_OPERATORS.includes(o.value))
    case 'TEXT': case 'FIO':      return ALL_OPERATORS.filter((o) => TEXT_OPERATORS.includes(o.value))
    case 'SELECT':                return ALL_OPERATORS.filter((o) => [...TEXT_OPERATORS].includes(o.value))
    default:                      return ALL_OPERATORS
  }
}

let _rowId = 0
const newRowId = () => `row-${++_rowId}`

type Connector = 'AND' | 'OR'

interface ConditionRow {
  id: string
  paramCode: string
  operator: Operator
  value: string
  connector: Connector
}

function rowsToExpression(rows: ConditionRow[]): string {
  return rows.map((row, i) => {
    const op = ALL_OPERATORS.find((o) => o.value === row.operator)
    const part = op?.noValue
      ? `${row.paramCode} ${row.operator}`
      : `${row.paramCode} ${row.operator} ${row.value}`
    return i < rows.length - 1 ? `${part} ${row.connector}` : part
  }).join(' ')
}

function expressionToRows(expr: string): ConditionRow[] {
  if (!expr.trim()) return []
  const tokens = expr.trim().split(/\s+/)
  const rows: ConditionRow[] = []
  let i = 0
  while (i < tokens.length) {
    const paramCode = tokens[i++] ?? ''
    const operator = (tokens[i++] ?? '==') as Operator
    const isNoValue = ALL_OPERATORS.find((o) => o.value === operator)?.noValue
    const value = isNoValue ? '' : (tokens[i++] ?? '')
    const connectorToken = tokens[i]?.toUpperCase()
    const connector: Connector = (connectorToken === 'AND' || connectorToken === 'OR') ? connectorToken : 'AND'
    if (connectorToken === 'AND' || connectorToken === 'OR') i++
    rows.push({ id: newRowId(), paramCode, operator, value, connector })
  }
  return rows.length ? rows : []
}

function ValueInput({ param, operator, value, onChange }: {
  param: Parameter | undefined
  operator: Operator
  value: string
  onChange: (v: string) => void
}) {
  const op = ALL_OPERATORS.find((o) => o.value === operator)
  if (op?.noValue) return <span className="text-muted-foreground text-xs">—</span>

  if (!param || param.type === 'TEXT' || param.type === 'FIO') {
    if (operator === 'in' || operator === 'not_in') {
      return (
        <Input
          value={value}
          onChange={(e) => onChange(e.target.value)}
          placeholder="Введите варианты (через запятую)"
          className="w-[120px] h-7 text-xs"
        />
      )
    }
    return <Input value={value} onChange={(e) => onChange(e.target.value)} className="w-[120px] h-7 text-xs" />
  }

  if (param.type === 'NUMBER') {
    return (
      <Input
        type="number"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        className="w-[90px] h-7 text-xs"
      />
    )
  }

  if (param.type === 'DATE') {
    return <DatePicker value={value} onChange={onChange} />
  }

  if (param.type === 'BOOLEAN') {
    return (
      <Switch
        size="sm"
        checked={value === 'true'}
        onCheckedChange={(checked) => onChange(String(checked))}
      />
    )
  }

  if (param.type === 'SELECT') {
    const options = (param.options ?? []).map((o) => ({ value: o, label: o }))
    if (operator === 'in' || operator === 'not_in') {
      return (
        <Input
          value={value}
          onChange={(e) => onChange(e.target.value)}
          placeholder="Введите варианты (через запятую)"
          className="w-[120px] h-7 text-xs"
        />
      )
    }
    return (
      <Select value={value || undefined} onValueChange={onChange}>
        <SelectTrigger size="sm" className="w-[120px]">
          <SelectValue placeholder="Выберите" />
        </SelectTrigger>
        <SelectContent>
          {options.map((opt) => (
            <SelectItem key={opt.value} value={opt.value}>
              {opt.label}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
    )
  }

  return <Input value={value} onChange={(e) => onChange(e.target.value)} className="w-[120px] h-7 text-xs" />
}

function SimpleBuilder({ rows, parameters, dirFields, onChange }: {
  rows: ConditionRow[]
  parameters: Parameter[]
  dirFields: Map<string, DirectoryField[]>
  onChange: (rows: ConditionRow[]) => void
}) {
  const expandedParams = useMemo<Parameter[]>(() => {
    const result: Parameter[] = []
    for (const p of parameters) {
      if (p.type !== 'DIRECTORY') {
        result.push(p)
        continue
      }
      const fields = dirFields.get(p.code) ?? []
      for (const fieldKey of (p.directoryFields ?? [])) {
        const def = fields.find((f) => f.key === fieldKey)
        result.push({
          code: `${p.code}__${fieldKey}`,
          label: `${p.label} → ${def?.label ?? fieldKey}`,
          type: (def?.type ?? 'TEXT') as ParameterType,
          required: false,
          options: def?.options ?? null,
        })
      }
    }
    return result
  }, [parameters, dirFields])

  const paramOptions = expandedParams.map((p) => ({ value: p.code, label: `${p.label} (${p.code})` }))

  const update = (id: string, patch: Partial<ConditionRow>) =>
    onChange(rows.map((r) => r.id === id ? { ...r, ...patch } : r))

  const addRow = () =>
    onChange([...rows, { id: newRowId(), paramCode: expandedParams[0]?.code ?? '', operator: '==', value: '', connector: 'AND' }])

  const removeRow = (id: string) => onChange(rows.filter((r) => r.id !== id))

  return (
    <div>
      {rows.map((row, i) => {
        const param = expandedParams.find((p) => p.code === row.paramCode)
        const ops = operatorsFor(param).map((o) => ({ value: o.value, label: o.label }))

        return (
          <div key={row.id}>
            {i > 0 && (
              <div className="flex justify-center my-1">
                <div className="flex border border-input rounded-md overflow-hidden">
                  <button
                    className={`px-3 py-1 text-sm font-medium transition-colors ${
                      rows[i - 1].connector === 'AND'
                        ? 'bg-primary text-primary-foreground'
                        : 'bg-background hover:bg-muted'
                    }`}
                    onClick={() => update(rows[i - 1].id, { connector: 'AND' })}
                  >
                    И
                  </button>
                  <button
                    className={`px-3 py-1 text-sm font-medium transition-colors ${
                      rows[i - 1].connector === 'OR'
                        ? 'bg-primary text-primary-foreground'
                        : 'bg-background hover:bg-muted'
                    }`}
                    onClick={() => update(rows[i - 1].id, { connector: 'OR' })}
                  >
                    ИЛИ
                  </button>
                </div>
              </div>
            )}

            <div className="flex items-center gap-1 mb-1">
              <Select
                value={row.paramCode || undefined}
                onValueChange={(v) => update(row.id, { paramCode: v, value: '', operator: '==' })}
              >
                <SelectTrigger size="sm" className="w-[130px]">
                  <SelectValue placeholder="Параметр" />
                </SelectTrigger>
                <SelectContent>
                  {paramOptions.map((opt) => (
                    <SelectItem key={opt.value} value={opt.value}>
                      {opt.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>

              <Select
                value={row.operator}
                onValueChange={(v) => update(row.id, { operator: v as Operator, value: '' })}
              >
                <SelectTrigger size="sm" className="w-[120px]">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {ops.map((opt) => (
                    <SelectItem key={opt.value} value={opt.value}>
                      {opt.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>

              <ValueInput
                param={param}
                operator={row.operator}
                value={row.value}
                onChange={(v) => update(row.id, { value: v })}
              />

              <Tooltip>
                <TooltipTrigger asChild>
                  <Button
                    variant="ghost"
                    size="icon-sm"
                    className="h-8 w-8 text-destructive"
                    onClick={() => removeRow(row.id)}
                    disabled={rows.length === 1}
                  >
                    <Trash2 className="size-4" />
                  </Button>
                </TooltipTrigger>
                <TooltipContent>Удалить строку</TooltipContent>
              </Tooltip>
            </div>
          </div>
        )
      })}

      <Button
        variant="outline"
        size="sm"
        className="mt-2 w-full"
        onClick={addRow}
      >
        <Plus className="size-4 mr-1" />
        Добавить условие
      </Button>

      {rows.length > 0 && (
        <div className="mt-2.5 p-1.5 bg-muted rounded-md font-mono text-xs text-muted-foreground">
          {rowsToExpression(rows)}
        </div>
      )}
    </div>
  )
}

export interface ConditionBuilderProps {
  open: boolean
  condition: Condition
  parameters: Parameter[]
  onSave: (condition: Condition) => void
  onClose: () => void
}

export default function ConditionBuilder({ open, condition, parameters, onSave, onClose }: ConditionBuilderProps) {
  const [mode, setMode] = useState<'simple' | 'python'>(condition.type === 'PYTHON' ? 'python' : 'simple')
  const [rows, setRows] = useState<ConditionRow[]>([])
  const [pythonCode, setPythonCode] = useState('')
  const [dirFields, setDirFields] = useState<Map<string, DirectoryField[]>>(new Map())
  const [testDialogOpen, setTestDialogOpen] = useState(false)

  useEffect(() => {
    if (!open) return

    setDirFields(new Map())

    const isSimple = condition.type !== 'PYTHON'
    setMode(isSimple ? 'simple' : 'python')
    if (isSimple) {
      const parsed = expressionToRows(condition.expression ?? '')
      const defaultCode = parameters.find((p) => p.type !== 'DIRECTORY')?.code ?? parameters[0]?.code ?? ''
      setRows(parsed.length ? parsed : [{ id: newRowId(), paramCode: defaultCode, operator: '==', value: '', connector: 'AND' }])
    } else {
      setPythonCode(condition.expression ?? '')
    }

    const dirParams = parameters.filter((p) => p.type === 'DIRECTORY' && p.directoryId)
    if (dirParams.length === 0) return

    let cancelled = false
    Promise.all(dirParams.map((p) => directoriesApi.getById(p.directoryId!)))
      .then((dirs) => {
        if (cancelled) return
        const map = new Map<string, DirectoryField[]>()
        dirParams.forEach((p, i) => map.set(p.code, dirs[i].fields))
        setDirFields(map)
      })
      .catch((e) => {
        if (!cancelled) console.warn('ConditionBuilder: не удалось загрузить поля справочников', e)
      })

    return () => { cancelled = true }
  }, [open])

  const handleSave = () => {
    if (mode === 'simple') {
      onSave({ type: 'SIMPLE', expression: rowsToExpression(rows), scriptId: null })
    } else {
      onSave({ type: 'PYTHON', expression: pythonCode, scriptId: condition.scriptId ?? null })
    }
    onClose()
  }

  const paramList = parameters.map((p) => p.code).join(', ')
  const pythonPlaceholder = `# Доступные переменные: ${paramList || 'параметры шаблона'}\n# Результат — переменная result (bool)\nresult = `

  return (
    <>
      <Dialog open={open} onOpenChange={onClose}>
        <DialogContent className="sm:max-w-[560px]">
          <DialogHeader>
            <DialogTitle>Настройка условия</DialogTitle>
          </DialogHeader>

          <div className="mt-3">
            <div className="flex justify-between items-center mb-4">
              <div className="flex border border-input rounded-md overflow-hidden">
                <button
                  className={`px-4 py-1.5 text-sm font-medium transition-colors ${
                    mode === 'simple'
                      ? 'bg-primary text-primary-foreground'
                      : 'bg-background hover:bg-muted'
                  }`}
                  onClick={() => setMode('simple')}
                >
                  Визуальный
                </button>
                <button
                  className={`px-4 py-1.5 text-sm font-medium transition-colors ${
                    mode === 'python'
                      ? 'bg-primary text-primary-foreground'
                      : 'bg-background hover:bg-muted'
                  }`}
                  onClick={() => setMode('python')}
                >
                  Python-скрипт
                </button>
              </div>

              {mode === 'simple' && (
                <span className="text-muted-foreground text-xs">
                  Условие видимости: выполняется когда все / одно из правил истинно
                </span>
              )}
            </div>

            <Separator className="my-4" />

            {mode === 'simple' ? (
              <SimpleBuilder rows={rows} parameters={parameters} dirFields={dirFields} onChange={setRows} />
            ) : (
              <div>
                <textarea
                  rows={7}
                  value={pythonCode}
                  onChange={(e) => setPythonCode(e.target.value)}
                  placeholder={pythonPlaceholder}
                  className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm font-mono ring-offset-background placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50"
                />
                <div className="mt-2 flex justify-end">
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => setTestDialogOpen(true)}
                  >
                    <Play className="size-4 mr-1" />
                    Тестировать
                  </Button>
                </div>
              </div>
            )}
          </div>

          <DialogFooter>
            <Button variant="outline" onClick={onClose}>
              Отмена
            </Button>
            <Button onClick={handleSave}>
              Сохранить
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={testDialogOpen} onOpenChange={setTestDialogOpen}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>Тестирование скрипта</DialogTitle>
            <DialogDescription>
              Функция тестирования будет доступна в следующей версии.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button onClick={() => setTestDialogOpen(false)}>
              Понятно
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  )
}
