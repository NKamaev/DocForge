import { useEffect, useState } from 'react'
import { Button, Popconfirm, Space, Table, Tag, Tooltip, Typography, message } from 'antd'
import { CheckCircleOutlined, DeleteOutlined, EditOutlined, PlusOutlined, StopOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import type { TablePaginationConfig } from 'antd'
import type { TemplateListItem } from '../../types/template'
import { templatesApi } from '../../api/templatesApi'
import CreateTemplateModal from './CreateTemplateModal'

const { Title } = Typography

export default function TemplateList() {
  const [items, setItems] = useState<TemplateListItem[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(0)
  const [pageSize, setPageSize] = useState(20)
  const [loading, setLoading] = useState(false)
  const [modalOpen, setModalOpen] = useState(false)
  const navigate = useNavigate()

  const load = async (p = page, s = pageSize) => {
    setLoading(true)
    try {
      const data = await templatesApi.getTemplates({ page: p, size: s })
      setItems(data.content)
      setTotal(data.totalElements)
    } catch {
      message.error('Не удалось загрузить шаблоны')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { load() }, []) // eslint-disable-line react-hooks/exhaustive-deps

  const handleTableChange = (pagination: TablePaginationConfig) => {
    const newPage = (pagination.current ?? 1) - 1
    const newSize = pagination.pageSize ?? pageSize
    setPage(newPage)
    setPageSize(newSize)
    load(newPage, newSize)
  }

  const handlePublish = async (id: string, currentlyPublished: boolean) => {
    try {
      if (currentlyPublished) {
        await templatesApi.unpublishTemplate(id)
        message.success('Шаблон снят с публикации — создание документов закрыто')
      } else {
        await templatesApi.publishTemplate(id)
        message.success('Шаблон опубликован — теперь можно создавать документы')
      }
      load()
    } catch {
      message.error(currentlyPublished ? 'Не удалось снять шаблон с публикации' : 'Не удалось опубликовать шаблон')
    }
  }

  const handleDelete = async (id: string) => {
    try {
      await templatesApi.deleteTemplate(id)
      message.success('Шаблон удалён')
      load()
    } catch (err: any) {
      const detail = err?.response?.data?.detail
      message.error(detail ?? 'Не удалось удалить шаблон')
    }
  }

  const columns = [
    {
      title: 'Название',
      dataIndex: 'name',
      key: 'name',
    },
    {
      title: 'Категория',
      dataIndex: 'category',
      key: 'category',
      render: (v: string | null) => v ?? '—',
    },
    {
      title: 'Статус',
      dataIndex: 'published',
      key: 'published',
      width: 140,
      render: (published: boolean) =>
        published
          ? <Tag color="green">Опубликован</Tag>
          : <Tag color="default">Черновик</Tag>,
    },
    {
      title: 'Дата создания',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 180,
      render: (v: string) => new Date(v).toLocaleDateString('ru-RU'),
    },
    {
      title: '',
      key: 'actions',
      width: 110,
      render: (_: unknown, record: TemplateListItem) => (
        <Space>
          <Tooltip title="Открыть редактор">
            <Button
              type="link"
              size="small"
              icon={<EditOutlined />}
              onClick={() => navigate(`/templates/${record.id}/edit`)}
            />
          </Tooltip>
          <Popconfirm
            title={record.published ? 'Снять с публикации?' : 'Опубликовать шаблон?'}
            okText={record.published ? 'Да, снять' : 'Опубликовать'}
            cancelText="Отмена"
            onConfirm={() => handlePublish(record.id, record.published)}
          >
            <Tooltip title={record.published ? 'Снять с публикации' : 'Опубликовать шаблон'}>
              <Button
                type="link"
                size="small"
                icon={record.published ? <StopOutlined /> : <CheckCircleOutlined />}
                style={{ color: record.published ? undefined : '#52c41a' }}
              />
            </Tooltip>
          </Popconfirm>
          <Popconfirm
            title="Удалить шаблон?"
            description="Это действие нельзя отменить."
            okText="Удалить"
            cancelText="Отмена"
            okButtonProps={{ danger: true }}
            onConfirm={() => handleDelete(record.id)}
          >
            <Tooltip title="Удалить шаблон">
              <Button type="link" danger size="small" icon={<DeleteOutlined />} />
            </Tooltip>
          </Popconfirm>
        </Space>
      ),
    },
  ]

  return (
    <div style={{ padding: 24 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <Title level={3} style={{ margin: 0 }}>Шаблоны</Title>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => setModalOpen(true)}>
          Создать шаблон
        </Button>
      </div>

      <Table
        rowKey="id"
        columns={columns}
        dataSource={items}
        loading={loading}
        scroll={{ x: 800 }}
        pagination={{
          current: page + 1,
          pageSize,
          total,
          showSizeChanger: true,
          showTotal: (t) => `Всего: ${t}`,
        }}
        onChange={handleTableChange}
      />

      <CreateTemplateModal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        onCreated={() => { setModalOpen(false); load() }}
      />
    </div>
  )
}
