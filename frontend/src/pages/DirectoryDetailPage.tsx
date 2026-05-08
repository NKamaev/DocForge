import { useEffect, useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import {
  Alert, Button, Card, Checkbox, DatePicker, Form, Input, InputNumber,
  Modal, Popconfirm, Select, Space, Spin, Switch, Table, Typography, notification,
} from 'antd'
import {
  PlusOutlined, DeleteOutlined, EditOutlined, HolderOutlined,
} from '@ant-design/icons'
import { Icon } from '../components/Icon'
import dayjs from 'dayjs'
import { directoriesApi } from '../api/directoriesApi'
import type { Directory, DirectoryEntry, DirectoryField, DirectoryFieldType } from '../types/directory'

const { Text } = Typography

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
  onDelete: () => void
}

function FieldRow({ field, existingKeys, onChange, onDelete }: FieldRowProps) {
  const keyError = !KEY_PATTERN.test(field.key)
    ? 'Только строчные буквы, цифры и _'
    : existingKeys.filter(k => k === field.key).length > 1
    ? 'Код уже используется'
    : null

  return (
    <div style={{ display: 'flex', gap: 8, alignItems: 'flex-start', marginBottom: 12 }}>
      <HolderOutlined style={{ color: '#bfbfbf', cursor: 'grab', marginTop: 6 }} />

      {/* Key */}
      <div style={{ width: 150 }}>
        <Input
          value={field.key}
          onChange={e => onChange({ ...field, key: e.target.value })}
          placeholder="field_key"
          style={{ fontFamily: 'monospace', borderColor: keyError ? '#ff4d4f' : undefined }}
        />
        {keyError && <div style={{ color: '#ff4d4f', fontSize: 11, marginTop: 2 }}>{keyError}</div>}
      </div>

      {/* Label */}
      <Input
        value={field.label}
        onChange={e => onChange({ ...field, label: e.target.value })}
        placeholder="Название поля"
        style={{ width: 180 }}
      />

      {/* Type */}
      <Select
        value={field.type}
        onChange={type => onChange({ ...field, type, options: type === 'SELECT' ? (field.options ?? []) : undefined })}
        options={FIELD_TYPE_OPTIONS}
        style={{ width: 120 }}
      />

      {/* Options (SELECT only) */}
      {field.type === 'SELECT' && (
        <Select
          mode="tags"
          value={field.options ?? []}
          onChange={options => onChange({ ...field, options })}
          placeholder="Добавьте варианты..."
          style={{ flex: 1, minWidth: 160 }}
          tokenSeparators={[',']}
        />
      )}

      <Popconfirm title="Удалить поле?" onConfirm={onDelete} okText="Да" cancelText="Нет">
        <Button size="small" danger icon={<DeleteOutlined />} style={{ marginTop: 2 }} />
      </Popconfirm>
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
        <InputNumber
          style={{ width: '100%' }}
          value={value as number | undefined}
          onChange={onChange}
        />
      )
    case 'DATE':
      return (
        <DatePicker
          style={{ width: '100%' }}
          value={value ? dayjs(value as string) : null}
          onChange={d => onChange(d ? d.format('YYYY-MM-DD') : null)}
          format="DD.MM.YYYY"
        />
      )
    case 'BOOLEAN':
      return (
        <Switch
          checked={Boolean(value)}
          onChange={onChange}
          checkedChildren="Да"
          unCheckedChildren="Нет"
        />
      )
    case 'SELECT':
      return (
        <Select
          style={{ width: '100%' }}
          value={value as string | undefined}
          onChange={onChange}
          options={(field.options ?? []).map(o => ({ value: o, label: o }))}
          allowClear
          placeholder="Выберите..."
        />
      )
    default:
      return (
        <Input
          value={value as string | undefined}
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
    <Modal
      title={initial ? 'Редактировать запись' : 'Новая запись'}
      open={open}
      onOk={handleOk}
      onCancel={onClose}
      confirmLoading={saving}
      okText="Сохранить" cancelText="Отмена"
      destroyOnHidden
      width={520}
    >
      <div style={{ marginTop: 16 }}>
        {fields.map(f => (
          <Form.Item key={f.key} label={f.label || f.key} style={{ marginBottom: 12 }}>
            <FieldInput
              field={f}
              value={values[f.key]}
              onChange={v => setValues(prev => ({ ...prev, [f.key]: v }))}
            />
          </Form.Item>
        ))}
      </div>
    </Modal>
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

  const load = () => {
    if (!id) return
    directoriesApi.getById(id)
      .then(d => {
        setDirectory(d)
        setSchemaFields(d.fields)
        setSchemaDirty(false)
      })
      .catch(() => notification.error({ message: 'Справочник не найден' }))
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
      notification.success({ message: 'Схема сохранена' })
    } catch {
      notification.error({ message: 'Ошибка сохранения схемы' })
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
      notification.error({ message: 'Ошибка удаления записи' })
    }
  }

  // ── Render ──────────────────────────────────────────────────────────────────

  if (loading) return <div style={{ padding: 64, textAlign: 'center' }}><Spin size="large" /></div>
  if (!directory) return <Alert type="error" message="Справочник не найден" style={{ margin: 24 }} />

  const renderEntryCell = (entry: DirectoryEntry, field: DirectoryField) => {
    const v = entry.values[field.key]
    if (v === null || v === undefined || v === '') return <Text type="secondary">—</Text>
    if (field.type === 'BOOLEAN') return <Checkbox checked={Boolean(v)} disabled />
    if (field.type === 'DATE') return <span>{dayjs(v as string).isValid() ? dayjs(v as string).format('DD.MM.YYYY') : String(v)}</span>
    return <span>{String(v)}</span>
  }

  const entryColumns = [
    ...directory.fields.map(f => ({
      title: (
        <span>
          {f.label || f.key}
          <span style={{ color: '#999', fontSize: 11, marginLeft: 4, fontFamily: 'monospace' }}>
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
        <Space>
          <Button size="small" icon={<EditOutlined />} onClick={() => {
            setEditingEntry(entry)
            setEntryModalOpen(true)
          }} />
          <Popconfirm title="Удалить запись?" onConfirm={() => handleDeleteEntry(entry.id)}
            okText="Да" cancelText="Нет">
            <Button size="small" danger icon={<DeleteOutlined />} />
          </Popconfirm>
        </Space>
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
      <Card
        title="Поля справочника"
        style={{ marginBottom: 24 }}
        extra={
          <Space>
            <Button icon={<PlusOutlined />} size="small" onClick={addField}>
              Добавить поле
            </Button>
            {schemaDirty && (
              <Button
                type="primary" size="small" loading={savingSchema}
                onClick={saveSchema} disabled={!schemaValid}
                style={{}}
              >
                Сохранить схему
              </Button>
            )}
          </Space>
        }
      >
        {schemaFields.length === 0 ? (
          <Text type="secondary">Нет полей. Добавьте поля, чтобы начать вносить записи.</Text>
        ) : (
          <>
            {/* Header row */}
            <div style={{ display: 'flex', gap: 8, marginBottom: 6, paddingLeft: 20 }}>
              <Text type="secondary" style={{ width: 150, fontSize: 12 }}>Системный ключ</Text>
              <Text type="secondary" style={{ width: 180, fontSize: 12 }}>Название</Text>
              <Text type="secondary" style={{ width: 120, fontSize: 12 }}>Тип</Text>
              <Text type="secondary" style={{ fontSize: 12 }}>Варианты (для списка)</Text>
            </div>
            {schemaFields.map((f, i) => (
              <FieldRow
                key={i}
                field={f}
                existingKeys={allKeys}
                onChange={updated => updateField(i, updated)}
                onDelete={() => removeField(i)}
              />
            ))}
          </>
        )}
      </Card>

      {/* Entries */}
      <Card
        title={`Записи (${directory.entries.length})`}
        extra={
          <Button
            type="primary" icon={<PlusOutlined />} size="small"
            onClick={() => { setEditingEntry(null); setEntryModalOpen(true) }}
            disabled={directory.fields.length === 0}
          >
            Добавить запись
          </Button>
        }
      >
        {directory.fields.length === 0 ? (
          <Alert type="info" message="Сначала определите поля справочника" showIcon />
        ) : directory.entries.length === 0 ? (
          <Text type="secondary">Нет записей</Text>
        ) : (
          <Table
            dataSource={directory.entries}
            columns={entryColumns}
            rowKey="id"
            pagination={false}
            size="small"
          />
        )}
      </Card>

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
