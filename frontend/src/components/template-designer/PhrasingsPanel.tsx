import { useState } from 'react'
import { Button, List, Popconfirm, Space, Tag, Tooltip, Typography } from 'antd'
import { DeleteOutlined, EditOutlined, InsertRowAboveOutlined, PlusOutlined } from '@ant-design/icons'
import type { Editor } from '@tiptap/react'
import PhrasingEditorModal from './PhrasingEditorModal'
import PhrasingsRefInsertModal from './PhrasingsRefInsertModal'
import type { Phrasing } from '../../types/templateModel'
import type { Parameter } from '../../types/template'

const { Text } = Typography

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

  const handleDelete = (id: string) => {
    onChange(phrasings.filter(p => p.id !== id))
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
        <Text strong>Формулировки</Text>
        {!readOnly && (
          <Button size="small" type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            Добавить
          </Button>
        )}
      </div>

      {phrasings.length === 0 ? (
        <Text type="secondary" style={{ fontSize: 12 }}>
          Нет формулировок. Нажмите «Добавить», чтобы создать именованный список вариантов текста с условиями.
        </Text>
      ) : (
        <List
          dataSource={phrasings}
          renderItem={p => (
            <List.Item
              style={{ padding: '6px 0' }}
              actions={readOnly ? [] : [
                <Tooltip title="Вставить в текст">
                  <Button
                    size="small"
                    type="text"
                    icon={<InsertRowAboveOutlined />}
                    onClick={() => handleInsert(p)}
                    disabled={!editor}
                  />
                </Tooltip>,
                <Tooltip title="Редактировать">
                  <Button size="small" type="text" icon={<EditOutlined />} onClick={() => openEdit(p)} />
                </Tooltip>,
                <Popconfirm
                  title="Удалить формулировку?"
                  description="Ноды phrasingsRef в шаблоне потеряют ссылку."
                  okText="Удалить"
                  cancelText="Отмена"
                  okType="danger"
                  onConfirm={() => handleDelete(p.id)}
                >
                  <Button size="small" type="text" danger icon={<DeleteOutlined />} />
                </Popconfirm>,
              ]}
            >
              <Space direction="vertical" size={0} style={{ flex: 1, overflow: 'hidden' }}>
                <Text ellipsis style={{ maxWidth: 180 }}>{p.name}</Text>
                <Space size={4} wrap>
                  <Tag style={{ fontSize: 11 }}>{p.variants.length} вар.</Tag>
                  {p.description && (
                    <Text type="secondary" style={{ fontSize: 11 }} ellipsis>
                      {p.description}
                    </Text>
                  )}
                </Space>
              </Space>
            </List.Item>
          )}
        />
      )}

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
