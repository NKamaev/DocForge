import { useState, useRef } from 'react'
import {
  Button,
  Input,
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  Tabs,
  TabsContent,
  TabsList,
  TabsTrigger,
  showToast,
} from '@/components/ui'
import { Upload } from 'lucide-react'
import type { Editor } from '@tiptap/react'
import { uploadsApi } from '../../api/uploadsApi'

function showNotification(type: 'success' | 'error', message: string) {
  showToast(message, type)
}

interface Props {
  open: boolean
  onClose: () => void
  editor: Editor
}

export default function ImageUploadModal({ open, onClose, editor }: Props) {
  const [uploading, setUploading] = useState(false)
  const [uploadedUrl, setUploadedUrl] = useState<string | null>(null)
  const [urlValue, setUrlValue] = useState('')
  const [isDragOver, setIsDragOver] = useState(false)
  const fileInputRef = useRef<HTMLInputElement>(null)

  const handleClose = () => {
    setUploadedUrl(null)
    setUrlValue('')
    setIsDragOver(false)
    onClose()
  }

  const insertImage = (src: string) => {
    if (!src.trim()) return
    editor.chain().focus().setImage({ src }).run()
    handleClose()
  }

  const handleFileSelect = async (file: File) => {
    if (!file.type.startsWith('image/')) {
      showNotification('error', 'Выберите изображение')
      return
    }

    setUploading(true)
    setUploadedUrl(null)

    try {
      const res = await uploadsApi.uploadImage(file)
      const fullUrl = `http://localhost:8080${res.url}`
      setUploadedUrl(fullUrl)
    } catch {
      showNotification('error', 'Не удалось загрузить изображение')
    } finally {
      setUploading(false)
    }
  }

  const handleDragOver = (e: React.DragEvent) => {
    e.preventDefault()
    setIsDragOver(true)
  }

  const handleDragLeave = () => {
    setIsDragOver(false)
  }

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault()
    setIsDragOver(false)
    
    const files = e.dataTransfer.files
    if (files.length > 0) {
      handleFileSelect(files[0])
    }
  }

  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const files = e.target.files
    if (files && files.length > 0) {
      handleFileSelect(files[0])
    }
  }

  return (
    <Dialog open={open} onOpenChange={(open) => !open && handleClose()}>
      <DialogContent className="sm:max-w-[480px]">
        <DialogHeader>
          <DialogTitle>Вставить изображение</DialogTitle>
        </DialogHeader>

        <Tabs defaultValue="upload" className="mt-2">
          <TabsList className="grid w-full grid-cols-2">
            <TabsTrigger value="upload">Загрузить</TabsTrigger>
            <TabsTrigger value="url">По URL</TabsTrigger>
          </TabsList>

          <TabsContent value="upload" className="mt-4">
            <div className="flex flex-col gap-4">
              <div
                className={`flex flex-col items-center justify-center p-8 border-2 border-dashed rounded-lg cursor-pointer transition-colors ${
                  isDragOver
                    ? 'border-primary bg-primary/5'
                    : 'border-border hover:border-primary/50 hover:bg-muted/50'
                }`}
                onDragOver={handleDragOver}
                onDragLeave={handleDragLeave}
                onDrop={handleDrop}
                onClick={() => fileInputRef.current?.click()}
              >
                <input
                  ref={fileInputRef}
                  type="file"
                  accept="image/*"
                  className="hidden"
                  onChange={handleInputChange}
                />
                <Upload className="size-12 text-muted-foreground mb-2" />
                <p className="text-sm font-medium">
                  Перетащите изображение или нажмите для выбора
                </p>
                <p className="text-xs text-muted-foreground mt-1">
                  Поддерживаются JPEG, PNG, GIF, WebP. Максимум 10 МБ.
                </p>
              </div>

              {uploading && (
                <div className="text-center py-4">
                  <p className="text-sm text-muted-foreground">Загрузка...</p>
                </div>
              )}

              {uploadedUrl && !uploading && (
                <div className="text-center">
                  <img
                    src={uploadedUrl}
                    alt="preview"
                    className="max-h-[200px] max-w-full object-contain rounded-md mx-auto"
                  />
                </div>
              )}

              <Button
                type="button"
                disabled={!uploadedUrl || uploading}
                onClick={() => uploadedUrl && insertImage(uploadedUrl)}
                className="w-full"
              >
                {uploading ? 'Загрузка...' : 'Вставить'}
              </Button>
            </div>
          </TabsContent>

          <TabsContent value="url" className="mt-4">
            <div className="flex flex-col gap-3">
              <Input
                placeholder="https://example.com/image.png"
                value={urlValue}
                onChange={(e) => setUrlValue(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') {
                    insertImage(urlValue)
                  }
                }}
              />

              {urlValue && (
                <div className="text-center">
                  <img
                    src={urlValue}
                    alt="preview"
                    className="max-h-[200px] max-w-full object-contain rounded-md mx-auto"
                    onError={(e) => {
                      e.currentTarget.style.display = 'none'
                    }}
                  />
                </div>
              )}

              <Button
                type="button"
                disabled={!urlValue.trim()}
                onClick={() => insertImage(urlValue)}
                className="w-full"
              >
                Вставить
              </Button>
            </div>
          </TabsContent>
        </Tabs>
      </DialogContent>
    </Dialog>
  )
}
