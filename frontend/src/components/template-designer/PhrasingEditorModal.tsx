import { useState, useEffect } from 'react'
import {
  Button,
  Input,
  Textarea,
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  Label,
} from '@/components/ui'
import { Plus } from 'lucide-react'
import PhrasingVariantEditor from './PhrasingVariantEditor'
import type { Phrasing, PhrasingVariant } from '../../types/templateModel'
import type { Parameter } from '../../types/template'

interface Props {
  open: boolean
  initial: Phrasing | null
  parameters: Parameter[]
  onSave: (phrasing: Phrasing) => void
  onClose: () => void
}

function newVariant(): PhrasingVariant {
  return { id: crypto.randomUUID(), condition: null, content: [] }
}

export default function PhrasingEditorModal({ open, initial, parameters, onSave, onClose }: Props) {
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [variants, setVariants] = useState<PhrasingVariant[]>([newVariant()])

  useEffect(() => {
    if (!open) return
    if (initial) {
      setName(initial.name)
      setDescription(initial.description ?? '')
      setVariants(initial.variants.length ? initial.variants : [newVariant()])
    } else {
      setName('')
      setDescription('')
      setVariants([newVariant()])
    }
  }, [open, initial])

  const handleSave = () => {
    if (!name.trim()) return
    onSave({
      id: initial?.id ?? crypto.randomUUID(),
      name: name.trim(),
      description: description.trim() || null,
      variants,
    })
    onClose()
  }

  const addVariant = () => {
    setVariants(vs => {
      const last = vs[vs.length - 1]
      const hasDefault = last && last.condition == null
      if (hasDefault) {
        return [...vs.slice(0, -1), newVariant(), last]
      }
      return [...vs, newVariant()]
    })
  }

  const updateVariant = (i: number, v: PhrasingVariant) =>
    setVariants(vs => vs.map((x, idx) => idx === i ? v : x))

  const deleteVariant = (i: number) =>
    setVariants(vs => vs.filter((_, idx) => idx !== i))

  const moveVariant = (i: number, dir: 'up' | 'down') => {
    setVariants(vs => {
      const arr = [...vs]
      const j = dir === 'up' ? i - 1 : i + 1
      if (j < 0 || j >= arr.length) return arr
      ;[arr[i], arr[j]] = [arr[j], arr[i]]
      return arr
    })
  }

  return (
    <Dialog open={open} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="sm:max-w-[680px]">
        <DialogHeader>
          <DialogTitle>
            {initial ? 'Редактировать формулировку' : 'Новая формулировка'}
          </DialogTitle>
        </DialogHeader>

        <div className="mt-4 flex flex-col gap-3">
          <div className="space-y-2">
            <Label htmlFor="phrasing-name">
              Название <span className="text-destructive">*</span>
            </Label>
            <Input
              id="phrasing-name"
              autoFocus
              value={name}
              onChange={e => setName(e.target.value)}
              placeholder="Неустойка"
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor="phrasing-description">Описание</Label>
            <Textarea
              id="phrasing-description"
              value={description}
              onChange={e => setDescription(e.target.value)}
              placeholder="Необязательное описание"
              rows={2}
            />
          </div>

          <div>
            <div className="flex justify-between items-center mb-2">
              <Label className="text-base font-semibold">Варианты</Label>
              <Button variant="outline" size="sm" onClick={addVariant}>
                <Plus className="size-4 mr-1" />
                Добавить вариант
              </Button>
            </div>
            {variants.map((v, i) => (
              <PhrasingVariantEditor
                key={v.id}
                variant={v}
                index={i}
                isFirst={i === 0}
                isLast={i === variants.length - 1}
                isDefault={v.condition == null}
                parameters={parameters}
                onChange={updated => updateVariant(i, updated)}
                onDelete={() => deleteVariant(i)}
                onMoveUp={() => moveVariant(i, 'up')}
                onMoveDown={() => moveVariant(i, 'down')}
              />
            ))}
            <p className="text-xs text-muted-foreground mt-2">
              Порядок важен: берётся первый вариант, чьё условие выполнено.
              Вариант без условия — умолчание (рекомендуется ставить последним).
            </p>
          </div>
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={onClose}>
            Отмена
          </Button>
          <Button onClick={handleSave} disabled={!name.trim()}>
            Сохранить
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
