import { useEffect, useState } from 'react'
import {
  Input,
  Label,
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
  Separator,
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
} from '@/components/ui'
import type { DocumentFormatting } from '../../types/template'

interface Props {
  open: boolean
  onClose: () => void
  formatting: DocumentFormatting
  onChange: (f: DocumentFormatting) => void
}

const PAGE_SIZES = [
  { value: 'A4', label: 'A4 (210 × 297 мм)' },
  { value: 'A3', label: 'A3 (297 × 420 мм)' },
  { value: 'Letter', label: 'Letter (216 × 279 мм)' },
]

const FONT_FAMILIES = [
  { value: 'Times New Roman, serif', label: 'Times New Roman' },
  { value: 'Arial, sans-serif', label: 'Arial' },
  { value: 'Calibri, sans-serif', label: 'Calibri' },
  { value: 'Courier New, monospace', label: 'Courier New' },
]

const LINE_SPACINGS = [
  { value: '1', label: '1.0 — одинарный' },
  { value: '1.15', label: '1.15' },
  { value: '1.5', label: '1.5 — полуторный' },
  { value: '2', label: '2.0 — двойной' },
]

export default function PageSettingsDrawer({ open, onClose, formatting, onChange }: Props) {
  const [local, setLocal] = useState<DocumentFormatting>(formatting)

  useEffect(() => {
    if (open) setLocal(formatting)
  }, [open, formatting])

  const update = (partial: Partial<DocumentFormatting>) => {
    const next = { ...local, ...partial }
    setLocal(next)
    onChange(next)
  }

  const updateMargins = (key: 'top' | 'right' | 'bottom' | 'left', value: number) => {
    update({ margins: { ...local.margins, [key]: value } })
  }

  const updateParagraphDefaults = (key: string, value: number | undefined) => {
    update({ paragraphDefaults: { ...(local.paragraphDefaults ?? {}), [key]: value ?? undefined } })
  }

  return (
    <Sheet open={open} onOpenChange={(o) => { if (!o) onClose() }}>
      <SheetContent side="right" className="w-[360px] sm:max-w-[360px] overflow-y-auto">
        <SheetHeader>
          <SheetTitle>Параметры страницы</SheetTitle>
        </SheetHeader>

        <p className="text-xs text-muted-foreground mt-2 mb-4">
          Изменения применяются немедленно и сохраняются вместе с шаблоном.
        </p>

        <Separator className="my-3" />

        {/* Page */}
        <p className="text-xs font-medium text-muted-foreground mb-3">Страница</p>

        <div className="space-y-3">
          <div className="space-y-1.5">
            <Label>Формат</Label>
            <Select value={local.pageSize} onValueChange={v => update({ pageSize: v as DocumentFormatting['pageSize'] })}>
              <SelectTrigger className="w-full">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {PAGE_SIZES.map(ps => (
                  <SelectItem key={ps.value} value={ps.value}>{ps.label}</SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          <div className="space-y-1.5">
            <Label>Ориентация</Label>
            <div className="flex gap-2">
              <button
                type="button"
                className={`flex-1 rounded-lg border px-3 py-1.5 text-sm transition-colors ${local.orientation === 'portrait' ? 'border-primary bg-primary/5 text-primary' : 'border-input hover:border-muted-foreground/30'}`}
                onClick={() => update({ orientation: 'portrait' })}
              >
                Книжная
              </button>
              <button
                type="button"
                className={`flex-1 rounded-lg border px-3 py-1.5 text-sm transition-colors ${local.orientation === 'landscape' ? 'border-primary bg-primary/5 text-primary' : 'border-input hover:border-muted-foreground/30'}`}
                onClick={() => update({ orientation: 'landscape' })}
              >
                Альбомная
              </button>
            </div>
          </div>
        </div>

        <Separator className="my-3" />

        {/* Margins */}
        <p className="text-xs font-medium text-muted-foreground mb-3">Поля (мм)</p>

        <div className="grid grid-cols-2 gap-3">
          <div className="space-y-1.5">
            <Label>Верхнее</Label>
            <div className="flex items-center gap-1">
              <Input
                type="number"
                value={local.margins.top}
                onChange={e => updateMargins('top', Number(e.target.value))}
                min={0} max={100}
              />
              <span className="text-xs text-muted-foreground">мм</span>
            </div>
          </div>
          <div className="space-y-1.5">
            <Label>Нижнее</Label>
            <div className="flex items-center gap-1">
              <Input
                type="number"
                value={local.margins.bottom}
                onChange={e => updateMargins('bottom', Number(e.target.value))}
                min={0} max={100}
              />
              <span className="text-xs text-muted-foreground">мм</span>
            </div>
          </div>
          <div className="space-y-1.5">
            <Label>Левое</Label>
            <div className="flex items-center gap-1">
              <Input
                type="number"
                value={local.margins.left}
                onChange={e => updateMargins('left', Number(e.target.value))}
                min={0} max={100}
              />
              <span className="text-xs text-muted-foreground">мм</span>
            </div>
          </div>
          <div className="space-y-1.5">
            <Label>Правое</Label>
            <div className="flex items-center gap-1">
              <Input
                type="number"
                value={local.margins.right}
                onChange={e => updateMargins('right', Number(e.target.value))}
                min={0} max={100}
              />
              <span className="text-xs text-muted-foreground">мм</span>
            </div>
          </div>
        </div>

        <Separator className="my-3" />

        {/* Font */}
        <p className="text-xs font-medium text-muted-foreground mb-3">Шрифт по умолчанию</p>

        <div className="space-y-3">
          <div className="space-y-1.5">
            <Label>Гарнитура</Label>
            <Select value={local.defaultFont} onValueChange={v => update({ defaultFont: v })}>
              <SelectTrigger className="w-full">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {FONT_FAMILIES.map(f => (
                  <SelectItem key={f.value} value={f.value}>{f.label}</SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          <div className="space-y-1.5">
            <Label>Кегль (пт)</Label>
            <div className="flex items-center gap-1">
              <Input
                type="number"
                value={local.defaultFontSize}
                onChange={e => update({ defaultFontSize: Number(e.target.value) })}
                min={6} max={72}
              />
              <span className="text-xs text-muted-foreground">пт</span>
            </div>
          </div>

          <div className="space-y-1.5">
            <Label>Межстрочный интервал</Label>
            <Select
              value={String(local.lineSpacing)}
              onValueChange={v => update({ lineSpacing: Number(v) })}
            >
              <SelectTrigger className="w-full">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {LINE_SPACINGS.map(ls => (
                  <SelectItem key={ls.value} value={ls.value}>{ls.label}</SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        </div>

        <Separator className="my-3" />

        {/* Paragraph defaults */}
        <p className="text-xs font-medium text-muted-foreground mb-3">Абзац по умолчанию</p>

        <p className="text-[11px] text-muted-foreground mb-3">
          Применяются ко всем абзацам и заголовкам, у которых не задан явный отступ.
        </p>

        <div className="grid grid-cols-2 gap-3">
          <div className="space-y-1.5">
            <Label>Интервал перед</Label>
            <div className="flex items-center gap-1">
              <Input
                type="number"
                value={local.paragraphDefaults?.spaceBefore ?? ''}
                onChange={e => updateParagraphDefaults('spaceBefore', e.target.value ? Number(e.target.value) : undefined)}
                min={0} max={200}
              />
              <span className="text-xs text-muted-foreground">пт</span>
            </div>
          </div>
          <div className="space-y-1.5">
            <Label>Интервал после</Label>
            <div className="flex items-center gap-1">
              <Input
                type="number"
                value={local.paragraphDefaults?.spaceAfter ?? ''}
                onChange={e => updateParagraphDefaults('spaceAfter', e.target.value ? Number(e.target.value) : undefined)}
                min={0} max={200}
              />
              <span className="text-xs text-muted-foreground">пт</span>
            </div>
          </div>
          <div className="space-y-1.5">
            <Label>Отступ слева</Label>
            <div className="flex items-center gap-1">
              <Input
                type="number"
                value={local.paragraphDefaults?.marginLeft ?? ''}
                onChange={e => updateParagraphDefaults('marginLeft', e.target.value ? Number(e.target.value) : undefined)}
                min={0} max={200}
              />
              <span className="text-xs text-muted-foreground">мм</span>
            </div>
          </div>
          <div className="space-y-1.5">
            <Label>Отступ справа</Label>
            <div className="flex items-center gap-1">
              <Input
                type="number"
                value={local.paragraphDefaults?.marginRight ?? ''}
                onChange={e => updateParagraphDefaults('marginRight', e.target.value ? Number(e.target.value) : undefined)}
                min={0} max={200}
              />
              <span className="text-xs text-muted-foreground">мм</span>
            </div>
          </div>
          <div className="space-y-1.5">
            <Label>Первая строка</Label>
            <div className="flex items-center gap-1">
              <Input
                type="number"
                value={local.paragraphDefaults?.firstLineIndent ?? ''}
                onChange={e => updateParagraphDefaults('firstLineIndent', e.target.value ? Number(e.target.value) : undefined)}
                min={0} max={100}
              />
              <span className="text-xs text-muted-foreground">мм</span>
            </div>
          </div>
        </div>
      </SheetContent>
    </Sheet>
  )
}
