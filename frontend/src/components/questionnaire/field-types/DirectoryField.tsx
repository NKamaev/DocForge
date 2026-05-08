import { useEffect, useState } from 'react'
import { Button, Select, Space, Spin, Tooltip } from 'antd'
import { EditOutlined, PlusOutlined } from '@ant-design/icons'
import { directoriesApi } from '../../../api/directoriesApi'
import type { Directory, DirectoryEntry } from '../../../types/directory'
import DirectoryEntryModal from './DirectoryEntryModal'

interface Props {
  directoryId: string
  directoryFields: string[]
  paramCode: string
  value: unknown
  /** Called for the main answer (entryId) AND for each sub-field value */
  onMultiChange: (changes: Record<string, unknown>) => void
}

export default function DirectoryField({
  directoryId, directoryFields, paramCode, value, onMultiChange,
}: Props) {
  const [directory, setDirectory] = useState<Directory | null>(null)
  const [loading, setLoading] = useState(true)
  const [modalOpen, setModalOpen] = useState(false)
  const [editEntry, setEditEntry] = useState<DirectoryEntry | undefined>(undefined)

  const loadDirectory = () => {
    setLoading(true)
    directoriesApi.getById(directoryId)
      .then(setDirectory)
      .finally(() => setLoading(false))
  }

  useEffect(() => { loadDirectory() }, [directoryId]) // eslint-disable-line react-hooks/exhaustive-deps

  if (loading) return <Spin size="small" />
  if (!directory) return <span style={{ color: '#ff4d4f' }}>Справочник не найден</span>

  // Build display label from first available field
  const firstFieldKey = directory.fields[0]?.key
  const options = directory.entries.map(entry => ({
    value: entry.id,
    label: firstFieldKey ? String(entry.values[firstFieldKey] ?? entry.id) : entry.id,
    entry,
  }))

  const selectedEntry = directory.entries.find(e => e.id === (value as string))

  const applyEntry = (entry: DirectoryEntry) => {
    const changes: Record<string, unknown> = { [paramCode]: entry.id }
    for (const fieldKey of directoryFields) {
      changes[`${paramCode}__${fieldKey}`] = entry.values[fieldKey] ?? ''
    }
    onMultiChange(changes)
  }

  const handleChange = (entryId: string) => {
    const entry = directory.entries.find(e => e.id === entryId)
    if (!entry) return
    applyEntry(entry)
  }

  const openCreate = () => {
    setEditEntry(undefined)
    setModalOpen(true)
  }

  const openEdit = () => {
    if (!selectedEntry) return
    setEditEntry(selectedEntry)
    setModalOpen(true)
  }

  const handleSaved = (saved: DirectoryEntry) => {
    // Refresh directory to pick up new/updated entry
    loadDirectory()
    // Auto-select saved entry
    applyEntry(saved)
  }

  return (
    <>
      <Space.Compact style={{ width: '100%' }}>
        <Select
          style={{ flex: 1 }}
          value={value as string | undefined}
          onChange={handleChange}
          placeholder="Выберите запись из справочника"
          options={options}
          showSearch
          filterOption={(input, opt) =>
            String(opt?.label ?? '').toLowerCase().includes(input.toLowerCase())
          }
          allowClear
          onClear={() => {
            const changes: Record<string, unknown> = { [paramCode]: null }
            for (const fieldKey of directoryFields) {
              changes[`${paramCode}__${fieldKey}`] = ''
            }
            onMultiChange(changes)
          }}
        />
        <Tooltip title="Создать новую запись">
          <Button icon={<PlusOutlined />} onClick={openCreate} />
        </Tooltip>
        <Tooltip title={selectedEntry ? 'Редактировать выбранную запись' : 'Выберите запись для редактирования'}>
          <Button
            icon={<EditOutlined />}
            onClick={openEdit}
            disabled={!selectedEntry}
          />
        </Tooltip>
      </Space.Compact>

      {directory && (
        <DirectoryEntryModal
          open={modalOpen}
          directory={directory}
          entry={editEntry}
          onClose={() => setModalOpen(false)}
          onSaved={handleSaved}
        />
      )}
    </>
  )
}
