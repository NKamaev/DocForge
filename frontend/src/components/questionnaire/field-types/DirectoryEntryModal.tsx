import { useEffect } from 'react'
import { DatePicker, Form, Input, InputNumber, Modal, Select, Switch, notification } from 'antd'
import dayjs from 'dayjs'
import { directoriesApi } from '../../../api/directoriesApi'
import type { Directory, DirectoryEntry, DirectoryEntryRequest } from '../../../types/directory'

interface Props {
  open: boolean
  directory: Directory
  entry?: DirectoryEntry         // undefined → create mode, set → edit mode
  onClose: () => void
  onSaved: (entry: DirectoryEntry) => void
}

export default function DirectoryEntryModal({ open, directory, entry, onClose, onSaved }: Props) {
  const [form] = Form.useForm()
  const isEdit = Boolean(entry)

  useEffect(() => {
    if (open) {
      if (entry) {
        // Preprocess dates for DatePicker
        const vals: Record<string, unknown> = {}
        for (const field of directory.fields) {
          const v = entry.values[field.key]
          if (field.type === 'DATE' && typeof v === 'string' && v) {
            vals[field.key] = dayjs(v)
          } else {
            vals[field.key] = v ?? ''
          }
        }
        form.setFieldsValue(vals)
      } else {
        form.resetFields()
      }
    }
  }, [open, entry, directory, form])

  const handleOk = async () => {
    try {
      const raw = await form.validateFields()
      const values: Record<string, unknown> = {}
      for (const field of directory.fields) {
        const v = raw[field.key]
        if (field.type === 'DATE' && v && typeof v === 'object' && 'toISOString' in (v as object)) {
          values[field.key] = (v as typeof dayjs.prototype).format('YYYY-MM-DD')
        } else {
          values[field.key] = v ?? ''
        }
      }
      const payload: DirectoryEntryRequest = { values }
      let updated: { entries: DirectoryEntry[] }
      if (isEdit && entry) {
        updated = await directoriesApi.updateEntry(directory.id, entry.id, payload)
        const saved = updated.entries.find(e => e.id === entry.id)!
        notification.success({ message: 'Запись обновлена' })
        onSaved(saved)
      } else {
        updated = await directoriesApi.addEntry(directory.id, payload)
        const saved = updated.entries[updated.entries.length - 1]
        notification.success({ message: 'Запись создана' })
        onSaved(saved)
      }
      onClose()
    } catch {
      // validation error — stay open
    }
  }

  return (
    <Modal
      title={isEdit ? 'Редактировать запись' : 'Создать запись'}
      open={open}
      onOk={handleOk}
      onCancel={onClose}
      okText={isEdit ? 'Сохранить' : 'Создать'}
      cancelText="Отмена"
      destroyOnHidden
      width={480}
    >
      <Form form={form} layout="vertical" style={{ marginTop: 12 }}>
        {directory.fields.map(field => (
          <Form.Item
            key={field.key}
            name={field.key}
            label={field.label || field.key}
            rules={[]}
          >
            {field.type === 'BOOLEAN' ? (
              <Switch />
            ) : field.type === 'NUMBER' ? (
              <InputNumber style={{ width: '100%' }} />
            ) : field.type === 'DATE' ? (
              <DatePicker style={{ width: '100%' }} />
            ) : field.type === 'SELECT' && field.options?.length ? (
              <Select
                options={field.options.map(o => ({ value: o, label: o }))}
                allowClear
                showSearch
              />
            ) : (
              <Input />
            )}
          </Form.Item>
        ))}
      </Form>
    </Modal>
  )
}
