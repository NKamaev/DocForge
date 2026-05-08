import { useState } from 'react'
import { Button, Input, Modal, Tabs, Upload, notification, Image } from 'antd'
import { InboxOutlined } from '@ant-design/icons'
import type { UploadFile, UploadProps } from 'antd'
import type { Editor } from '@tiptap/react'
import { uploadsApi } from '../../api/uploadsApi'

interface Props {
  open: boolean
  onClose: () => void
  editor: Editor
}

export default function ImageUploadModal({ open, onClose, editor }: Props) {
  const [uploading, setUploading] = useState(false)
  const [uploadedUrl, setUploadedUrl] = useState<string | null>(null)
  const [fileList, setFileList] = useState<UploadFile[]>([])
  const [urlValue, setUrlValue] = useState('')

  const handleClose = () => {
    setUploadedUrl(null)
    setFileList([])
    setUrlValue('')
    onClose()
  }

  const insertImage = (src: string) => {
    if (!src.trim()) return
    editor.chain().focus().setImage({ src }).run()
    handleClose()
  }

  const uploadProps: UploadProps = {
    name: 'file',
    accept: 'image/*',
    maxCount: 1,
    fileList,
    beforeUpload: async (file) => {
      setUploading(true)
      setUploadedUrl(null)
      try {
        const res = await uploadsApi.uploadImage(file)
        // Prefix with backend base URL so img src resolves correctly
        const fullUrl = `http://localhost:8080${res.url}`
        setUploadedUrl(fullUrl)
        setFileList([{ ...file, status: 'done', url: fullUrl, uid: res.id }])
      } catch {
        notification.error({ message: 'Не удалось загрузить изображение' })
        setFileList([])
      } finally {
        setUploading(false)
      }
      return false // prevent antd default upload
    },
    onRemove: () => {
      setUploadedUrl(null)
      setFileList([])
    },
  }

  const tabs = [
    {
      key: 'upload',
      label: 'Загрузить',
      children: (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          <Upload.Dragger {...uploadProps}>
            <p className="ant-upload-drag-icon">
              <InboxOutlined />
            </p>
            <p className="ant-upload-text">Перетащите изображение или нажмите для выбора</p>
            <p className="ant-upload-hint">Поддерживаются JPEG, PNG, GIF, WebP. Максимум 10 МБ.</p>
          </Upload.Dragger>

          {uploadedUrl && (
            <div style={{ textAlign: 'center' }}>
              <Image
                src={uploadedUrl}
                alt="preview"
                style={{ maxHeight: 200, maxWidth: '100%', objectFit: 'contain', borderRadius: 4 }}
                preview={false}
              />
            </div>
          )}

          <Button
            type="primary"
            disabled={!uploadedUrl || uploading}
            loading={uploading}
            onClick={() => uploadedUrl && insertImage(uploadedUrl)}
            block
          >
            Вставить
          </Button>
        </div>
      ),
    },
    {
      key: 'url',
      label: 'По URL',
      children: (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          <Input
            placeholder="https://example.com/image.png"
            value={urlValue}
            onChange={(e) => setUrlValue(e.target.value)}
            onPressEnter={() => insertImage(urlValue)}
          />
          {urlValue && (
            <div style={{ textAlign: 'center' }}>
              <Image
                src={urlValue}
                alt="preview"
                style={{ maxHeight: 200, maxWidth: '100%', objectFit: 'contain', borderRadius: 4 }}
                preview={false}
                fallback="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=="
              />
            </div>
          )}
          <Button
            type="primary"
            disabled={!urlValue.trim()}
            onClick={() => insertImage(urlValue)}
            block
          >
            Вставить
          </Button>
        </div>
      ),
    },
  ]

  return (
    <Modal
      title="Вставить изображение"
      open={open}
      onCancel={handleClose}
      footer={null}
      width={480}
      destroyOnHidden
    >
      <Tabs items={tabs} />
    </Modal>
  )
}
