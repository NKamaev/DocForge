import { useState, useEffect } from 'react'
import { Button, Input, Modal, Space, Typography } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import PhrasingVariantEditor from './PhrasingVariantEditor'
import type { Phrasing, PhrasingVariant } from '../../types/templateModel'
import type { Parameter } from '../../types/template'

const { Text } = Typography

interface Props {
  open: boolean
  initial: Phrasing | null  // null = создание нового
  parameters: Parameter[]
  onSave: (phrasing: Phrasing) => void
  onClose: () => void
}

function newVariant(): PhrasingVariant {
  return { id: crypto.randomUUID(), condition: null, content: [] }
}

export default function PhrasingEditorModal({ open, initial, parameters, onSave, onClose }: Props) {
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [variants, setVariants] = useState<PhrasingVariant[]>([newVariant()])

  useEffect(() => {
    if (!open) return
    if (initial) {
      setName(initial.name)
      setDescription(initial.description ?? '')
      setVariants(initial.variants.length ? initial.variants : [newVariant()])
    } else {
      setName('')
      setDescription('')
      setVariants([newVariant()])
    }
  }, [open, initial])

  const handleSave = () => {
    if (!name.trim()) return
    onSave({
      id: initial?.id ?? crypto.randomUUID(),
      name: name.trim(),
      description: description.trim() || null,
      variants,
    })
    onClose()
  }

  const addVariant = () => {
    setVariants(vs => {
      // Новый вариант вставляется перед последним (дефолтным), если последний null
      const last = vs[vs.length - 1]
      const hasDefault = last && last.condition == null
      if (hasDefault) {
        return [...vs.slice(0, -1), newVariant(), last]
      }
      return [...vs, newVariant()]
    })
  }

  const updateVariant = (i: number, v: PhrasingVariant) =>
    setVariants(vs => vs.map((x, idx) => idx === i ? v : x))

  const deleteVariant = (i: number) =>
    setVariants(vs => vs.filter((_, idx) => idx !== i))

  const moveVariant = (i: number, dir: 'up' | 'down') => {
    setVariants(vs => {
      const arr = [...vs]
      const j = dir === 'up' ? i - 1 : i + 1
      if (j < 0 || j >= arr.length) return arr
      ;[arr[i], arr[j]] = [arr[j], arr[i]]
      return arr
    })
  }

  return (
    <Modal
      title={initial ? 'Редактировать формулировку' : 'Новая формулировка'}
      open={open}
      onOk={handleSave}
      onCancel={onClose}
      okText="Сохранить"
      cancelText="Отмена"
      okButtonProps={{ disabled: !name.trim() }}
      destroyOnHidden
      width={680}
    >
      <Space direction="vertical" style={{ width: '100%', marginTop: 12 }} size={12}>
        <div>
          <Text strong>Название *</Text>
          <Input
            autoFocus
            value={name}
            onChange={e => setName(e.target.value)}
            placeholder="Неустойка"
            style={{ marginTop: 4 }}
          />
        </div>

        <div>
          <Text strong>Описание</Text>
          <Input.TextArea
            value={description}
            onChange={e => setDescription(e.target.value)}
            placeholder="Необязательное описание"
            rows={2}
            style={{ marginTop: 4 }}
          />
        </div>

        <div>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
            <Text strong>Варианты</Text>
            <Button size="small" icon={<PlusOutlined />} onClick={addVariant}>
              Добавить вариант
            </Button>
          </div>
          {variants.map((v, i) => (
            <PhrasingVariantEditor
              key={v.id}
              variant={v}
              index={i}
              isFirst={i === 0}
              isLast={i === variants.length - 1}
              isDefault={v.condition == null}
              parameters={parameters}
              onChange={updated => updateVariant(i, updated)}
              onDelete={() => deleteVariant(i)}
              onMoveUp={() => moveVariant(i, 'up')}
              onMoveDown={() => moveVariant(i, 'down')}
            />
          ))}
          <Text type="secondary" style={{ fontSize: 12 }}>
            Порядок важен: берётся первый вариант, чьё условие выполнено.
            Вариант без условия — умолчание (рекомендуется ставить последним).
          </Text>
        </div>
      </Space>
    </Modal>
  )
}
