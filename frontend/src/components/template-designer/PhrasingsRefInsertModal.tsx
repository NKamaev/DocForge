import { useState, useEffect } from 'react'
import {
  Button,
  Checkbox,
  Input,
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  RadioGroup,
  RadioGroupItem,
  Label,
  Badge,
} from '@/components/ui'
import type { Phrasing } from '../../types/templateModel'

interface SeparatorPreset {
  label: string
  value: string
}

const SEPARATOR_PRESETS: SeparatorPreset[] = [
  { label: 'Запятая', value: ', ' },
  { label: 'Точка с зап.', value: '; ' },
  { label: 'Пробел', value: ' ' },
  { label: 'Тире', value: ' — ' },
  { label: 'Абзац', value: '\n' },
]

interface Props {
  open: boolean
  phrasings: Phrasing[]
  initialIds?: string[]
  onInsert: (phrasingsIds: string[], displayNames: string[], separator: string) => void
  onClose: () => void
}

export default function PhrasingsRefInsertModal({
  open,
  phrasings,
  initialIds,
  onInsert,
  onClose,
}: Props) {
  const [selectedIds, setSelectedIds] = useState<string[]>(initialIds ?? [])
  const [separatorValue, setSeparatorValue] = useState<string>(', ')
  const [customSeparator, setCustomSeparator] = useState('')
  const [useCustom, setUseCustom] = useState(false)

  useEffect(() => {
    if (open) {
      setSelectedIds(initialIds ?? [])
      setSeparatorValue(', ')
      setCustomSeparator('')
      setUseCustom(false)
    }
  }, [open, initialIds])

  const effectiveSep = useCustom ? customSeparator : separatorValue

  const toggleId = (id: string) => {
    setSelectedIds(prev =>
      prev.includes(id) ? prev.filter(x => x !== id) : [...prev, id]
    )
  }

  const handleInsert = () => {
    if (selectedIds.length === 0) return
    const displayNames = selectedIds.map(id => phrasings.find(p => p.id === id)?.name ?? id)
    onInsert(selectedIds, displayNames, effectiveSep)
    onClose()
  }

  const previewText = selectedIds
    .map(id => phrasings.find(p => p.id === id)?.name ?? id)
    .join(effectiveSep === '\n' ? ' ↵ ' : effectiveSep)

  return (
    <Dialog open={open} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="sm:max-w-[480px]">
        <DialogHeader>
          <DialogTitle>Вставить формулировки</DialogTitle>
        </DialogHeader>

        <div className="mt-4">
          <Label className="text-base font-semibold mb-2 block">Выберите формулировки</Label>
          {phrasings.length === 0 ? (
            <p className="text-muted-foreground">Нет формулировок</p>
          ) : (
            <div className="flex flex-col gap-1">
              {phrasings.map(p => (
                <div
                  key={p.id}
                  className={`flex items-center justify-between p-2 rounded-md border cursor-pointer transition-colors ${
                    selectedIds.includes(p.id)
                      ? 'border-primary/50 bg-primary/5'
                      : 'border-border hover:border-primary/30'
                  }`}
                  onClick={() => toggleId(p.id)}
                >
                  <div className="flex items-center gap-2">
                    <Checkbox
                      checked={selectedIds.includes(p.id)}
                      onClick={e => e.stopPropagation()}
                      onCheckedChange={() => toggleId(p.id)}
                    />
                    <span>{p.name}</span>
                  </div>
                  <Badge variant="secondary" className="text-xs">
                    {p.variants.length} вар.
                  </Badge>
                </div>
              ))}
            </div>
          )}
        </div>

        {selectedIds.length > 1 && (
          <div className="mt-4">
            <Label className="text-base font-semibold mb-2 block">Разделитель</Label>
            <RadioGroup
              value={useCustom ? '__custom__' : separatorValue}
              onValueChange={value => {
                if (value === '__custom__') {
                  setUseCustom(true)
                } else {
                  setUseCustom(false)
                  setSeparatorValue(value)
                }
              }}
              className="flex flex-col gap-2"
            >
              {SEPARATOR_PRESETS.map(preset => (
                <div key={preset.value} className="flex items-center gap-2">
                  <RadioGroupItem value={preset.value} id={`preset-${preset.value}`} />
                  <Label htmlFor={`preset-${preset.value}`} className="cursor-pointer">
                    {preset.label}
                    <span className="text-muted-foreground text-xs ml-1.5">
                      {preset.value === '\n' ? '↵' : `"${preset.value}"`}
                    </span>
                  </Label>
                </div>
              ))}
              <div className="flex items-center gap-2">
                <RadioGroupItem value="__custom__" id="preset-custom" />
                <Label htmlFor="preset-custom" className="cursor-pointer">
                  Другой:
                </Label>
                <Input
                  value={customSeparator}
                  placeholder="введите разделитель"
                  className="w-[120px] h-8 text-xs"
                  onClick={e => { e.stopPropagation(); setUseCustom(true) }}
                  onChange={e => { setCustomSeparator(e.target.value); setUseCustom(true) }}
                />
              </div>
            </RadioGroup>
          </div>
        )}

        {selectedIds.length > 0 && (
          <div className="mt-4 bg-muted/50 rounded-md p-2.5">
            <span className="text-muted-foreground text-xs">Предпросмотр тега: </span>
            <span className="text-sm">📝 {previewText}</span>
          </div>
        )}

        <DialogFooter>
          <Button variant="outline" onClick={onClose}>
            Отмена
          </Button>
          <Button onClick={handleInsert} disabled={selectedIds.length === 0}>
            Вставить
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
