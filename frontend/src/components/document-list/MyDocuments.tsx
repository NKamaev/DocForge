import { useCallback, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  Badge,
  Button,
  Popconfirm,
  Space,
  Table,
  Tag,
  Typography,
  notification,
} from 'antd'
import type { TableProps } from 'antd'
import { DeleteOutlined, EditOutlined, PlusOutlined } from '@ant-design/icons'
import { documentsApi } from '../../api/documentsApi'
import type { DocumentListItem } from '../../types/document'
import CreateDocumentModal from './CreateDocumentModal'

const { Title } = Typography

const STATUS_LABELS: Record<string, { label: string; color: string }> = {
  DRAFT:     { label: 'Черновик',   color: 'default' },
  GENERATED: { label: 'Сгенерирован', color: 'blue' },
  EXPORTED:  { label: 'Экспортирован', color: 'green' },
}

export default function MyDocuments() {
  const navigate = useNavigate()
  const [documents, setDocuments] = useState<DocumentListItem[]>([])
  const [loading, setLoading] = useState(false)
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)   // Ant Design uses 1-based
  const [createOpen, setCreateOpen] = useState(false)

  const loadDocuments = useCallback(async (pageNum = 1) => {
    setLoading(true)
    try {
      const res = await documentsApi.list(pageNum - 1, 20)
      setDocuments(res.content)
      setTotal(res.totalElements)
    } catch {
      notification.error({ message: 'Не удалось загрузить документы' })
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { loadDocuments(page) }, [page, loadDocuments])

  const handleDelete = async (id: string) => {
    try {
      await documentsApi.delete(id)
      notification.success({ message: 'Документ удалён', duration: 2 })
      loadDocuments(page)
    } catch {
      notification.error({ message: 'Не удалось удалить документ' })
    }
  }

  const columns: TableProps<DocumentListItem>['columns'] = [
    {
      title: 'Название',
      dataIndex: 'name',
      key: 'name',
      render: (name: string, record) => (
        <Button
          type="link"
          style={{ padding: 0, textAlign: 'left', height: 'auto', fontWeight: 500 }}
          onClick={() => navigate(`/documents/${record.id}/fill`)}
        >
          {name}
        </Button>
      ),
    },
    {
      title: 'Статус',
      dataIndex: 'status',
      key: 'status',
      width: 160,
      render: (status: string) => {
        const s = STATUS_LABELS[status] ?? { label: status, color: 'default' }
        return <Tag color={s.color}>{s.label}</Tag>
      },
    },
    {
      title: 'Дата создания',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 180,
      render: (val: string) =>
        new Date(val).toLocaleDateString('ru-RU', {
          day: '2-digit', month: '2-digit', year: 'numeric',
          hour: '2-digit', minute: '2-digit',
        }),
    },
    {
      title: '',
      key: 'actions',
      width: 100,
      render: (_: unknown, record: DocumentListItem) => (
        <Space size={4}>
          <Button
            size="small"
            type="text"
            icon={<EditOutlined />}
            onClick={() => navigate(`/documents/${record.id}/fill`)}
          />
          <Popconfirm
            title="Удалить документ?"
            description="Это действие нельзя отменить."
            onConfirm={() => handleDelete(record.id)}
            okText="Удалить"
            cancelText="Отмена"
            okButtonProps={{ danger: true }}
          >
            <Button size="small" type="text" danger icon={<DeleteOutlined />} />
          </Popconfirm>
        </Space>
      ),
    },
  ]

  return (
    <div style={{ padding: '24px 32px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20 }}>
        <Space align="center">
          <Title level={3} style={{ margin: 0 }}>Мои документы</Title>
          <Badge count={total} showZero style={{ backgroundColor: '#8c8c8c' }} />
        </Space>
        <Button
          type="primary"
          icon={<PlusOutlined />}
          onClick={() => setCreateOpen(true)}
        >
          Создать документ
        </Button>
      </div>

      <Table<DocumentListItem>
        rowKey="id"
        columns={columns}
        dataSource={documents}
        loading={loading}
        scroll={{ x: 800 }}
        pagination={{
          current: page,
          total,
          pageSize: 20,
          showSizeChanger: false,
          hideOnSinglePage: true,
          onChange: p => setPage(p),
        }}
        locale={{ emptyText: 'Документов пока нет. Создайте первый!' }}
        onRow={record => ({
          style: { cursor: 'pointer' },
          onClick: e => {
            // avoid double-fire when clicking action buttons
            if ((e.target as HTMLElement).closest('button, .ant-btn')) return
            navigate(`/documents/${record.id}/fill`)
          },
        })}
      />

      <CreateDocumentModal
        open={createOpen}
        onClose={() => {
          setCreateOpen(false)
          loadDocuments(page)
        }}
      />
    </div>
  )
}
