import { useState } from 'react'
import { useEditor, EditorContent } from '@tiptap/react'
import StarterKit from '@tiptap/starter-kit'
import { Button, Dropdown, Space, Tag, Tooltip } from 'antd'
import type { MenuProps } from 'antd'
import {
  ArrowDownOutlined,
  ArrowUpOutlined,
  DeleteOutlined,
  FilterOutlined,
  PlusSquareOutlined,
} from '@ant-design/icons'
import { ParameterFieldNode } from './extensions/ParameterFieldNode'
import ConditionBuilder from './ConditionBuilder'
import type { PhrasingVariant } from '../../types/templateModel'
import type { Condition } from '../../types/templateModel'
import type { Parameter } from '../../types/template'

interface Props {
  variant: PhrasingVariant
  index: number
  isFirst: boolean
  isLast: boolean
  isDefault: boolean
  parameters: Parameter[]
  onChange: (variant: PhrasingVariant) => void
  onDelete: () => void
  onMoveUp: () => void
  onMoveDown: () => void
}

export default function PhrasingVariantEditor({
  variant,
  index,
  isFirst,
  isLast,
  isDefault,
  parameters,
  onChange,
  onDelete,
  onMoveUp,
  onMoveDown,
}: Props) {
  const [conditionOpen, setConditionOpen] = useState(false)

  const editor = useEditor({
    extensions: [
      StarterKit.configure({
        heading: false,
        bulletList: false,
        orderedList: false,
        blockquote: false,
        codeBlock: false,
        horizontalRule: false,
      }),
      ParameterFieldNode,
    ],
    content: {
      type: 'doc',
      content: [{ type: 'paragraph', content: variant.content as never[] ?? [] }],
    },
    onUpdate: ({ editor: e }) => {
      const json = e.getJSON()
      const content = json.content?.[0]?.content ?? []
      onChange({ ...variant, content: content as PhrasingVariant['content'] })
    },
  })

  const paramMenuItems: MenuProps['items'] = parameters.length === 0
    ? [{ key: 'none', label: 'Нет параметров в шаблоне', disabled: true }]
    : parameters.flatMap((p) => {
        if (p.type === 'DIRECTORY' && p.directoryFields && p.directoryFields.length > 0) {
          return p.directoryFields.map((fieldKey) => ({
            key: `${p.code}__${fieldKey}`,
            label: <span><span style={{ fontWeight: 500 }}>{p.label} → {fieldKey}</span></span>,
            onClick: () => editor?.chain().focus().insertParameterField({
              parameterCode: `${p.code}__${fieldKey}`,
              display: `${p.label} → ${fieldKey}`,
            }).run(),
          }))
        }
        return [{
          key: p.code,
          label: <span><span style={{ fontWeight: 500 }}>{p.label}</span><span style={{ color: '#999', fontSize: 12, marginLeft: 8 }}>{p.code}</span></span>,
          onClick: () => editor?.chain().focus().insertParameterField({ parameterCode: p.code, display: p.label }).run(),
        }]
      })

  const conditionLabel = variant.condition
    ? (variant.condition.type === 'PYTHON'
        ? `Python: ${variant.condition.expression ?? ''}`
        : variant.condition.expression || '(пусто)')
    : null

  const handleConditionSave = (cond: Condition) => {
    if (!cond.expression?.trim()) {
      onChange({ ...variant, condition: null })
    } else {
      onChange({ ...variant, condition: cond })
    }
  }

  return (
    <div
      style={{
        border: `1.5px solid ${isDefault ? '#91CAFF' : '#d9d9d9'}`,
        borderRadius: 6,
        padding: '8px 10px',
        marginBottom: 8,
        background: isDefault ? '#F0F7FF' : '#FAFAFA',
      }}
    >
      {/* Header */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 6 }}>
        <Space size={4}>
          <span style={{ fontSize: 12, color: '#8c8c8c' }}>Вариант {index + 1}</span>
          {isDefault && <Tag color="blue" style={{ fontSize: 11 }}>Умолчание</Tag>}
          {conditionLabel != null && (
            <Tag
              color="orange"
              style={{ fontSize: 11, cursor: 'pointer', maxWidth: 200, overflow: 'hidden', textOverflow: 'ellipsis' }}
              onClick={() => setConditionOpen(true)}
            >
              <FilterOutlined /> {conditionLabel}
            </Tag>
          )}
          {conditionLabel == null && (
            <Button size="small" type="dashed" icon={<FilterOutlined />} onClick={() => setConditionOpen(true)}>
              Добавить условие
            </Button>
          )}
          {conditionLabel != null && (
            <Tooltip title="Изменить условие">
              <Button size="small" type="text" icon={<FilterOutlined />} onClick={() => setConditionOpen(true)} />
            </Tooltip>
          )}
        </Space>
        <Space size={4}>
          <Tooltip title="Переместить вверх">
            <Button size="small" type="text" icon={<ArrowUpOutlined />} onClick={onMoveUp} disabled={isFirst} />
          </Tooltip>
          <Tooltip title="Переместить вниз">
            <Button size="small" type="text" icon={<ArrowDownOutlined />} onClick={onMoveDown} disabled={isLast} />
          </Tooltip>
          <Tooltip title="Удалить вариант">
            <Button size="small" type="text" danger icon={<DeleteOutlined />} onClick={onDelete} />
          </Tooltip>
        </Space>
      </div>

      {/* Mini editor */}
      <div style={{ border: '1px solid #d9d9d9', borderRadius: 4, background: '#fff' }}>
        <div style={{ borderBottom: '1px solid #f0f0f0', padding: '2px 6px' }}>
          <Dropdown menu={{ items: paramMenuItems }} trigger={['click']} placement="bottomLeft">
            <Button size="small" type="text" icon={<PlusSquareOutlined />} style={{ fontSize: 12, color: '#1677ff' }}>
              Параметр
            </Button>
          </Dropdown>
        </div>
        <div
          style={{ padding: '4px 8px', minHeight: 36, fontSize: 13, cursor: 'text' }}
          onClick={() => editor?.commands.focus()}
        >
          <EditorContent editor={editor} />
        </div>
      </div>

      {isDefault && (
        <div style={{ fontSize: 11, color: '#8c8c8c', marginTop: 4 }}>
          Вариант без условия — показывается если ни один другой вариант не подошёл.
        </div>
      )}

      <ConditionBuilder
        open={conditionOpen}
        condition={variant.condition ?? { type: 'SIMPLE', expression: '', scriptId: null }}
        parameters={parameters}
        onSave={handleConditionSave}
        onClose={() => setConditionOpen(false)}
      />
    </div>
  )
}
