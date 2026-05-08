import { Form, Input, Modal, message } from 'antd'
import { useState } from 'react'
import { templatesApi } from '../../api/templatesApi'
import type { CreateTemplateRequest } from '../../types/template'

interface Props {
  open: boolean
  onClose: () => void
  onCreated: () => void
}

export default function CreateTemplateModal({ open, onClose, onCreated }: Props) {
  const [form] = Form.useForm<CreateTemplateRequest>()
  const [loading, setLoading] = useState(false)

  const handleOk = async () => {
    const values = await form.validateFields()
    setLoading(true)
    try {
      await templatesApi.createTemplate(values)
      message.success('Шаблон создан')
      form.resetFields()
      onCreated()
    } catch {
      message.error('Не удалось создать шаблон')
    } finally {
      setLoading(false)
    }
  }

  const handleCancel = () => {
    form.resetFields()
    onClose()
  }

  return (
    <Modal
      title="Создать шаблон"
      open={open}
      onOk={handleOk}
      onCancel={handleCancel}
      okText="Создать"
      cancelText="Отмена"
      confirmLoading={loading}
      destroyOnHidden
    >
      <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
        <Form.Item
          name="name"
          label="Название"
          rules={[{ required: true, message: 'Введите название шаблона' }]}
        >
          <Input placeholder="Договор оказания услуг" maxLength={500} />
        </Form.Item>

        <Form.Item name="description" label="Описание">
          <Input.TextArea rows={3} placeholder="Краткое описание шаблона" />
        </Form.Item>

        <Form.Item name="category" label="Категория">
          <Input placeholder="Договоры" maxLength={100} />
        </Form.Item>
      </Form>
    </Modal>
  )
}
