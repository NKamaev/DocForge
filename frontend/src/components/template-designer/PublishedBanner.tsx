import { Alert } from 'antd'
import { LockOutlined } from '@ant-design/icons'

export default function PublishedBanner() {
  return (
    <Alert
      type="warning"
      icon={<LockOutlined />}
      showIcon
      message="Шаблон в режиме только для чтения (опубликован)"
      style={{ borderRadius: 0, flexShrink: 0 }}
    />
  )
}
