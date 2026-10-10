import { useState } from 'react'
import {
  Button,
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  Input,
  Textarea,
  Label,
  showToast,
} from '@/components/ui'
import { templatesApi } from '../../api/templatesApi'
import type { CreateTemplateRequest } from '../../types/template'

interface Props {
  open: boolean
  onClose: () => void
  onCreated: () => void
}

export default function CreateTemplateModal({ open, onClose, onCreated }: Props) {
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [category, setCategory] = useState('')
  const [loading, setLoading] = useState(false)
  const [errors, setErrors] = useState<{ name?: string }>({})

  const handleCreate = async () => {
    if (!name.trim()) {
      setErrors({ name: 'Введите название шаблона' })
      return
    }

    const values: CreateTemplateRequest = {
      name: name.trim(),
      description: description.trim() || undefined,
      category: category.trim() || undefined,
    }

    setLoading(true)
    try {
      await templatesApi.createTemplate(values)
      showToast('Шаблон создан', 'success')
      handleReset()
      onCreated()
    } catch {
      showToast('Не удалось создать шаблон', 'error')
    } finally {
      setLoading(false)
    }
  }

  const handleReset = () => {
    setName('')
    setDescription('')
    setCategory('')
    setErrors({})
  }

  const handleCancel = () => {
    handleReset()
    onClose()
  }

  return (
    <Dialog open={open} onOpenChange={(open) => !open && handleCancel()}>
      <DialogContent className="sm:max-w-[500px]">
        <DialogHeader>
          <DialogTitle>Создать шаблон</DialogTitle>
        </DialogHeader>

        <div className="mt-4 space-y-4">
          <div className="space-y-2">
            <Label htmlFor="template-name">
              Название <span className="text-destructive">*</span>
            </Label>
            <Input
              id="template-name"
              value={name}
              onChange={(e) => {
                setName(e.target.value)
                if (errors.name) setErrors({})
              }}
              placeholder="Договор оказания услуг"
              maxLength={500}
              className={errors.name ? 'border-destructive' : ''}
            />
            {errors.name && (
              <p className="text-xs text-destructive">{errors.name}</p>
            )}
          </div>

          <div className="space-y-2">
            <Label htmlFor="template-description">Описание</Label>
            <Textarea
              id="template-description"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="Краткое описание шаблона"
              rows={3}
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor="template-category">Категория</Label>
            <Input
              id="template-category"
              value={category}
              onChange={(e) => setCategory(e.target.value)}
              placeholder="Договоры"
              maxLength={100}
            />
          </div>
        </div>

        <DialogFooter className="mt-6">
          <Button variant="outline" onClick={handleCancel}>
            Отмена
          </Button>
          <Button onClick={handleCreate} disabled={loading}>
            {loading ? 'Создание...' : 'Создать'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
