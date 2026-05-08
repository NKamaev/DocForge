import { useState, useEffect } from 'react'
import { Button, Checkbox, Input, Modal, Radio, Space, Tag, Typography } from 'antd'
import type { Phrasing } from '../../types/templateModel'

const { Text } = Typography

// ── Separator presets ─────────────────────────────────────────────────────────

interface SeparatorPreset {
  label: string
  value: string
}

const SEPARATOR_PRESETS: SeparatorPreset[] = [
  { label: 'Запятая', value: ', ' },
  { label: 'Точка с зап.', value: '; ' },
  { label: 'Пробел', value: ' ' },
  { label: 'Тире', value: ' — ' },
  { label: 'Абзац', value: '\n' },
]

// ── Props ─────────────────────────────────────────────────────────────────────

interface Props {
  open: boolean
  phrasings: Phrasing[]
  /** Pre-selected phrasing IDs when opening the modal (e.g. clicked from list item) */
  initialIds?: string[]
  onInsert: (phrasingsIds: string[], displayNames: string[], separator: string) => void
  onClose: () => void
}

// ── Component ─────────────────────────────────────────────────────────────────

export default function PhrasingsRefInsertModal({
  open,
  phrasings,
  initialIds,
  onInsert,
  onClose,
}: Props) {
  const [selectedIds, setSelectedIds] = useState<string[]>(initialIds ?? [])
  const [separatorValue, setSeparatorValue] = useState<string>(', ')
  const [customSeparator, setCustomSeparator] = useState('')
  const [useCustom, setUseCustom] = useState(false)

  // Reset state when modal opens
  useEffect(() => {
    if (open) {
      setSelectedIds(initialIds ?? [])
      setSeparatorValue(', ')
      setCustomSeparator('')
      setUseCustom(false)
    }
  }, [open, initialIds])

  const effectiveSep = useCustom ? customSeparator : separatorValue

  const toggleId = (id: string) => {
    setSelectedIds(prev =>
      prev.includes(id) ? prev.filter(x => x !== id) : [...prev, id]
    )
  }

  const handleInsert = () => {
    if (selectedIds.length === 0) return
    const displayNames = selectedIds.map(id => phrasings.find(p => p.id === id)?.name ?? id)
    onInsert(selectedIds, displayNames, effectiveSep)
    onClose()
  }

  const previewText = selectedIds
    .map(id => phrasings.find(p => p.id === id)?.name ?? id)
    .join(effectiveSep === '\n' ? ' ↵ ' : effectiveSep)

  return (
    <Modal
      title="Вставить формулировки"
      open={open}
      onCancel={onClose}
      footer={[
        <Button key="cancel" onClick={onClose}>Отмена</Button>,
        <Button
          key="insert"
          type="primary"
          disabled={selectedIds.length === 0}
          onClick={handleInsert}
        >
          Вставить
        </Button>,
      ]}
      width={480}
    >
      {/* Phrasings list */}
      <div style={{ marginBottom: 16 }}>
        <Text strong style={{ display: 'block', marginBottom: 8 }}>Выберите формулировки</Text>
        {phrasings.length === 0 ? (
          <Text type="secondary">Нет формулировок</Text>
        ) : (
          <Space direction="vertical" size={4} style={{ width: '100%' }}>
            {phrasings.map(p => (
              <div
                key={p.id}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  padding: '4px 8px',
                  border: `1px solid ${selectedIds.includes(p.id) ? '#91CAFF' : '#d9d9d9'}`,
                  borderRadius: 4,
                  background: selectedIds.includes(p.id) ? '#F0F7FF' : undefined,
                  cursor: 'pointer',
                }}
                onClick={() => toggleId(p.id)}
              >
                <Space size={8}>
                  <Checkbox checked={selectedIds.includes(p.id)} onClick={e => e.stopPropagation()} onChange={() => toggleId(p.id)} />
                  <Text>{p.name}</Text>
                </Space>
                <Tag style={{ fontSize: 11 }}>{p.variants.length} вар.</Tag>
              </div>
            ))}
          </Space>
        )}
      </div>

      {/* Separator (shown only when more than one can be selected) */}
      {selectedIds.length > 1 && (
        <div style={{ marginBottom: 16 }}>
          <Text strong style={{ display: 'block', marginBottom: 8 }}>Разделитель</Text>
          <Radio.Group
            value={useCustom ? '__custom__' : separatorValue}
            onChange={e => {
              if (e.target.value === '__custom__') {
                setUseCustom(true)
              } else {
                setUseCustom(false)
                setSeparatorValue(e.target.value as string)
              }
            }}
          >
            <Space direction="vertical" size={4}>
              {SEPARATOR_PRESETS.map(preset => (
                <Radio key={preset.value} value={preset.value}>
                  {preset.label}
                  <Text type="secondary" style={{ fontSize: 11, marginLeft: 6 }}>
                    {preset.value === '\n' ? '↵' : `"${preset.value}"`}
                  </Text>
                </Radio>
              ))}
              <Radio value="__custom__">
                Другой:&nbsp;
                <Input
                  size="small"
                  style={{ width: 120 }}
                  value={customSeparator}
                  placeholder="введите разделитель"
                  onClick={e => { e.stopPropagation(); setUseCustom(true) }}
                  onChange={e => { setCustomSeparator(e.target.value); setUseCustom(true) }}
                />
              </Radio>
            </Space>
          </Radio.Group>
        </div>
      )}

      {/* Preview */}
      {selectedIds.length > 0 && (
        <div style={{ background: '#F9F9F9', borderRadius: 4, padding: '6px 10px' }}>
          <Text type="secondary" style={{ fontSize: 11 }}>Предпросмотр тега: </Text>
          <Text style={{ fontSize: 12 }}>📝 {previewText}</Text>
        </div>
      )}
    </Modal>
  )
}
