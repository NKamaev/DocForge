import { useEffect, useMemo, useState } from 'react'
import {
  Button, DatePicker, Divider, Input, InputNumber, Modal,
  Popconfirm, Radio, Select, Space, Switch, Tooltip, Typography,
} from 'antd'
import { DeleteOutlined, PlusOutlined, ExperimentOutlined } from '@ant-design/icons'
import dayjs from 'dayjs'
import type { Condition, ParameterType } from '../../types/templateModel'
import type { Parameter } from '../../types/template'
import type { DirectoryField } from '../../types/directory'
import { directoriesApi } from '../../api/directoriesApi'

const { Text } = Typography

// ── Operator definitions ──────────────────────────────────────────────────────

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

// ── Internal row type ─────────────────────────────────────────────────────────

let _rowId = 0
const newRowId = () => `row-${++_rowId}`

type Connector = 'AND' | 'OR'

interface ConditionRow {
  id: string
  paramCode: string
  operator: Operator
  value: string
  connector: Connector  // connector between this and the NEXT row
}

// ── Expression serialisation ──────────────────────────────────────────────────

function rowsToExpression(rows: ConditionRow[]): string {
  return rows.map((row, i) => {
    const op = ALL_OPERATORS.find((o) => o.value === row.operator)
    const part = op?.noValue
      ? `${row.paramCode} ${row.operator}`
      : `${row.paramCode} ${row.operator} ${row.value}`
    return i < rows.length - 1 ? `${part} ${row.connector}` : part
  }).join(' ')
}

// Best-effort parse: "code op value CONNECTOR code op value ..."
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

// ── Value input — adapts to parameter type ────────────────────────────────────

function ValueInput({ param, operator, value, onChange }: {
  param: Parameter | undefined
  operator: Operator
  value: string
  onChange: (v: string) => void
}) {
  const op = ALL_OPERATORS.find((o) => o.value === operator)
  if (op?.noValue) return <Text type="secondary" style={{ fontSize: 12 }}>—</Text>

  if (!param || param.type === 'TEXT' || param.type === 'FIO') {
    if (operator === 'in' || operator === 'not_in') {
      return (
        <Select
          mode="tags" size="small" style={{ minWidth: 120 }}
          value={value ? value.split(',').map((s) => s.trim()) : []}
          onChange={(vals: string[]) => onChange(vals.join(', '))}
          placeholder="Введите варианты"
          open={false}
          tokenSeparators={[',']}
        />
      )
    }
    return <Input size="small" value={value} onChange={(e) => onChange(e.target.value)} style={{ width: 120 }} />
  }

  if (param.type === 'NUMBER') {
    return (
      <InputNumber
        size="small" style={{ width: 90 }}
        value={value ? Number(value) : undefined}
        onChange={(v) => onChange(v != null ? String(v) : '')}
      />
    )
  }

  if (param.type === 'DATE') {
    return (
      <DatePicker
        size="small" style={{ width: 130 }}
        value={value ? dayjs(value) : null}
        onChange={(d) => onChange(d ? d.format('YYYY-MM-DD') : '')}
        format="DD.MM.YYYY"
      />
    )
  }

  if (param.type === 'BOOLEAN') {
    return (
      <Switch
        size="small"
        checked={value === 'true'}
        onChange={(checked) => onChange(String(checked))}
        checkedChildren="Да" unCheckedChildren="Нет"
      />
    )
  }

  if (param.type === 'SELECT') {
    const options = (param.options ?? []).map((o) => ({ value: o, label: o }))
    if (operator === 'in' || operator === 'not_in') {
      return (
        <Select
          mode="multiple" size="small" style={{ minWidth: 120 }}
          value={value ? value.split(',').map((s) => s.trim()) : []}
          onChange={(vals: string[]) => onChange(vals.join(', '))}
          options={options}
        />
      )
    }
    return (
      <Select
        size="small" style={{ width: 120 }}
        value={value || undefined}
        onChange={onChange}
        options={options}
        allowClear
      />
    )
  }

  return <Input size="small" value={value} onChange={(e) => onChange(e.target.value)} style={{ width: 120 }} />
}

// ── Simple mode builder ───────────────────────────────────────────────────────

function SimpleBuilder({ rows, parameters, dirFields, onChange }: {
  rows: ConditionRow[]
  parameters: Parameter[]
  dirFields: Map<string, DirectoryField[]>
  onChange: (rows: ConditionRow[]) => void
}) {
  // Expand DIRECTORY params into virtual Parameter objects for each sub-field
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
            {/* Connector between rows */}
            {i > 0 && (
              <div style={{ display: 'flex', justifyContent: 'center', margin: '4px 0' }}>
                <Radio.Group
                  size="small"
                  value={rows[i - 1].connector}
                  onChange={(e) => update(rows[i - 1].id, { connector: e.target.value as Connector })}
                  optionType="button"
                  buttonStyle="solid"
                  options={[{ value: 'AND', label: 'И' }, { value: 'OR', label: 'ИЛИ' }]}
                />
              </div>
            )}

            {/* Row */}
            <Space size={4} style={{ width: '100%' }}>
              <Select
                size="small" style={{ width: 130 }}
                options={paramOptions}
                value={row.paramCode || undefined}
                onChange={(v) => update(row.id, { paramCode: v, value: '', operator: '==' })}
                placeholder="Параметр"
                showSearch
                filterOption={(input, opt) => String(opt?.label ?? '').toLowerCase().includes(input.toLowerCase())}
              />
              <Select
                size="small" style={{ width: 120 }}
                options={ops}
                value={row.operator}
                onChange={(v) => update(row.id, { operator: v as Operator, value: '' })}
              />
              <ValueInput
                param={param}
                operator={row.operator}
                value={row.value}
                onChange={(v) => update(row.id, { value: v })}
              />
              <Tooltip title="Удалить строку">
                <Button
                  type="text" size="small" danger
                  icon={<DeleteOutlined />}
                  onClick={() => removeRow(row.id)}
                  disabled={rows.length === 1}
                />
              </Tooltip>
            </Space>
          </div>
        )
      })}

      <Button
        type="dashed" size="small" icon={<PlusOutlined />}
        onClick={addRow}
        style={{ marginTop: 8, width: '100%' }}
      >
        Добавить условие
      </Button>

      {rows.length > 0 && (
        <div style={{ marginTop: 10, padding: '6px 10px', background: '#f5f5f5', borderRadius: 4, fontFamily: 'monospace', fontSize: 12, color: '#595959' }}>
          {rowsToExpression(rows)}
        </div>
      )}
    </div>
  )
}

// ── Main component ────────────────────────────────────────────────────────────

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

  // Initialise state when modal opens
  useEffect(() => {
    if (!open) return

    // Reset stale directory data from previous open
    setDirFields(new Map())

    const isSimple = condition.type !== 'PYTHON'
    setMode(isSimple ? 'simple' : 'python')
    if (isSimple) {
      const parsed = expressionToRows(condition.expression ?? '')
      // Use first non-DIRECTORY param as default, or empty string; addRow uses expandedParams[0]
      const defaultCode = parameters.find((p) => p.type !== 'DIRECTORY')?.code ?? parameters[0]?.code ?? ''
      setRows(parsed.length ? parsed : [{ id: newRowId(), paramCode: defaultCode, operator: '==', value: '', connector: 'AND' }])
    } else {
      setPythonCode(condition.expression ?? '')
    }

    // Fetch directory definitions for DIRECTORY parameters
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
  }, [open]) // eslint-disable-line react-hooks/exhaustive-deps

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
    <Modal
      title="Настройка условия"
      open={open}
      onOk={handleSave}
      onCancel={onClose}
      okText="Сохранить"
      cancelText="Отмена"
      destroyOnHidden
      width={560}
    >
      <div style={{ marginTop: 12 }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
          <Radio.Group
            value={mode}
            onChange={(e) => setMode(e.target.value as 'simple' | 'python')}
            optionType="button"
            buttonStyle="solid"
            options={[
              { value: 'simple', label: 'Визуальный' },
              { value: 'python', label: 'Python-скрипт' },
            ]}
          />
          {mode === 'simple' && (
            <Text type="secondary" style={{ fontSize: 12 }}>
              Условие видимости: выполняется когда все / одно из правил истинно
            </Text>
          )}
        </div>

        <Divider style={{ margin: '0 0 16px' }} />

        {mode === 'simple' ? (
          <SimpleBuilder rows={rows} parameters={parameters} dirFields={dirFields} onChange={setRows} />
        ) : (
          <div>
            <Input.TextArea
              rows={7}
              value={pythonCode}
              onChange={(e) => setPythonCode(e.target.value)}
              placeholder={pythonPlaceholder}
              style={{ fontFamily: 'monospace', fontSize: 13 }}
            />
            <div style={{ marginTop: 8, display: 'flex', justifyContent: 'flex-end' }}>
              <Popconfirm
                title="Тестирование скрипта"
                description="Функция тестирования будет доступна в следующей версии."
                showCancel={false}
                okText="Понятно"
              >
                <Button size="small" icon={<ExperimentOutlined />}>
                  Тестировать
                </Button>
              </Popconfirm>
            </div>
          </div>
        )}
      </div>
    </Modal>
  )
}
