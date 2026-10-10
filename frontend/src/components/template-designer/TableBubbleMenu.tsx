import { useState } from 'react'
import { useEditorState } from '@tiptap/react'
import type { Editor } from '@tiptap/react'
import {
  Button,
  ColorPicker,
  Input,
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
  Separator,
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from '@/components/ui'
import {
  AlignCenterVertical,
  AlignEndVertical,
  AlignStartVertical,
  ArrowDownToLine,
  ArrowLeftToLine,
  ArrowRightToLine,
  ArrowUpToLine,
  TableCellsMerge,
  TableCellsSplit,
  Trash2,
} from 'lucide-react'

interface Props {
  editor: Editor
}

type BorderSide = 'top' | 'right' | 'bottom' | 'left'
type BorderStyle = 'solid' | 'dashed' | 'dotted'

const BORDER_SIDES: BorderSide[] = ['top', 'right', 'bottom', 'left']
const SIDE_LABELS: Record<BorderSide, string> = { top: 'Верх', right: 'Право', bottom: 'Низ', left: 'Лево' }
const SIDE_SHORT: Record<BorderSide, string> = { top: 'В', right: 'П', bottom: 'Н', left: 'Л' }

function buildBorderValue(style: BorderStyle, width: number, color: string): string {
  return `${width}px ${style} ${color}`
}

function setCellBorders(editor: Editor, sides: BorderSide[] | 'all', value: string | null) {
  const targets = sides === 'all' ? BORDER_SIDES : sides
  const chain = editor.chain().focus()
  for (const side of targets) {
    const attr = `border${side.charAt(0).toUpperCase()}${side.slice(1)}` as
      'borderTop' | 'borderRight' | 'borderBottom' | 'borderLeft'
    chain.setCellAttribute(attr, value)
  }
  chain.run()
}

const ROW_STYLE: React.CSSProperties = {
  display: 'flex',
  alignItems: 'center',
  flexWrap: 'wrap',
  gap: 4,
  padding: '4px 12px',
}

export default function TableBubbleMenu({ editor }: Props) {
  const editorState = useEditorState({
    editor,
    selector: ({ editor: e }) => ({
      isInTable: e.isActive('table'),
      cellBgColor: e.getAttributes('tableCell').backgroundColor as string | undefined,
    }),
  })

  const [borderStyle, setBorderStyle] = useState<BorderStyle>('solid')
  const [borderWidth, setBorderWidth] = useState<number>(1)
  const [borderColor, setBorderColor] = useState<string>('#000000')

  if (!editorState.isInTable) return null

  const applyToSide = (side: BorderSide) =>
    setCellBorders(editor, [side], buildBorderValue(borderStyle, borderWidth, borderColor))

  return (
    <div style={{ borderBottom: '1px solid #bae0ff', background: '#e6f4ff' }}>

      {/* ── Row 1: structural operations ───────────────────────────── */}
      <div style={ROW_STYLE}>

        {/* Rows */}
        <TooltipProvider>
          <div className="flex gap-0.5 items-center">
            <Tooltip>
              <TooltipTrigger>
                <Button variant="outline" size="icon-sm"
                  onClick={() => editor.chain().focus().addRowBefore().run()}>
                  <ArrowUpToLine className="size-3.5" />
                </Button>
              </TooltipTrigger>
              <TooltipContent>Строка выше</TooltipContent>
            </Tooltip>
            <Tooltip>
              <TooltipTrigger>
                <Button variant="outline" size="icon-sm"
                  onClick={() => editor.chain().focus().addRowAfter().run()}>
                  <ArrowDownToLine className="size-3.5" />
                </Button>
              </TooltipTrigger>
              <TooltipContent>Строка ниже</TooltipContent>
            </Tooltip>
            <Tooltip>
              <TooltipTrigger>
                <Button variant="outline" size="icon-sm"
                  onClick={() => editor.chain().focus().deleteRow().run()}>
                  <Trash2 className="size-3.5 text-destructive" />
                </Button>
              </TooltipTrigger>
              <TooltipContent>Удалить строку</TooltipContent>
            </Tooltip>
          </div>
        </TooltipProvider>

        <Separator orientation="vertical" className="mx-1 h-5" />

        {/* Columns */}
        <TooltipProvider>
          <div className="flex gap-0.5 items-center">
            <Tooltip>
              <TooltipTrigger>
                <Button variant="outline" size="icon-sm"
                  onClick={() => editor.chain().focus().addColumnBefore().run()}>
                  <ArrowLeftToLine className="size-3" />
                </Button>
              </TooltipTrigger>
              <TooltipContent>Колонка слева</TooltipContent>
            </Tooltip>
            <Tooltip>
              <TooltipTrigger>
                <Button variant="outline" size="icon-sm"
                  onClick={() => editor.chain().focus().addColumnAfter().run()}>
                  <ArrowRightToLine className="size-3" />
                </Button>
              </TooltipTrigger>
              <TooltipContent>Колонка справа</TooltipContent>
            </Tooltip>
            <Tooltip>
              <TooltipTrigger>
                <Button variant="outline" size="icon-sm"
                  onClick={() => editor.chain().focus().deleteColumn().run()}>
                  <Trash2 className="size-3.5 text-destructive" />
                </Button>
              </TooltipTrigger>
              <TooltipContent>Удалить колонку</TooltipContent>
            </Tooltip>
          </div>
        </TooltipProvider>

        <Separator orientation="vertical" className="mx-1 h-5" />

        {/* Merge */}
        <TooltipProvider>
          <div className="flex gap-0.5 items-center">
            <Tooltip>
              <TooltipTrigger>
                <Button variant="outline" size="icon-sm"
                  onClick={() => editor.chain().focus().mergeCells().run()}
                  disabled={!editor.can().mergeCells()}>
                  <TableCellsMerge className="size-3.5" />
                </Button>
              </TooltipTrigger>
              <TooltipContent>Объединить ячейки</TooltipContent>
            </Tooltip>
            <Tooltip>
              <TooltipTrigger>
                <Button variant="outline" size="icon-sm"
                  onClick={() => editor.chain().focus().splitCell().run()}
                  disabled={!editor.can().splitCell()}>
                  <TableCellsSplit className="size-3.5" />
                </Button>
              </TooltipTrigger>
              <TooltipContent>Разбить ячейку</TooltipContent>
            </Tooltip>
          </div>
        </TooltipProvider>

        <Separator orientation="vertical" className="mx-1 h-5" />

        {/* Vertical align */}
        <div className="flex gap-0.5 items-center">
          <span style={{ fontSize: 11, color: '#595959' }}>Верт.:</span>
          <TooltipProvider>
            <Tooltip>
              <TooltipTrigger>
                <Button variant="outline" size="icon-sm"
                  onClick={() => editor.chain().focus().setCellAttribute('verticalAlign', 'top').run()}>
                  <AlignStartVertical className="size-3.5" />
                </Button>
              </TooltipTrigger>
              <TooltipContent>Сверху</TooltipContent>
            </Tooltip>
            <Tooltip>
              <TooltipTrigger>
                <Button variant="outline" size="icon-sm"
                  onClick={() => editor.chain().focus().setCellAttribute('verticalAlign', 'middle').run()}>
                  <AlignCenterVertical className="size-3.5" />
                </Button>
              </TooltipTrigger>
              <TooltipContent>По центру</TooltipContent>
            </Tooltip>
            <Tooltip>
              <TooltipTrigger>
                <Button variant="outline" size="icon-sm"
                  onClick={() => editor.chain().focus().setCellAttribute('verticalAlign', 'bottom').run()}>
                  <AlignEndVertical className="size-3.5" />
                </Button>
              </TooltipTrigger>
              <TooltipContent>Снизу</TooltipContent>
            </Tooltip>
          </TooltipProvider>
        </div>

        <Separator orientation="vertical" className="mx-1 h-5" />

        {/* Cell size */}
        <div className="flex gap-1 items-center">
          <span style={{ fontSize: 11, color: '#595959' }}>Шир.:</span>
          <TooltipProvider>
            <Tooltip>
              <TooltipTrigger asChild>
                <div className="flex items-center gap-0.5">
                  <Input
                    type="number"
                    className="h-6 w-[60px] text-xs"
                    min={20} max={800}
                    placeholder="Авто"
                    onChange={v => {
                      const val = parseInt(v.target.value)
                      if (val) editor.chain().focus().setCellAttribute('colwidth', [val]).run()
                      else editor.chain().focus().setCellAttribute('colwidth', null).run()
                    }}
                  />
                  <span style={{ fontSize: 10, color: '#595959' }}>px</span>
                </div>
              </TooltipTrigger>
              <TooltipContent>Ширина ячейки</TooltipContent>
            </Tooltip>
          </TooltipProvider>
          <span style={{ fontSize: 11, color: '#595959' }}>Выс.:</span>
          <TooltipProvider>
            <Tooltip>
              <TooltipTrigger asChild>
                <div className="flex items-center gap-0.5">
                  <Input
                    type="number"
                    className="h-6 w-[60px] text-xs"
                    min={20} max={500}
                    placeholder="Авто"
                    onChange={v => {
                      const val = parseInt(v.target.value)
                      editor.chain().focus().setCellAttribute('minHeight', val ?? null).run()
                    }}
                  />
                  <span style={{ fontSize: 10, color: '#595959' }}>px</span>
                </div>
              </TooltipTrigger>
              <TooltipContent>Высота ячейки</TooltipContent>
            </Tooltip>
          </TooltipProvider>
        </div>

        <Separator orientation="vertical" className="mx-1 h-5" />

        <TooltipProvider>
          <Tooltip>
            <TooltipTrigger>
              <Button variant="outline" size="icon-sm"
                onClick={() => editor.chain().focus().deleteTable().run()}>
                <Trash2 className="size-3.5 text-destructive" />
              </Button>
            </TooltipTrigger>
            <TooltipContent>Удалить таблицу</TooltipContent>
          </Tooltip>
        </TooltipProvider>
      </div>

      {/* ── Row 2: cell styling ──────────────────────────────── */}
      <div style={{ ...ROW_STYLE, borderTop: '1px solid #bae0ff', background: '#f0f9ff' }}>

        {/* Cell background */}
          <div className="flex gap-1 items-center">
            <span style={{ fontSize: 11, color: '#595959' }}>Фон:</span>
            <ColorPicker
              value={editorState.cellBgColor || '#ffffff'}
              onChange={(c) =>
                editor.chain().focus().setCellAttribute('backgroundColor', c).run()
              }
              data-testid="table-cell-bg-color"
            />
            <TooltipProvider>
            <Tooltip>
              <TooltipTrigger>
                <Button variant="outline" size="icon-sm" style={{ fontSize: 11 }}
                  onClick={() => editor.chain().focus().setCellAttribute('backgroundColor', null).run()}>
                  ✕
                </Button>
              </TooltipTrigger>
              <TooltipContent>Сбросить фон</TooltipContent>
            </Tooltip>
            </TooltipProvider>
          </div>

        <Separator orientation="vertical" className="mx-1 h-5" />

        {/* Text orientation */}
        <div className="flex gap-0.5 items-center">
          <span style={{ fontSize: 11, color: '#595959' }}>Текст:</span>
          <TooltipProvider>
            <Tooltip>
              <TooltipTrigger>
                <Button variant="outline" size="icon-sm" style={{ fontSize: 11 }}
                  onClick={() => editor.chain().focus().setCellAttribute('textOrientation', null).run()}>
                  0°
                </Button>
              </TooltipTrigger>
              <TooltipContent>Горизонтально (обычный)</TooltipContent>
            </Tooltip>
            <Tooltip>
              <TooltipTrigger>
                <Button variant="outline" size="icon-sm" style={{ fontSize: 11 }}
                  onClick={() => editor.chain().focus().setCellAttribute('textOrientation', 'rotate-90').run()}>
                  90°
                </Button>
              </TooltipTrigger>
              <TooltipContent>Вертикально по часовой (90°)</TooltipContent>
            </Tooltip>
            <Tooltip>
              <TooltipTrigger>
                <Button variant="outline" size="icon-sm" style={{ fontSize: 11 }}
                  onClick={() => editor.chain().focus().setCellAttribute('textOrientation', 'rotate-180').run()}>
                  180°
                </Button>
              </TooltipTrigger>
              <TooltipContent>Перевёрнуто (180°)</TooltipContent>
            </Tooltip>
            <Tooltip>
              <TooltipTrigger>
                <Button variant="outline" size="icon-sm" style={{ fontSize: 11 }}
                  onClick={() => editor.chain().focus().setCellAttribute('textOrientation', 'rotate-270').run()}>
                  270°
                </Button>
              </TooltipTrigger>
              <TooltipContent>Вертикально против часовой (270°)</TooltipContent>
            </Tooltip>
          </TooltipProvider>
        </div>

        <Separator orientation="vertical" className="mx-1 h-5" />

        {/* Borders */}
        <div className="flex gap-1 items-center flex-wrap">
          <span style={{ fontSize: 11, color: '#595959' }}>Граница:</span>
          <Select value={borderStyle} onValueChange={(v: BorderStyle) => setBorderStyle(v)}>
            <TooltipProvider>
              <Tooltip>
                <TooltipTrigger asChild>
                  <SelectTrigger className="h-6 w-[80px] text-xs">
                    <SelectValue />
                  </SelectTrigger>
                </TooltipTrigger>
                <TooltipContent>Стиль границы</TooltipContent>
              </Tooltip>
            </TooltipProvider>
            <SelectContent>
              <SelectItem value="solid">Сплошная</SelectItem>
              <SelectItem value="dashed">Пунктир</SelectItem>
              <SelectItem value="dotted">Точки</SelectItem>
            </SelectContent>
          </Select>
          <TooltipProvider>
            <Tooltip>
              <TooltipTrigger asChild>
                <Input
                  type="number"
                  className="h-6 w-[48px] text-xs"
                  min={1} max={4}
                  value={borderWidth}
                  onChange={e => setBorderWidth(parseInt(e.target.value) || 1)}
                />
              </TooltipTrigger>
              <TooltipContent>Толщина границы</TooltipContent>
            </Tooltip>
          </TooltipProvider>
          <span style={{ fontSize: 10, color: '#595959' }}>px</span>
          <ColorPicker
            value={borderColor}
            onChange={(c) => setBorderColor(c)}
            data-testid="table-border-color"
          />
          <span style={{ fontSize: 11, color: '#8c8c8c' }}>→</span>
          {BORDER_SIDES.map(side => (
            <TooltipProvider key={side}>
              <Tooltip>
                <TooltipTrigger>
                  <Button
                    variant="outline"
                    size="icon-sm"
                    style={{ fontSize: 10, minWidth: 28, height: 24 }}
                    onClick={() => applyToSide(side)}
                  >
                    {SIDE_SHORT[side]}
                  </Button>
                </TooltipTrigger>
                <TooltipContent>Применить к: {SIDE_LABELS[side]}</TooltipContent>
              </Tooltip>
            </TooltipProvider>
          ))}
          <TooltipProvider>
            <Tooltip>
              <TooltipTrigger>
                <Button
                  variant="default"
                  size="icon-sm"
                  style={{ fontSize: 10, height: 24 }}
                  onClick={() => setCellBorders(editor, 'all', buildBorderValue(borderStyle, borderWidth, borderColor))}
                >
                  Все
                </Button>
              </TooltipTrigger>
              <TooltipContent>Применить ко всем сторонам</TooltipContent>
            </Tooltip>
          </TooltipProvider>
          <TooltipProvider>
            <Tooltip>
              <TooltipTrigger>
                <Button
                  variant="outline"
                  size="icon-sm"
                  style={{ fontSize: 10, height: 24 }}
                  className="text-destructive"
                  onClick={() => setCellBorders(editor, 'all', null)}
                >
                  Нет
                </Button>
              </TooltipTrigger>
              <TooltipContent>Убрать все границы</TooltipContent>
            </Tooltip>
          </TooltipProvider>
        </div>
      </div>
    </div>
  )
}
