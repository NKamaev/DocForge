import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  Card,
  Form,
  Input,
  Modal,
  Radio,
  Select,
  Spin,
  Typography,
  notification,
} from 'antd'
import { FileTextOutlined } from '@ant-design/icons'
import { templatesApi } from '../../api/templatesApi'
import { documentsApi } from '../../api/documentsApi'
import { foldersApi } from '../../api/foldersApi'
import type { TemplateListItem } from '../../types/template'
import type { Folder } from '../../types/folder'

const { Text } = Typography

interface Props {
  open: boolean
  onClose: () => void
  /** Pre-select this folder when modal opens (e.g. current folder on DocumentsPage) */
  defaultFolderId?: string | null
}

export default function CreateDocumentModal({ open, onClose, defaultFolderId }: Props) {
  const navigate = useNavigate()
  const [form] = Form.useForm<{ name: string }>()

  const [templates, setTemplates] = useState<TemplateListItem[]>([])
  const [folders, setFolders] = useState<Folder[]>([])
  const [loadingTemplates, setLoadingTemplates] = useState(false)
  const [selectedTemplateId, setSelectedTemplateId] = useState<string | null>(null)
  const [selectedFolderId, setSelectedFolderId] = useState<string | null>(null)
  const [creating, setCreating] = useState(false)

  useEffect(() => {
    if (!open) return
    setSelectedTemplateId(null)
    setSelectedFolderId(defaultFolderId ?? null)
    form.resetFields()

    setLoadingTemplates(true)
    Promise.all([
      templatesApi.getTemplates({ published: true, size: 100 }),
      foldersApi.list(),
    ])
      .then(([tmplRes, folderRes]) => {
        setTemplates(tmplRes.content)
        setFolders(folderRes)
      })
      .catch(() => notification.error({ message: 'Не удалось загрузить данные' }))
      .finally(() => setLoadingTemplates(false))
  }, [open, form, defaultFolderId])

  // When template changes, pre-fill folder from template's defaultFolderId
  const handleTemplateSelect = (templateId: string) => {
    setSelectedTemplateId(templateId)
    // Only auto-set folder if user hasn't already chosen one explicitly
    const tmpl = templates.find(t => t.id === templateId)
    if (tmpl && 'defaultFolderId' in tmpl) {
      const dfi = (tmpl as { defaultFolderId?: string | null }).defaultFolderId
      if (dfi) setSelectedFolderId(dfi)
    }
  }

  const handleOk = async () => {
    if (!selectedTemplateId) {
      notification.warning({ message: 'Выберите шаблон' })
      return
    }
    const values = await form.validateFields()
    setCreating(true)
    try {
      const doc = await documentsApi.create({
        templateId: selectedTemplateId,
        name: values.name,
        folderId: selectedFolderId ?? null,
      })
      onClose()
      navigate(`/documents/${doc.id}/fill`)
    } catch {
      notification.error({ message: 'Не удалось создать документ' })
    } finally {
      setCreating(false)
    }
  }

  const folderOptions = [
    { value: '', label: 'Без папки' },
    ...folders.map(f => ({ value: f.id, label: f.name })),
  ]

  return (
    <Modal
      title="Создать документ"
      open={open}
      onOk={handleOk}
      onCancel={onClose}
      okText="Создать"
      cancelText="Отмена"
      confirmLoading={creating}
      width={640}
      destroyOnHidden
    >
      <Form form={form} layout="vertical" style={{ marginTop: 12 }}>
        <Form.Item
          name="name"
          label="Название документа"
          rules={[{ required: true, message: 'Введите название' }]}
        >
          <Input placeholder="Договор с ООО «Ромашка»" autoFocus />
        </Form.Item>

        <Form.Item label="Папка">
          <Select
            value={selectedFolderId ?? ''}
            onChange={v => setSelectedFolderId(v === '' ? null : v)}
            options={folderOptions}
            style={{ width: '100%' }}
          />
        </Form.Item>
      </Form>

      <Text strong>Выберите шаблон</Text>

      {loadingTemplates ? (
        <div style={{ textAlign: 'center', padding: '24px 0' }}>
          <Spin />
        </div>
      ) : templates.length === 0 ? (
        <Text type="secondary" style={{ display: 'block', marginTop: 12 }}>
          Нет опубликованных шаблонов
        </Text>
      ) : (
        <Radio.Group
          value={selectedTemplateId}
          onChange={e => handleTemplateSelect(e.target.value as string)}
          style={{ width: '100%', marginTop: 12 }}
        >
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10, maxHeight: 280, overflowY: 'auto' }}>
            {templates.map(t => (
              <Radio.Button
                key={t.id}
                value={t.id}
                style={{ height: 'auto', padding: 0, borderRadius: 8 }}
              >
                <Card
                  size="small"
                  style={{
                    border: selectedTemplateId === t.id ? '2px solid #1677ff' : '1px solid transparent',
                    borderRadius: 8,
                    cursor: 'pointer',
                    background: selectedTemplateId === t.id ? '#e6f4ff' : undefined,
                  }}
                >
                  <div style={{ display: 'flex', alignItems: 'flex-start', gap: 10 }}>
                    <FileTextOutlined
                      style={{
                        fontSize: 20,
                        color: selectedTemplateId === t.id ? '#1677ff' : '#8c8c8c',
                        flexShrink: 0,
                        marginTop: 2,
                      }}
                    />
                    <div style={{ minWidth: 0 }}>
                      <Text strong style={{ display: 'block', color: selectedTemplateId === t.id ? '#1677ff' : undefined, wordBreak: 'break-word' }}>
                        {t.name}
                      </Text>
                      {t.description && (
                        <Text type="secondary" style={{ fontSize: 12, display: 'block', marginTop: 2 }} ellipsis>
                          {t.description}
                        </Text>
                      )}
                      {t.category && (
                        <Text style={{ fontSize: 11, color: '#aaa' }}>{t.category}</Text>
                      )}
                    </div>
                  </div>
                </Card>
              </Radio.Button>
            ))}
          </div>
        </Radio.Group>
      )}
    </Modal>
  )
}
