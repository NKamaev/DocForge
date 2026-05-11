import { useEffect, useReducer, useState } from 'react'
import {
  Button,
  Input,
  Tooltip,
  TooltipContent,
  TooltipTrigger,
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuGroup,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
  MultiSelect,
  Popover,
  PopoverContent,
  PopoverTrigger,
} from '@/components/ui'
import { ColorPicker } from '@/components/ui/color-picker'
import type { Editor } from '@tiptap/react'
import {
  AlignCenter,
  AlignLeft,
  AlignRight,
  Menu,
  Bold,
  Italic,
  Minus,
  Link,
  Image,
  ListOrdered,
  Strikethrough,
  Table,
  Underline,
  List,
} from 'lucide-react'
import { Icon } from '../Icon'
import type { DocumentFormatting, Parameter, Phrasing } from '../../types/template'
import { autoNumberingPluginKey } from './extensions/NumberedClauseNode'
import type { NumberingStyle } from './extensions/NumberedClauseNode'

interface Props {
  editor: Editor
  parameters?: Parameter[]
  phrasings?: Phrasing[]
  onImageClick?: () => void
  formatting?: DocumentFormatting
}

function ToolBtn({
  title,
  active,
  onClick,
  disabled,
  children,
}: {
  title: string
  active?: boolean
  onClick: () => void
  disabled?: boolean
  children: React.ReactNode
}) {
  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <button
          className={'tb-btn' + (active ? ' active' : '')}
          onClick={onClick}
          disabled={disabled}
        >
          {children}
        </button>
      </TooltipTrigger>
      <TooltipContent>{title}</TooltipContent>
    </Tooltip>
  )
}

function TbSelect({
  value,
  onChange,
  options,
  style,
  placeholder,
}: {
  value?: string
  onChange: (v: string) => void
  options: { value: string; label: string }[]
  style?: React.CSSProperties
  placeholder?: string
}) {
  return (
    <div className="tb-select-wrap" style={style}>
      <select
        className="tb-select"
        value={value ?? ''}
        onChange={(e) => onChange(e.target.value)}
      >
        {placeholder && <option value="" disabled>{placeholder}</option>}
        {options.map((o) => (
          <option key={o.value} value={o.value}>{o.label}</option>
        ))}
      </select>
      <Icon name="chev-down" size={14} className="tb-select-chevron" />
    </div>
  )
}

const PLACEHOLDER_PARAMS: Parameter[] = [
  { code: 'client_name', label: 'ФИО клиента', type: 'FIO', required: true },
  { code: 'contract_date', label: 'Дата договора', type: 'DATE', required: true },
  { code: 'contract_type', label: 'Тип договора', type: 'SELECT', required: true },
  { code: 'has_penalty', label: 'Предусмотрена неустойка', type: 'BOOLEAN', required: false },
  { code: 'penalty_percent', label: 'Процент неустойки', type: 'NUMBER', required: false },
]

const LEVEL_LABELS: Record<number, string> = {
  1: 'Уровень 1',
  2: 'Уровень 2',
  3: 'Уровень 3',
  4: 'Уровень 4',
  5: 'Уровень 5',
}

const STYLE_LABELS: Record<NumberingStyle, string> = {
  'decimal':  '1 2 3 (числа)',
  'alpha-ru': 'а б в (рус.)',
  'alpha-en': 'a b c (лат.)',
}

const FONT_FAMILIES = [
  { value: 'Times New Roman, serif', label: 'Times New Roman' },
  { value: 'Arial, sans-serif', label: 'Arial' },
  { value: 'Calibri, sans-serif', label: 'Calibri' },
  { value: 'Courier New, monospace', label: 'Courier New' },
]

const FONT_SIZES = ['8', '9', '10', '11', '12', '14', '16', '18', '20', '24', '28', '36']

const LINE_SPACINGS = [
  { value: '1', label: '1.0' },
  { value: '1.15', label: '1.15' },
  { value: '1.5', label: '1.5' },
  { value: '2', label: '2.0' },
]

export default function EditorToolbar({ editor, parameters, phrasings = [], onImageClick, formatting }: Props) {
  const [linkUrl, setLinkUrl] = useState('')
  const [paragraphPanelOpen, setParagraphPanelOpen] = useState(false)
  const [paramMultiOpen, setParamMultiOpen] = useState(false)
  const [selectedParams, setSelectedParams] = useState<string[]>([])

  const [, forceUpdate] = useReducer((x: number) => x + 1, 0)
  useEffect(() => {
    editor.on('selectionUpdate', forceUpdate)
    editor.on('transaction', forceUpdate)
    return () => {
      editor.off('selectionUpdate', forceUpdate)
      editor.off('transaction', forceUpdate)
    }
  }, [editor])

  const insertTable = () =>
    editor.chain().focus().insertTable({ rows: 3, cols: 3, withHeaderRow: false }).run()

  const insertHorizontalRule = () =>
    editor.chain().focus().setHorizontalRule().run()

  useEffect(() => {
    if (!paramMultiOpen) setSelectedParams([])
  }, [paramMultiOpen])

  const visibleParams = (parameters && parameters.length > 0) ? parameters : PLACEHOLDER_PARAMS

  const isListStart = editor.isActive('numberedClause', { listStart: true })

  const buildClauseRefItems = () => {
    const state = autoNumberingPluginKey.getState(editor.view.state)
    if (!state?.byId.size) {
      return [{ label: 'Нет нумерованных пунктов', disabled: true, onClick: () => {} }]
    }

    const items: { label: string; disabled?: boolean; onClick: () => void }[] = []
    editor.view.state.doc.descendants((node, _pos) => {
      if (node.type.name !== 'numberedClause') return
      const id = node.attrs.id as string | null
      if (!id) return
      const label = state.byId.get(id)
      if (!label) return

      let text = ''
      node.descendants((child) => {
        if (child.isText && text.length < 50) text += child.text ?? ''
      })
      if (text.length > 50) text = text.slice(0, 50) + '…'
      const preview = text.trim() || '(пустой пункт)'

      items.push({
        label: `${label} — ${preview}`,
        onClick: () => editor.chain().focus().insertClauseRef(id).run(),
      })
    })

    return items.length ? items : [{ label: 'Нет нумерованных пунктов', disabled: true, onClick: () => {} }]
  }

  const currentFontFamily =
    (editor.getAttributes('textStyle').fontFamily as string | undefined) ??
    formatting?.defaultFont

  const currentFontSize = (() => {
    const fs = editor.getAttributes('textStyle').fontSize as string | undefined
    if (fs) return fs.replace('pt', '')
    return formatting?.defaultFontSize != null ? String(formatting.defaultFontSize) : undefined
  })()

  const currentLineSpacing = (() => {
    for (const type of ['paragraph', 'heading', 'numberedClause'] as const) {
      const attrs = editor.getAttributes(type)
      if (attrs.lineSpacing) return attrs.lineSpacing as string
    }
    return formatting?.lineSpacing != null ? String(formatting.lineSpacing) : undefined
  })()

  const currentBlockType = (['numberedClause', 'paragraph', 'heading'] as const).find(
    (t) => editor.isActive(t),
  ) ?? null
  const blockAttrs = currentBlockType ? editor.getAttributes(currentBlockType) : {}

  const parseUnit = (v: unknown): number | null => {
    if (!v || typeof v !== 'string') return null
    const n = parseFloat(v)
    return isNaN(n) ? null : n
  }

  const applySpacing = (key: string, value: number | null, unit: string) => {
    editor.chain().focus().setParagraphSpacing({
      [key]: value !== null ? `${value}${unit}` : null,
    }).run()
  }

  const insertLink = (url: string) => {
    if (!url) {
      editor.chain().focus().unsetLink().run()
      return
    }
    const href = url.startsWith('http') ? url : `https://${url}`
    editor.chain().focus().setLink({ href, target: '_blank' }).run()
  }

  return (
    <div className="toolbar">
      {/* Fonts */}
      <div className="flex items-center gap-[1px]">
        <TbSelect
          value={currentFontFamily}
          onChange={(v) => editor.chain().focus().setFontFamily(v).run()}
          options={FONT_FAMILIES}
          style={{ width: 145 }}
        />
        <TbSelect
          value={currentFontSize}
          onChange={(v) => editor.chain().focus().setFontSize(`${v}pt`).run()}
          options={FONT_SIZES.map((s) => ({ value: s, label: s }))}
          style={{ width: 60 }}
          placeholder="Размер"
        />
        <TbSelect
          value={currentLineSpacing}
          onChange={(v) => editor.chain().focus().setLineSpacing(v).run()}
          options={LINE_SPACINGS}
          style={{ width: 60 }}
        />
      </div>

      <div className="sep" />

      {/* Paragraph panel toggle */}
      <Tooltip>
        <TooltipTrigger asChild>
          <button
            className={'tb-btn-lbl' + (paragraphPanelOpen ? ' active' : '')}
            onClick={() => setParagraphPanelOpen((v) => !v)}
          >
            <Menu size={14} />
            Абзац
          </button>
        </TooltipTrigger>
        <TooltipContent>Настройки абзаца</TooltipContent>
      </Tooltip>

      <div className="sep" />

      {/* Basic formatting */}
      <div className="flex items-center gap-[1px]">
        <ToolBtn title="Жирный (Ctrl+B)" active={editor.isActive('bold')} onClick={() => editor.chain().focus().toggleBold().run()}>
          <Bold size={14} />
        </ToolBtn>
        <ToolBtn title="Курсив (Ctrl+I)" active={editor.isActive('italic')} onClick={() => editor.chain().focus().toggleItalic().run()}>
          <Italic size={14} />
        </ToolBtn>
        <ToolBtn title="Подчёркнутый (Ctrl+U)" active={editor.isActive('underline')} onClick={() => editor.chain().focus().toggleUnderline().run()}>
          <Underline size={14} />
        </ToolBtn>
        <ToolBtn title="Зачёркнутый" active={editor.isActive('strike')} onClick={() => editor.chain().focus().toggleStrike().run()}>
          <Strikethrough size={14} />
        </ToolBtn>
        <ToolBtn title="Надстрочный" active={editor.isActive('superscript')} onClick={() => editor.chain().focus().toggleSuperscript().run()}>
          x²
        </ToolBtn>
        <ToolBtn title="Подстрочный" active={editor.isActive('subscript')} onClick={() => editor.chain().focus().toggleSubscript().run()}>
          x₂
        </ToolBtn>
      </div>

      <div className="sep" />

      {/* Color pickers */}
      <div className="flex items-center gap-[1px]">
        <Tooltip>
          <TooltipTrigger asChild>
            <div>
              <ColorPicker
                value={editor.getAttributes('textStyle').color as string | undefined}
                onChange={(color) => editor.chain().focus().setColor(color).run()}
                onClear={() => editor.chain().focus().unsetColor().run()}
                allowClear
              />
            </div>
          </TooltipTrigger>
          <TooltipContent>Цвет текста</TooltipContent>
        </Tooltip>

        <Tooltip>
          <TooltipTrigger asChild>
            <div>
              <ColorPicker
                value={editor.getAttributes('highlight').color as string | undefined}
                onChange={(color) => editor.chain().focus().setHighlight({ color: color }).run()}
                onClear={() => editor.chain().focus().unsetHighlight().run()}
                allowClear
              />
            </div>
          </TooltipTrigger>
          <TooltipContent>Цвет фона</TooltipContent>
        </Tooltip>
      </div>

      <div className="sep" />

      {/* Headings */}
      <div className="flex items-center gap-[1px]">
        {([1, 2, 3] as const).map((level) => (
          <ToolBtn
            key={level}
            title={`Заголовок ${level}`}
            active={editor.isActive('heading', { level })}
            onClick={() => editor.chain().focus().toggleHeading({ level }).run()}
          >
            H{level}
          </ToolBtn>
        ))}
      </div>

      <div className="sep" />

      {/* Alignment */}
      <div className="flex items-center gap-[1px]">
        <ToolBtn title="По левому краю" active={editor.isActive({ textAlign: 'left' })} onClick={() => editor.chain().focus().setTextAlign('left').run()}>
          <AlignLeft size={14} />
        </ToolBtn>
        <ToolBtn title="По центру" active={editor.isActive({ textAlign: 'center' })} onClick={() => editor.chain().focus().setTextAlign('center').run()}>
          <AlignCenter size={14} />
        </ToolBtn>
        <ToolBtn title="По правому краю" active={editor.isActive({ textAlign: 'right' })} onClick={() => editor.chain().focus().setTextAlign('right').run()}>
          <AlignRight size={14} />
        </ToolBtn>
        <ToolBtn title="По ширине" active={editor.isActive({ textAlign: 'justify' })} onClick={() => editor.chain().focus().setTextAlign('justify').run()}>
          <span style={{ fontWeight: 700 }}>≡</span>
        </ToolBtn>
      </div>

      <div className="sep" />

      {/* Indent */}
      <div className="flex items-center gap-[1px]">
        <ToolBtn title="Увеличить отступ (Tab)" onClick={() => editor.chain().focus().indent().run()}>
          <Icon name="indent-r" size={14} />
        </ToolBtn>
        <ToolBtn title="Уменьшить отступ (Shift+Tab)" onClick={() => editor.chain().focus().outdent().run()}>
          <Icon name="indent-l" size={14} />
        </ToolBtn>
      </div>

      <div className="sep" />

      {/* Lists */}
      <div className="flex items-center gap-[1px]">
        <ToolBtn title="Маркированный список" active={editor.isActive('bulletList')} onClick={() => editor.chain().focus().toggleBulletList().run()}>
          <List size={14} />
        </ToolBtn>
        <ToolBtn title="Нумерованный список" active={editor.isActive('orderedList')} onClick={() => editor.chain().focus().toggleOrderedList().run()}>
          <ListOrdered size={14} />
        </ToolBtn>
      </div>

      <div className="sep" />

      {/* Insert */}
      <div className="flex items-center gap-[1px]">
        <ToolBtn title="Вставить таблицу" onClick={insertTable}>
          <Table size={14} />
        </ToolBtn>
        <ToolBtn title="Горизонтальная линия" onClick={insertHorizontalRule}>
          <Minus size={14} />
        </ToolBtn>
        <ToolBtn title="Разрыв страницы (Ctrl+Enter)" onClick={() => editor.chain().focus().insertPageBreak().run()}>
          <Icon name="page-break" size={14} />
        </ToolBtn>
        <ToolBtn title="Вставить изображение" onClick={() => onImageClick?.()}>
          <Image size={14} />
        </ToolBtn>

        {/* Link dropdown */}
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <button className={'tb-btn' + (editor.isActive('link') ? ' active' : '')}>
              <Link size={14} />
            </button>
          </DropdownMenuTrigger>
          <DropdownMenuContent className="w-80 p-2" align="start">
            <div className="flex items-center gap-2 mb-2">
              <Input
                value={linkUrl}
                onChange={(e) => setLinkUrl(e.target.value)}
                placeholder="https://..."
                onKeyDown={(e) => {
                  if (e.key === 'Enter') { insertLink(linkUrl); setLinkUrl('') }
                }}
              />
              <Button size="sm" onClick={() => { insertLink(linkUrl); setLinkUrl('') }}>
                Вставить
              </Button>
            </div>
            {editor.isActive('link') && (
              <DropdownMenuItem variant="destructive" onClick={() => editor.chain().focus().unsetLink().run()}>
                Удалить ссылку
              </DropdownMenuItem>
            )}
          </DropdownMenuContent>
        </DropdownMenu>
      </div>

      <div className="sep" />

      {/* Template-specific */}
      <div className="flex items-center gap-[1px]">
        {/* Parameters dropdown with multi-select */}
        <Popover open={paramMultiOpen} onOpenChange={setParamMultiOpen}>
          <PopoverTrigger asChild>
            <button className="tb-btn-lbl">
              <Icon name="param" size={14} />
              Параметр
            </button>
          </PopoverTrigger>
          <PopoverContent className="w-80 p-3" align="start">
            <div className="space-y-3">
              <p className="text-xs font-medium text-muted-foreground">
                Выберите параметры для вставки
              </p>
              <MultiSelect
                options={visibleParams.flatMap((p) => {
                  if (p.type === 'DIRECTORY' && p.directoryFields && p.directoryFields.length > 0) {
                    return p.directoryFields.map((fieldKey) => ({
                      value: `${p.code}__${fieldKey}`,
                      label: `${p.label} → ${fieldKey}`,
                      description: p.code,
                    }))
                  }
                  return [{
                    value: p.code,
                    label: p.label,
                    description: p.code,
                  }]
                })}
                selected={selectedParams}
                onChange={setSelectedParams}
                placeholder="Выберите параметры..."
                searchPlaceholder="Поиск параметра..."
                emptyText="Нет параметров"
                maxDisplay={10}
              />
              <div className="flex justify-end gap-2">
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => setParamMultiOpen(false)}
                >
                  Отмена
                </Button>
                <Button
                  size="sm"
                  onClick={() => {
                    const chain = editor.chain().focus()
                    selectedParams.forEach((code) => {
                      const p = visibleParams.find((par) => par.code === code || code.startsWith(par.code + '__'))
                      if (p) {
                        if (code.includes('__')) {
                          chain.insertParameterField({
                            parameterCode: code,
                            display: code,
                          })
                        } else {
                          chain.insertParameterField({
                            parameterCode: p.code,
                            display: p.label,
                          })
                        }
                      } else {
                        chain.insertParameterField({
                          parameterCode: code,
                          display: code,
                        })
                      }
                    })
                    chain.run()
                    setParamMultiOpen(false)
                  }}
                  disabled={selectedParams.length === 0}
                >
                  Вставить ({selectedParams.length})
                </Button>
              </div>
            </div>
          </PopoverContent>
        </Popover>

        {/* Clause dropdown */}
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <button className={'tb-btn-lbl' + (editor.isActive('numberedClause') ? ' active' : '')}>
              <Icon name="hash" size={14} />
              Пункт
            </button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="start">
            <DropdownMenuGroup>
              <DropdownMenuLabel>Вставить пункт</DropdownMenuLabel>
              {([1, 2, 3, 4, 5] as const).map((level) => (
                <DropdownMenuItem key={`insert-${level}`} onClick={() => editor.chain().focus().insertNumberedClause(level).run()}>
                  {LEVEL_LABELS[level]}
                </DropdownMenuItem>
              ))}
            </DropdownMenuGroup>
            <DropdownMenuSeparator />
            <DropdownMenuGroup>
              <DropdownMenuLabel>Стиль нумерации (текущий пункт)</DropdownMenuLabel>
              {(['decimal', 'alpha-ru', 'alpha-en'] as NumberingStyle[]).map((style) => (
                <DropdownMenuItem
                  key={`style-${style}`}
                  disabled={!editor.isActive('numberedClause')}
                  onClick={() => editor.chain().focus().setClauseNumberingStyle(style).run()}
                >
                  {STYLE_LABELS[style]}
                </DropdownMenuItem>
              ))}
            </DropdownMenuGroup>
            <DropdownMenuSeparator />
            <DropdownMenuItem disabled={!editor.isActive('numberedClause')} onClick={() => editor.chain().focus().toggleClauseListStart().run()}>
              {isListStart ? '✓ ' : ''}Начать новый список
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>

        {/* Conditional inline button */}
        <Tooltip>
          <TooltipTrigger asChild>
            <button
              className="tb-btn-lbl"
              disabled={editor.state.selection.empty}
              onClick={() => editor.chain().focus().wrapInConditionalInline({ label: 'Условный текст', condition: { type: 'simple', expression: '' } }).run()}
            >
              <Icon name="branch" size={14} />
              Условный текст
            </button>
          </TooltipTrigger>
          <TooltipContent>
            {editor.state.selection.empty ? 'Выделите текст для обёртки в условие' : 'Обернуть выделение в условный текст'}
          </TooltipContent>
        </Tooltip>

        {/* Clause ref dropdown */}
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <button className="tb-btn-lbl">
              <Icon name="link" size={14} />
              Ссылка на пункт
            </button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="start">
            {buildClauseRefItems().map((item, idx) => (
              <DropdownMenuItem key={idx} disabled={item.disabled} onClick={item.onClick}>{item.label}</DropdownMenuItem>
            ))}
          </DropdownMenuContent>
        </DropdownMenu>

        {/* Phrasings dropdown */}
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <button className="tb-btn-lbl">
              📝 Формулировки
            </button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="start">
            {phrasings.length === 0 ? (
              <DropdownMenuItem disabled>Нет формулировок (создайте в панели «Формулировки»)</DropdownMenuItem>
            ) : (
              phrasings.map(p => (
                <DropdownMenuItem
                  key={p.id}
                  onClick={() => editor.chain().focus().insertPhrasingsRef({ phrasingsIds: [p.id], displayNames: [p.name], separator: ', ' }).run()}
                >
                  <span className="font-medium">📝 {p.name}</span>
                  <span className="ml-2 text-xs text-muted-foreground">{p.variants.length} вар.</span>
                </DropdownMenuItem>
              ))
            )}
          </DropdownMenuContent>
        </DropdownMenu>
      </div>

      {/* Paragraph spacing panel (collapsible) */}
      {paragraphPanelOpen && (
        <div className="flex items-center flex-wrap gap-2 p-1.5 px-3 border-t border-muted" style={{ width: '100%' }}>
          <span className="text-xs text-muted-foreground">Интервалы:</span>
          <Tooltip>
            <TooltipTrigger asChild>
              <div className="flex items-center">
                <Input type="number" min={0} max={200} className="w-20 h-7 text-xs" placeholder="Перед" value={parseUnit(blockAttrs.spaceBefore) ?? ''} onChange={(e) => { const v = e.target.value ? parseFloat(e.target.value) : null; applySpacing('spaceBefore', v, 'pt') }} disabled={!currentBlockType} />
                <span className="ml-1 text-xs text-muted-foreground">пт</span>
              </div>
            </TooltipTrigger>
            <TooltipContent>Интервал перед абзацем (пт)</TooltipContent>
          </Tooltip>
          <Tooltip>
            <TooltipTrigger asChild>
              <div className="flex items-center">
                <Input type="number" min={0} max={200} className="w-20 h-7 text-xs" placeholder="После" value={parseUnit(blockAttrs.spaceAfter) ?? ''} onChange={(e) => { const v = e.target.value ? parseFloat(e.target.value) : null; applySpacing('spaceAfter', v, 'pt') }} disabled={!currentBlockType} />
                <span className="ml-1 text-xs text-muted-foreground">пт</span>
              </div>
            </TooltipTrigger>
            <TooltipContent>Интервал после абзаца (пт)</TooltipContent>
          </Tooltip>
          <div className="sep" />
          <span className="text-xs text-muted-foreground">Отступы:</span>
          <Tooltip>
            <TooltipTrigger asChild>
              <div className="flex items-center">
                <Input type="number" min={0} max={200} className="w-20 h-7 text-xs" placeholder="Слева" value={parseUnit(blockAttrs.marginLeft) ?? ''} onChange={(e) => { const v = e.target.value ? parseFloat(e.target.value) : null; applySpacing('marginLeft', v, 'mm') }} disabled={!currentBlockType} />
                <span className="ml-1 text-xs text-muted-foreground">мм</span>
              </div>
            </TooltipTrigger>
            <TooltipContent>Отступ слева (мм)</TooltipContent>
          </Tooltip>
          <Tooltip>
            <TooltipTrigger asChild>
              <div className="flex items-center">
                <Input type="number" min={0} max={200} className="w-20 h-7 text-xs" placeholder="Справа" value={parseUnit(blockAttrs.marginRight) ?? ''} onChange={(e) => { const v = e.target.value ? parseFloat(e.target.value) : null; applySpacing('marginRight', v, 'mm') }} disabled={!currentBlockType} />
                <span className="ml-1 text-xs text-muted-foreground">мм</span>
              </div>
            </TooltipTrigger>
            <TooltipContent>Отступ справа (мм)</TooltipContent>
          </Tooltip>
          <div className="sep" />
          <span className="text-xs text-muted-foreground">Первая строка:</span>
          <Tooltip>
            <TooltipTrigger asChild>
              <div className="flex items-center">
                <Input type="number" min={0} max={100} className="w-20 h-7 text-xs" placeholder="1-я стр." value={parseUnit(blockAttrs.firstLineIndent) ?? ''} onChange={(e) => { const v = e.target.value ? parseFloat(e.target.value) : null; applySpacing('firstLineIndent', v, 'mm') }} disabled={!currentBlockType} />
                <span className="ml-1 text-xs text-muted-foreground">мм</span>
              </div>
            </TooltipTrigger>
            <TooltipContent>Отступ первой строки (мм)</TooltipContent>
          </Tooltip>
        </div>
      )}
    </div>
  )
}
