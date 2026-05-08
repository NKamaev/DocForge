import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Form, Input, Modal, notification } from 'antd'
import { directoriesApi } from '../api/directoriesApi'
import type { DirectoryListItem } from '../types/directory'
import { Icon } from '../components/Icon'

export default function DirectoriesPage() {
  const navigate = useNavigate()
  const [items, setItems] = useState<DirectoryListItem[]>([])
  const [loading, setLoading] = useState(true)
  const [modalOpen, setModalOpen] = useState(false)
  const [saving, setSaving] = useState(false)
  const [form] = Form.useForm<{ name: string }>()

  const load = () => {
    setLoading(true)
    directoriesApi
      .list()
      .then(setItems)
      .catch(() => notification.error({ message: 'Не удалось загрузить справочники' }))
      .finally(() => setLoading(false))
  }

  useEffect(() => { load() }, [])

  const handleCreate = async () => {
    const { name } = await form.validateFields()
    setSaving(true)
    try {
      await directoriesApi.create({ name, fields: [] })
      setModalOpen(false)
      form.resetFields()
      load()
    } catch {
      notification.error({ message: 'Ошибка создания' })
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="page">
      <div className="page-head">
        <div>
          <div className="crumb">
            <Icon name="home" size={12} />
            <span onClick={() => navigate('/')} style={{ cursor: 'pointer' }}>Главная</span>
            <Icon name="chev-right" size={11} />
            <span>Справочники</span>
          </div>
          <h1>Справочники</h1>
          <p>Структурированные данные для повторного использования в шаблонах: города, организации, сотрудники.</p>
        </div>
        <div className="page-head-actions">
          <button className="btn primary" onClick={() => setModalOpen(true)}>
            <Icon name="plus" size={14} /> Создать справочник
          </button>
        </div>
      </div>

      {loading ? (
        <div style={{ padding: 48, textAlign: 'center', color: 'var(--ink-3)' }}>Загрузка…</div>
      ) : items.length === 0 ? (
        <div className="card pad" style={{ textAlign: 'center', padding: 48 }}>
          <div style={{ color: 'var(--ink-3)', marginBottom: 16 }}>Нет справочников</div>
          <button className="btn primary" onClick={() => setModalOpen(true)}>
            <Icon name="plus" size={14} /> Создать первый
          </button>
        </div>
      ) : (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 14 }}>
          {items.map((d) => (
            <div
              key={d.id}
              className="card hover pad"
              onClick={() => navigate(`/directories/${d.id}`)}
              style={{ display: 'flex', flexDirection: 'column', gap: 10 }}
            >
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                <div style={{ width: 32, height: 32, borderRadius: 8, background: 'var(--bg-soft)', color: 'var(--ink-2)', display: 'grid', placeItems: 'center', border: '1px solid var(--line)' }}>
                  <Icon name="book" size={16} />
                </div>
                <Icon name="chev-right" size={14} />
              </div>
              <div style={{ fontSize: 15, fontWeight: 550 }}>{d.name}</div>
              <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 11.5, color: 'var(--ink-3)' }}>
                <span>
                  <b className="tabular" style={{ color: 'var(--ink-1)', fontWeight: 600 }}>{d.entryCount}</b> записей
                </span>
                <span>{d.fields.length} полей</span>
                <span>{new Date().toLocaleDateString('ru-RU')}</span>
              </div>
            </div>
          ))}
        </div>
      )}

      <Modal
        title="Новый справочник"
        open={modalOpen}
        onOk={handleCreate}
        onCancel={() => { setModalOpen(false); form.resetFields() }}
        confirmLoading={saving}
        okText="Создать"
        cancelText="Отмена"
        destroyOnHidden
      >
        <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item name="name" label="Название" rules={[{ required: true, message: 'Введите название' }]}>
            <Input placeholder="Список банков" autoFocus />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
