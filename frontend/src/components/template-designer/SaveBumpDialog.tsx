import { useState } from 'react'
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  Button,
  Label,
  RadioGroup,
  RadioGroupItem,
  Textarea,
} from '@/components/ui'

interface Props {
  open: boolean
  onClose: () => void
  templateId: string
  templateName: string
  majorVersion: number
  minorVersion: number
  onConfirm: (bumpType: 'MAJOR' | 'MINOR', comment: string) => Promise<void>
}

export default function SaveBumpDialog({
  open,
  onClose,
  templateName,
  majorVersion,
  minorVersion,
  onConfirm,
}: Props) {
  const [bumpType, setBumpType] = useState<'MAJOR' | 'MINOR'>('MINOR')
  const [comment, setComment] = useState('')
  const [saving, setSaving] = useState(false)

  const nextMajor = bumpType === 'MAJOR' ? majorVersion + 1 : majorVersion
  const nextMinor = bumpType === 'MAJOR' ? 0 : minorVersion + 1
  const nextVersion = `${nextMajor}.${nextMinor}`

  const handleConfirm = async () => {
    setSaving(true)
    try {
      await onConfirm(bumpType, comment)
    } finally {
      setSaving(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={(o) => !o && !saving && onClose()}>
      <DialogContent className="sm:max-w-[480px]" style={{ maxWidth: 480 }}>
        <DialogHeader>
          <DialogTitle>Сохранение версии шаблона</DialogTitle>
        </DialogHeader>

        <div className="mt-4 space-y-4">
          <div>
            <p className="text-sm font-medium mb-2">
              {templateName}
            </p>
            <p className="text-xs text-muted-foreground">
              Текущая версия: <strong>v{majorVersion}.{minorVersion}</strong> → <strong>v{nextVersion}</strong>
            </p>
          </div>

          {/* Bump type */}
          <div className="space-y-2">
            <Label>Тип изменений</Label>
            <RadioGroup
              value={bumpType}
              onValueChange={(v) => setBumpType(v as 'MAJOR' | 'MINOR')}
              className="flex gap-3"
            >
              <div className="flex items-center gap-2 rounded-lg border p-3 flex-1 cursor-pointer has-[:checked]:border-primary has-[:checked]:bg-primary/5">
                <RadioGroupItem value="MINOR" id="minor" />
                <Label htmlFor="minor" className="cursor-pointer">
                  <span className="text-sm font-medium">Minor</span>
                  <p className="text-xs text-muted-foreground">
                    v{majorVersion}.{minorVersion} → v{majorVersion}.{minorVersion + 1}
                    <br />Небольшие правки
                  </p>
                </Label>
              </div>
              <div className="flex items-center gap-2 rounded-lg border p-3 flex-1 cursor-pointer has-[:checked]:border-primary has-[:checked]:bg-primary/5">
                <RadioGroupItem value="MAJOR" id="major" />
                <Label htmlFor="major" className="cursor-pointer">
                  <span className="text-sm font-medium">Major</span>
                  <p className="text-xs text-muted-foreground">
                    v{majorVersion}.{minorVersion} → v{majorVersion + 1}.0
                    <br />Крупные изменения
                  </p>
                </Label>
              </div>
            </RadioGroup>
          </div>

          {/* Comment */}
          <div className="space-y-2">
            <Label htmlFor="version-comment">Комментарий к версии</Label>
            <Textarea
              id="version-comment"
              value={comment}
              onChange={(e) => setComment(e.target.value)}
              placeholder="Что изменилось в этой версии?"
              rows={2}
            />
          </div>

          {/* Auto-description hint */}
          <div className="rounded-md bg-muted/50 p-2.5 text-xs text-muted-foreground">
            После сохранения будет автоматически сформировано описание изменений
            на основе сравнения с предыдущей версией.
          </div>
        </div>

        <DialogFooter className="mt-6">
          <Button variant="outline" onClick={onClose} disabled={saving}>
            Отмена
          </Button>
          <Button onClick={handleConfirm} disabled={saving}>
            {saving ? 'Сохранение...' : `Сохранить v${nextVersion}`}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
