import { forwardRef, useEffect, useReducer, useState, type CSSProperties } from 'react'
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
  AlignJustify,
  AlignLeft,
  AlignRight,
  Bold,
  FileText,
  Image,
  Italic,
  Link,
  List,
  ListOrdered,
  Menu,
  Minus,
  Strikethrough,
  Table,
  Underline,
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
  'data-testid': testId,
}: {
  title: string
  active?: boolean
  onClick: () => void
  disabled?: boolean
  children: React.ReactNode
  'data-testid'?: string
}) {
  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <button
          className={'tb-btn' + (active ? ' active' : '')}
          onClick={onClick}
          disabled={disabled}
          data-testid={testId}
        >
          {children}
        </button>
      </TooltipTrigger>
      <TooltipContent>{title}</TooltipContent>
    </Tooltip>
  )
}

const TbSelect = forwardRef<HTMLDivElement, {
  value?: string
  onChange: (v: string) => void
  options: { value: string; label: string }[]
  style?: CSSProperties
  placeholder?: string
  'data-testid'?: string
}>(({
  value,
  onChange,
  options,
  style,
  placeholder,
  'data-testid': testId,
}, ref) => {
  return (
    <div ref={ref} className="tb-select-wrap" style={style} data-testid={testId}>
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
})

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
        <Tooltip>
          <TooltipTrigger asChild>
            <TbSelect
              value={currentFontFamily}
              onChange={(v) => editor.chain().focus().setFontFamily(v).run()}
              options={FONT_FAMILIES}
              style={{ width: 145 }}
              data-testid="toolbar-font-family"
            />
          </TooltipTrigger>
          <TooltipContent>Шрифт</TooltipContent>
        </Tooltip>
        <Tooltip>
          <TooltipTrigger asChild>
            <TbSelect
              value={currentFontSize}
              onChange={(v) => editor.chain().focus().setFontSize(`${v}pt`).run()}
              options={FONT_SIZES.map((s) => ({ value: s, label: s }))}
              style={{ width: 60 }}
              placeholder="Размер"
              data-testid="toolbar-font-size"
            />
          </TooltipTrigger>
          <TooltipContent>Размер шрифта</TooltipContent>
        </Tooltip>
        <Tooltip>
          <TooltipTrigger asChild>
            <TbSelect
              value={currentLineSpacing}
              onChange={(v) => editor.chain().focus().setLineSpacing(v).run()}
              options={LINE_SPACINGS}
              style={{ width: 60 }}
              data-testid="toolbar-line-spacing"
            />
          </TooltipTrigger>
          <TooltipContent>Межстрочный интервал</TooltipContent>
        </Tooltip>
      </div>

      <div className="sep" />

      {/* Paragraph panel toggle */}
      <Tooltip>
        <TooltipTrigger asChild>
          <button
            className={'tb-btn-lbl' + (paragraphPanelOpen ? ' active' : '')}
            onClick={() => setParagraphPanelOpen((v) => !v)}
            data-testid="toolbar-paragraph-panel"
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
        <ToolBtn title="Жирный (Ctrl+B)" active={editor.isActive('bold')} onClick={() => editor.chain().focus().toggleBold().run()} data-testid="toolbar-bold">
          <Bold size={14} />
        </ToolBtn>
        <ToolBtn title="Курсив (Ctrl+I)" active={editor.isActive('italic')} onClick={() => editor.chain().focus().toggleItalic().run()} data-testid="toolbar-italic">
          <Italic size={14} />
        </ToolBtn>
        <ToolBtn title="Подчёркнутый (Ctrl+U)" active={editor.isActive('underline')} onClick={() => editor.chain().focus().toggleUnderline().run()} data-testid="toolbar-underline">
          <Underline size={14} />
        </ToolBtn>
        <ToolBtn title="Зачёркнутый" active={editor.isActive('strike')} onClick={() => editor.chain().focus().toggleStrike().run()} data-testid="toolbar-strike">
          <Strikethrough size={14} />
        </ToolBtn>
        <ToolBtn title="Надстрочный" active={editor.isActive('superscript')} onClick={() => editor.chain().focus().toggleSuperscript().run()} data-testid="toolbar-superscript">
          x²
        </ToolBtn>
        <ToolBtn title="Подстрочный" active={editor.isActive('subscript')} onClick={() => editor.chain().focus().toggleSubscript().run()} data-testid="toolbar-subscript">
          x₂
        </ToolBtn>
      </div>

      <div className="sep" />

      {/* Color pickers */}
      <div className="flex items-center gap-[1px]">
        <ColorPicker
          value={editor.getAttributes('textStyle').color as string | undefined}
          onChange={(color) => editor.chain().focus().setColor(color).run()}
          onClear={() => editor.chain().focus().unsetColor().run()}
          allowClear
          data-testid="toolbar-color-text"
        />
        <ColorPicker
          value={editor.getAttributes('highlight').color as string | undefined}
          onChange={(color) => editor.chain().focus().setHighlight({ color: color }).run()}
          onClear={() => editor.chain().focus().unsetHighlight().run()}
          allowClear
          data-testid="toolbar-color-bg"
        />
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
            data-testid={`toolbar-h${level}`}
          >
            H{level}
          </ToolBtn>
        ))}
      </div>

      <div className="sep" />

      {/* Alignment */}
      <div className="flex items-center gap-[1px]">
        <ToolBtn title="По левому краю" active={editor.isActive({ textAlign: 'left' })} onClick={() => editor.chain().focus().setTextAlign('left').run()} data-testid="toolbar-align-left">
          <AlignLeft size={14} />
        </ToolBtn>
        <ToolBtn title="По центру" active={editor.isActive({ textAlign: 'center' })} onClick={() => editor.chain().focus().setTextAlign('center').run()} data-testid="toolbar-align-center">
          <AlignCenter size={14} />
        </ToolBtn>
        <ToolBtn title="По правому краю" active={editor.isActive({ textAlign: 'right' })} onClick={() => editor.chain().focus().setTextAlign('right').run()} data-testid="toolbar-align-right">
          <AlignRight size={14} />
        </ToolBtn>
        <ToolBtn title="По ширине" active={editor.isActive({ textAlign: 'justify' })} onClick={() => editor.chain().focus().setTextAlign('justify').run()} data-testid="toolbar-align-justify">
          <AlignJustify size={14} />
        </ToolBtn>
      </div>

      <div className="sep" />

      {/* Indent */}
      <div className="flex items-center gap-[1px]">
        <ToolBtn title="Увеличить отступ (Tab)" onClick={() => editor.chain().focus().indent().run()} data-testid="toolbar-indent">
          <Icon name="indent-r" size={14} />
        </ToolBtn>
        <ToolBtn title="Уменьшить отступ (Shift+Tab)" onClick={() => editor.chain().focus().outdent().run()} data-testid="toolbar-outdent">
          <Icon name="indent-l" size={14} />
        </ToolBtn>
      </div>

      <div className="sep" />

      {/* Lists */}
      <div className="flex items-center gap-[1px]">
        <ToolBtn title="Маркированный список" active={editor.isActive('bulletList')} onClick={() => editor.chain().focus().toggleBulletList().run()} data-testid="toolbar-bullet-list">
          <List size={14} />
        </ToolBtn>
        <ToolBtn title="Нумерованный список" active={editor.isActive('orderedList')} onClick={() => editor.chain().focus().toggleOrderedList().run()} data-testid="toolbar-ordered-list">
          <ListOrdered size={14} />
        </ToolBtn>
      </div>

      <div className="sep" />

      {/* Insert */}
      <div className="flex items-center gap-[1px]">
        <ToolBtn title="Вставить таблицу" onClick={insertTable} data-testid="toolbar-table">
          <Table size={14} />
        </ToolBtn>
        <ToolBtn title="Горизонтальная линия" onClick={insertHorizontalRule} data-testid="toolbar-hr">
          <Minus size={14} />
        </ToolBtn>
        <ToolBtn title="Разрыв страницы (Ctrl+Enter)" onClick={() => editor.chain().focus().insertPageBreak().run()} data-testid="toolbar-page-break">
          <Icon name="page-break" size={14} />
        </ToolBtn>
        <ToolBtn title="Вставить изображение" onClick={() => onImageClick?.()} data-testid="toolbar-image">
          <Image size={14} />
        </ToolBtn>

        {/* Link dropdown */}
        <Tooltip>
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <button className={'tb-btn' + (editor.isActive('link') ? ' active' : '')} data-testid="toolbar-link">
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
          <TooltipContent>Вставить ссылку</TooltipContent>
        </Tooltip>
      </div>

      <div className="sep" />

      {/* Template-specific */}
      <div className="flex items-center gap-[1px]">
        {/* Parameters dropdown with multi-select */}
        <Tooltip>
          <Popover open={paramMultiOpen} onOpenChange={setParamMultiOpen}>
            <PopoverTrigger asChild>
              <button className="tb-btn-lbl" data-testid="toolbar-parameter">
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
        <TooltipContent>Вставить параметр</TooltipContent>
      </Tooltip>

        {/* Clause dropdown */}
        <Tooltip>
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <button className={'tb-btn-lbl' + (editor.isActive('numberedClause') ? ' active' : '')} data-testid="toolbar-clause">
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
          <TooltipContent>Нумерованный пункт</TooltipContent>
        </Tooltip>

        {/* Conditional inline button */}
        <Tooltip>
          <TooltipTrigger asChild>
            <button
              className="tb-btn-lbl"
              disabled={editor.state.selection.empty}
              onClick={() => editor.chain().focus().wrapInConditionalInline({ label: 'Условный текст', condition: { type: 'simple', expression: '' } }).run()}
              data-testid="toolbar-conditional-text"
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
        <Tooltip>
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <button className="tb-btn-lbl" data-testid="toolbar-clause-ref">
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
          <TooltipContent>Ссылка на нумерованный пункт</TooltipContent>
        </Tooltip>

        {/* Phrasings dropdown */}
        <Tooltip>
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <button className="tb-btn-lbl" data-testid="toolbar-phrasings">
                <FileText size={14} />
                Формулировки
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
        <TooltipContent>Вставить формулировку</TooltipContent>
      </Tooltip>
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
