import { useEffect, useState } from 'react'
import {
  Badge, Button, Checkbox, Form, Input, InputNumber, List,
  Modal, Popconfirm, Select, Space, Spin, Tag, Tooltip, Typography,
} from 'antd'
import {
  DndContext, closestCenter, PointerSensor, useSensor, useSensors,
} from '@dnd-kit/core'
import type { DragEndEvent } from '@dnd-kit/core'
import {
  SortableContext, useSortable, verticalListSortingStrategy, arrayMove,
} from '@dnd-kit/sortable'
import { CSS } from '@dnd-kit/utilities'
import { DeleteOutlined, EditOutlined, HolderOutlined, PlusOutlined } from '@ant-design/icons'
import type { Parameter } from '../../types/template'
import type { ParameterType, ParameterFormat } from '../../types/templateModel'
import type { DirectoryListItem, DirectoryField } from '../../types/directory'
import { directoriesApi } from '../../api/directoriesApi'
import ParameterFormatSelector from './ParameterFormatSelector'

const { Text } = Typography

// ── Constants ─────────────────────────────────────────────────────────────────

const TYPE_OPTIONS: { value: ParameterType; label: string; color: string }[] = [
  { value: 'TEXT',      label: 'Текст',      color: 'blue'     },
  { value: 'NUMBER',    label: 'Число',      color: 'orange'   },
  { value: 'DATE',      label: 'Дата',       color: 'green'    },
  { value: 'FIO',       label: 'ФИО',        color: 'purple'   },
  { value: 'BOOLEAN',   label: 'Да/Нет',     color: 'cyan'     },
  { value: 'SELECT',    label: 'Список',     color: 'magenta'  },
  { value: 'DIRECTORY', label: 'Справочник', color: 'geekblue' },
]

const TYPE_COLOR: Record<ParameterType, string> = Object.fromEntries(
  TYPE_OPTIONS.map((t) => [t.value, t.color])
) as Record<ParameterType, string>

const CODE_PATTERN = /^[a-z][a-z0-9_]*$/

// ── Form values type ──────────────────────────────────────────────────────────

interface ParameterFormValues {
  code: string
  label: string
  type: ParameterType
  required: boolean
  defaultValue?: string
  options?: string[]
  validationMin?: number
  validationMax?: number
  validationPattern?: string
  directoryId?: string
  directoryFields?: string[]
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

// ── Parameter form (inside modal) ─────────────────────────────────────────────

interface ParameterFormProps {
  form: ReturnType<typeof Form.useForm<ParameterFormValues>>[0]
  existingCodes: string[]
  editingCode?: string   // when editing — allow own code
  formatValue: ParameterFormat | null
  onFormatChange: (fmt: ParameterFormat | null) => void
}

function ParameterFormFields({ form, existingCodes, editingCode, formatValue: fmtValue, onFormatChange }: ParameterFormProps) {
  const selectedType   = Form.useWatch('type', form)
  const selectedDirId  = Form.useWatch('directoryId', form)

  const [directories,   setDirectories]   = useState<DirectoryListItem[]>([])
  const [dirFields,     setDirFields]     = useState<DirectoryField[]>([])
  const [dirLoading,    setDirLoading]    = useState(false)
  const [fieldsLoading, setFieldsLoading] = useState(false)

  // Load directory list once when DIRECTORY type is selected
  useEffect(() => {
    if (selectedType !== 'DIRECTORY') return
    setDirLoading(true)
    directoriesApi.list()
      .then(setDirectories)
      .finally(() => setDirLoading(false))
  }, [selectedType])

  // Load directory fields when a directory is selected
  useEffect(() => {
    if (!selectedDirId) { setDirFields([]); return }
    setFieldsLoading(true)
    directoriesApi.getById(selectedDirId)
      .then(d => setDirFields(d.fields))
      .finally(() => setFieldsLoading(false))
  }, [selectedDirId])

  return (
    <>
      <Form.Item
        name="code"
        label="Код"
        tooltip="snake_case, только латиница: client_name"
        rules={[
          { required: true, message: 'Введите код' },
          { pattern: CODE_PATTERN, message: 'Только строчные латинские буквы, цифры и _' },
          {
            validator: (_, value) =>
              !value || !existingCodes.includes(value) || value === editingCode
                ? Promise.resolve()
                : Promise.reject('Код уже используется'),
          },
        ]}
      >
        <Input placeholder="client_name" style={{ fontFamily: 'monospace' }} />
      </Form.Item>

      <Form.Item name="label" label="Название" rules={[{ required: true, message: 'Введите название' }]}>
        <Input placeholder="ФИО клиента" />
      </Form.Item>

      <Form.Item name="type" label="Тип" rules={[{ required: true }]}>
        <Select options={TYPE_OPTIONS.map((t) => ({ value: t.value, label: t.label }))} />
      </Form.Item>

      <Form.Item name="required" valuePropName="checked">
        <Checkbox>Обязательный</Checkbox>
      </Form.Item>

      {selectedType === 'SELECT' && (
        <Form.List name="options">
          {(fields, { add, remove }) => (
            <Form.Item label="Варианты">
              <Space direction="vertical" style={{ width: '100%' }}>
                {fields.map((field) => (
                  <Space key={field.key} style={{ width: '100%' }}>
                    <Form.Item {...field} noStyle rules={[{ required: true, message: 'Введите вариант' }]}>
                      <Input placeholder="Вариант" style={{ width: 240 }} />
                    </Form.Item>
                    <Button type="text" danger icon={<DeleteOutlined />} onClick={() => remove(field.name)} />
                  </Space>
                ))}
                <Button type="dashed" icon={<PlusOutlined />} onClick={() => add('')} size="small">
                  Добавить вариант
                </Button>
              </Space>
            </Form.Item>
          )}
        </Form.List>
      )}

      {selectedType === 'DIRECTORY' && (
        <>
          <Form.Item
            name="directoryId"
            label="Справочник"
            rules={[{ required: true, message: 'Выберите справочник' }]}
          >
            <Select
              loading={dirLoading}
              placeholder="Выберите справочник"
              options={directories.map(d => ({ value: d.id, label: d.name }))}
              onChange={() => form.setFieldValue('directoryFields', [])}
              allowClear
            />
          </Form.Item>

          {selectedDirId && (
            <Form.Item
              name="directoryFields"
              label="Поля для подстановки"
              tooltip="Выбранные поля будут доступны в теле шаблона как отдельные параметры"
            >
              {fieldsLoading
                ? <Spin size="small" />
                : (
                  <Checkbox.Group
                    options={dirFields.map(f => ({ value: f.key, label: f.label || f.key }))}
                    style={{ display: 'flex', flexDirection: 'column', gap: 4 }}
                  />
                )
              }
            </Form.Item>
          )}
        </>
      )}

      {selectedType !== 'DIRECTORY' && (
        <Form.Item name="defaultValue" label="Значение по умолчанию">
          {selectedType === 'BOOLEAN'
            ? <Select allowClear options={[{ value: 'true', label: 'Да' }, { value: 'false', label: 'Нет' }]} />
            : selectedType === 'SELECT'
            ? <Input placeholder="Один из вариантов выше" />
            : <Input placeholder={selectedType === 'DATE' ? '$TODAY или дата' : 'Значение'} />
          }
        </Form.Item>
      )}

      {(selectedType === 'TEXT' || selectedType === 'NUMBER') && (
        <>
          {selectedType === 'NUMBER' && (
            <Space>
              <Form.Item name="validationMin" label="Мин">
                <InputNumber style={{ width: 100 }} />
              </Form.Item>
              <Form.Item name="validationMax" label="Макс">
                <InputNumber style={{ width: 100 }} />
              </Form.Item>
            </Space>
          )}
          {selectedType === 'TEXT' && (
            <Form.Item name="validationPattern" label="Паттерн (regex)">
              <Input placeholder="^[А-ЯЁ].*" style={{ fontFamily: 'monospace' }} />
            </Form.Item>
          )}
        </>
      )}

      {(selectedType === 'DATE' || selectedType === 'NUMBER' || selectedType === 'FIO') && (
        <Form.Item
          label="Формат отображения"
          tooltip="Как значение будет выглядеть в готовом документе. Можно переопределить для каждого поля отдельно."
        >
          <ParameterFormatSelector
            paramType={selectedType}
            value={fmtValue}
            onChange={onFormatChange}
          />
        </Form.Item>
      )}
    </>
  )
}

// ── Sortable row ──────────────────────────────────────────────────────────────

interface SortableRowProps {
  parameter: Parameter
  usedInBody: boolean
  onEdit: () => void
  onDelete: () => void
  readOnly?: boolean
}

function SortableRow({ parameter, usedInBody, onEdit, onDelete, readOnly }: SortableRowProps) {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } =
    useSortable({ id: parameter.code })

  const style = {
    transform: CSS.Transform.toString(transform),
    transition,
    opacity: isDragging ? 0.5 : 1,
    background: isDragging ? '#f0f5ff' : 'transparent',
  }

  return (
    <List.Item
      ref={setNodeRef}
      style={{ ...style, padding: '8px 0', alignItems: 'flex-start' }}
      actions={readOnly ? [] : [
        <Tooltip key="edit" title="Редактировать">
          <Button type="text" size="small" icon={<EditOutlined />} onClick={onEdit} />
        </Tooltip>,
        usedInBody ? (
          <Popconfirm
            key="del"
            title="Параметр используется в теле шаблона"
            description="При удалении ноды parameter_field для этого параметра останутся в тексте. Продолжить?"
            okText="Удалить"
            cancelText="Отмена"
            okButtonProps={{ danger: true }}
            onConfirm={onDelete}
          >
            <Button type="text" size="small" danger icon={<DeleteOutlined />} />
          </Popconfirm>
        ) : (
          <Popconfirm key="del" title="Удалить параметр?" okText="Да" cancelText="Нет" onConfirm={onDelete}>
            <Button type="text" size="small" danger icon={<DeleteOutlined />} />
          </Popconfirm>
        ),
      ]}
    >
      <Space size={4} align="start">
        {/* Drag handle */}
        <span
          {...attributes}
          {...(readOnly ? {} : listeners)}
          data-testid="drag-handle"
          style={{ cursor: readOnly ? 'default' : 'grab', color: '#bbb', padding: '2px 4px', display: 'flex', alignItems: 'center' }}
        >
          <HolderOutlined />
        </span>

        <Space direction="vertical" size={2}>
          <Space size={6}>
            <Text strong style={{ fontSize: 13 }}>{parameter.label}</Text>
            {parameter.required && <Badge dot color="red" title="Обязательный" />}
          </Space>
          <Space size={6}>
            <Tag color={TYPE_COLOR[parameter.type]} style={{ fontSize: 11, lineHeight: '18px' }}>
              {parameter.type}
            </Tag>
            <Text type="secondary" style={{ fontSize: 12, fontFamily: 'monospace' }}>
              {parameter.code}
            </Text>
          </Space>
        </Space>
      </Space>
    </List.Item>
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
  const [formatDraft, setFormatDraft] = useState<ParameterFormat | null>(null)
  const [form] = Form.useForm<ParameterFormValues>()

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
    setFormatDraft(null)
    form.resetFields()
    form.setFieldsValue({ type: 'TEXT', required: false, options: [] })
    setModalOpen(true)
  }

  // ── Open edit modal ──
  const openEdit = (param: Parameter) => {
    setEditingCode(param.code)
    setFormatDraft(param.format ?? null)
    form.setFieldsValue(parameterToForm(param))
    setModalOpen(true)
  }

  // ── Save ──
  const handleSave = async () => {
    const values = await form.validateFields()
    const next = formToParameter({ ...values, format: formatDraft })

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
        <Text strong>Параметры ({parameters.length})</Text>
        {!readOnly && (
          <Button size="small" type="primary" icon={<PlusOutlined />} onClick={openAdd}>
            Добавить
          </Button>
        )}
      </div>

      {parameters.length === 0 ? (
        <Text type="secondary" style={{ fontSize: 13 }}>
          {readOnly ? 'Нет параметров.' : 'Нет параметров. Нажмите «Добавить» чтобы создать первый.'}
        </Text>
      ) : (
        <div style={{ flex: 1, overflow: 'auto' }}>
          <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={handleDragEnd}>
            <SortableContext items={parameters.map((p) => p.code)} strategy={verticalListSortingStrategy}>
              <List
                size="small"
                dataSource={parameters}
                rowKey="code"
                renderItem={(param) => (
                  <SortableRow
                    key={param.code}
                    parameter={param}
                    usedInBody={usedCodes.has(param.code)}
                    onEdit={() => openEdit(param)}
                    onDelete={() => handleDelete(param.code)}
                    readOnly={readOnly}
                  />
                )}
              />
            </SortableContext>
          </DndContext>
        </div>
      )}

      <Modal
        title={editingCode ? 'Редактировать параметр' : 'Новый параметр'}
        open={modalOpen}
        onOk={handleSave}
        onCancel={() => setModalOpen(false)}
        okText="Сохранить"
        cancelText="Отмена"
        destroyOnHidden
        width={480}
      >
        <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
          <ParameterFormFields
            form={form}
            existingCodes={existingCodes}
            editingCode={editingCode ?? undefined}
            formatValue={formatDraft}
            onFormatChange={setFormatDraft}
          />
        </Form>
      </Modal>
    </div>
  )
}
