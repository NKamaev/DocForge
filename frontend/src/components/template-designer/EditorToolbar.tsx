import { useEffect, useReducer, useState } from 'react'
import { Button, ColorPicker, Divider, Dropdown, InputNumber, Select, Space, Tooltip } from 'antd'
import type { MenuProps } from 'antd'
import type { Editor } from '@tiptap/react'
import {
  AlignCenterOutlined,
  AlignLeftOutlined,
  AlignRightOutlined,
  BarsOutlined,
  BoldOutlined,
  ItalicOutlined,
  LineOutlined,
  LinkOutlined,
  PictureOutlined,
  OrderedListOutlined,
  StrikethroughOutlined,
  TableOutlined,
  UnderlineOutlined,
  UnorderedListOutlined,
} from '@ant-design/icons'
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
    <Tooltip title={title}>
      <Button
        size="small"
        type={active ? 'primary' : 'default'}
        onClick={onClick}
        disabled={disabled}
        style={{ minWidth: 32 }}
      >
        {children}
      </Button>
    </Tooltip>
  )
}

// Fallback parameters shown when the template has none defined yet
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

  // Rerender on every selection change / transaction so all derived values stay current
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

  const visibleParams = (parameters && parameters.length > 0) ? parameters : PLACEHOLDER_PARAMS

  // Expand DIRECTORY parameters into sub-field entries
  const paramMenuItems: MenuProps['items'] = visibleParams.flatMap((p) => {
    if (p.type === 'DIRECTORY' && p.directoryFields && p.directoryFields.length > 0) {
      return p.directoryFields.map((fieldKey) => ({
        key: `${p.code}__${fieldKey}`,
        label: (
          <span>
            <span style={{ fontWeight: 500 }}>{p.label} → {fieldKey}</span>
            <span style={{ color: '#999', fontSize: 12, marginLeft: 8 }}>{p.code}__{fieldKey}</span>
          </span>
        ),
        onClick: () => {
          editor.chain().focus().insertParameterField({
            parameterCode: `${p.code}__${fieldKey}`,
            display: `${p.label} → ${fieldKey}`,
          }).run()
        },
      }))
    }
    return [{
      key: p.code,
      label: (
        <span>
          <span style={{ fontWeight: 500 }}>{p.label}</span>
          <span style={{ color: '#999', fontSize: 12, marginLeft: 8 }}>{p.code}</span>
        </span>
      ),
      onClick: () => {
        editor.chain().focus().insertParameterField({ parameterCode: p.code, display: p.label }).run()
      },
    }]
  })

  // Build clause menu: insert levels + style change + listStart toggle
  const isListStart = editor.isActive('numberedClause', { listStart: true })
  const clauseMenuItems: MenuProps['items'] = [
    {
      key: 'insert-header',
      type: 'group',
      label: 'Вставить пункт',
      children: ([1, 2, 3, 4, 5] as const).map((level) => ({
        key: `insert-${level}`,
        label: LEVEL_LABELS[level],
        onClick: () => editor.chain().focus().insertNumberedClause(level).run(),
      })),
    },
    { type: 'divider' },
    {
      key: 'style-header',
      type: 'group',
      label: 'Стиль нумерации (текущий пункт)',
      children: (['decimal', 'alpha-ru', 'alpha-en'] as NumberingStyle[]).map((style) => ({
        key: `style-${style}`,
        label: STYLE_LABELS[style],
        disabled: !editor.isActive('numberedClause'),
        onClick: () => editor.chain().focus().setClauseNumberingStyle(style).run(),
      })),
    },
    { type: 'divider' },
    {
      key: 'list-start',
      label: (
        <span>
          {isListStart ? '✓ ' : ''}Начать новый список
        </span>
      ),
      disabled: !editor.isActive('numberedClause'),
      onClick: () => editor.chain().focus().toggleClauseListStart().run(),
    },
  ]

  // Build clause-ref dropdown from current plugin state
  const buildClauseRefItems = (): MenuProps['items'] => {
    const state = autoNumberingPluginKey.getState(editor.view.state)
    if (!state?.byId.size) {
      return [{ key: 'no-clauses', label: 'Нет нумерованных пунктов', disabled: true }]
    }

    const items: MenuProps['items'] = []
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
        key: id,
        label: (
          <span>
            <span style={{ fontWeight: 600, color: '#0958D9', marginRight: 8 }}>{label}</span>
            <span style={{ color: '#595959' }}>{preview}</span>
          </span>
        ),
        onClick: () => editor.chain().focus().insertClauseRef(id).run(),
      })
    })

    return items.length ? items : [{ key: 'no-clauses', label: 'Нет нумерованных пунктов', disabled: true }]
  }

  // Get current font family value for the Select; fall back to document default
  const currentFontFamily =
    (editor.getAttributes('textStyle').fontFamily as string | undefined) ??
    formatting?.defaultFont

  // Get current font size value for the Select; fall back to document default
  const currentFontSize = (() => {
    const fs = editor.getAttributes('textStyle').fontSize as string | undefined
    if (fs) return fs.replace('pt', '')
    return formatting?.defaultFontSize != null ? String(formatting.defaultFontSize) : undefined
  })()

  // Get current line spacing for the Select; fall back to document default
  const currentLineSpacing = (() => {
    for (const type of ['paragraph', 'heading', 'numberedClause']) {
      const attrs = editor.getAttributes(type)
      if (attrs.lineSpacing) return attrs.lineSpacing as string
    }
    return formatting?.lineSpacing != null ? String(formatting.lineSpacing) : undefined
  })()

  // Current block type + paragraph spacing attributes.
  // numberedClause must be checked BEFORE paragraph — when the cursor is inside
  // a clause, editor.isActive('paragraph') is also true (inner <p>), but spacing
  // attrs live on the numberedClause node, not on its children.
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

  // Insert/update link
  const insertLink = (url: string) => {
    if (!url) {
      editor.chain().focus().unsetLink().run()
      return
    }
    const href = url.startsWith('http') ? url : `https://${url}`
    editor.chain().focus().setLink({ href, target: '_blank' }).run()
  }

  const linkMenuItems: MenuProps['items'] = [
    {
      key: 'link-input',
      label: (
        <div style={{ display: 'flex', gap: 8, padding: '4px 0' }} onClick={(e) => e.stopPropagation()}>
          <input
            value={linkUrl}
            onChange={(e) => setLinkUrl(e.target.value)}
            placeholder="https://..."
            style={{ flex: 1, border: '1px solid #d9d9d9', borderRadius: 4, padding: '2px 8px', outline: 'none' }}
            onKeyDown={(e) => {
              if (e.key === 'Enter') {
                insertLink(linkUrl)
                setLinkUrl('')
              }
            }}
          />
          <Button size="small" type="primary" onClick={() => { insertLink(linkUrl); setLinkUrl('') }}>
            Вставить
          </Button>
        </div>
      ),
    },
    ...(editor.isActive('link') ? [{
      key: 'remove-link',
      label: 'Удалить ссылку',
      danger: true,
      onClick: () => editor.chain().focus().unsetLink().run(),
    }] : []),
  ]

  return (
    <div style={{ borderBottom: '1px solid #d9d9d9', background: '#fafafa' }}>
      {/* ── Main toolbar row ──────────────────────────────────── */}
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          flexWrap: 'wrap',
          gap: 4,
          padding: '6px 12px',
        }}
      >
      {/* ── Fonts ─────────────────────────────────────────────── */}
      <Space size={4}>
        <Select
          size="small"
          style={{ width: 160 }}
          placeholder="Шрифт"
          value={currentFontFamily}
          onChange={(v) => editor.chain().focus().setFontFamily(v).run()}
          options={FONT_FAMILIES.map((f) => ({ value: f.value, label: f.label }))}
          allowClear
          onClear={() => editor.chain().focus().unsetFontFamily().run()}
        />
        <Select
          size="small"
          style={{ width: 68 }}
          placeholder="Размер"
          value={currentFontSize}
          onChange={(v) => editor.chain().focus().setFontSize(`${v}pt`).run()}
          options={FONT_SIZES.map((s) => ({ value: s, label: s }))}
          allowClear
          onClear={() => editor.chain().focus().unsetFontSize().run()}
        />
        <Select
          size="small"
          style={{ width: 72 }}
          placeholder="Интерл."
          value={currentLineSpacing}
          onChange={(v) => editor.chain().focus().setLineSpacing(v).run()}
          options={LINE_SPACINGS.map((s) => ({ value: s.value, label: s.label }))}
        />
      </Space>

      <Divider type="vertical" style={{ margin: '0 4px' }} />

      {/* ── Paragraph panel toggle ────────────────────────────── */}
      <Tooltip title="Настройки абзаца">
        <Button
          size="small"
          icon={<BarsOutlined />}
          type={paragraphPanelOpen ? 'primary' : 'default'}
          onClick={() => setParagraphPanelOpen((v) => !v)}
        >
          Абзац
        </Button>
      </Tooltip>

      <Divider type="vertical" style={{ margin: '0 4px' }} />

      {/* ── Basic formatting ──────────────────────────────────── */}
      <Space size={4}>
        <ToolBtn
          title="Жирный (Ctrl+B)"
          active={editor.isActive('bold')}
          onClick={() => editor.chain().focus().toggleBold().run()}
        >
          <BoldOutlined />
        </ToolBtn>

        <ToolBtn
          title="Курсив (Ctrl+I)"
          active={editor.isActive('italic')}
          onClick={() => editor.chain().focus().toggleItalic().run()}
        >
          <ItalicOutlined />
        </ToolBtn>

        <ToolBtn
          title="Подчёркнутый (Ctrl+U)"
          active={editor.isActive('underline')}
          onClick={() => editor.chain().focus().toggleUnderline().run()}
        >
          <UnderlineOutlined />
        </ToolBtn>

        <ToolBtn
          title="Зачёркнутый"
          active={editor.isActive('strike')}
          onClick={() => editor.chain().focus().toggleStrike().run()}
        >
          <StrikethroughOutlined />
        </ToolBtn>

        <ToolBtn
          title="Надстрочный"
          active={editor.isActive('superscript')}
          onClick={() => editor.chain().focus().toggleSuperscript().run()}
        >
          x²
        </ToolBtn>

        <ToolBtn
          title="Подстрочный"
          active={editor.isActive('subscript')}
          onClick={() => editor.chain().focus().toggleSubscript().run()}
        >
          x₂
        </ToolBtn>
      </Space>

      <Divider type="vertical" style={{ margin: '0 4px' }} />

      {/* ── Color pickers ────────────────────────────────────── */}
      <Space size={4}>
        <Tooltip title="Цвет текста">
          <ColorPicker
            size="small"
            value={editor.getAttributes('textStyle').color as string | undefined}
            onChange={(color) => editor.chain().focus().setColor(color.toHexString()).run()}
            onClear={() => editor.chain().focus().unsetColor().run()}
            allowClear
          />
        </Tooltip>
        <Tooltip title="Цвет фона">
          <ColorPicker
            size="small"
            value={editor.getAttributes('highlight').color as string | undefined}
            onChange={(color) =>
              editor.chain().focus().setHighlight({ color: color.toHexString() }).run()
            }
            onClear={() => editor.chain().focus().unsetHighlight().run()}
            allowClear
          />
        </Tooltip>
      </Space>

      <Divider type="vertical" style={{ margin: '0 4px' }} />

      {/* ── Headings ─────────────────────────────────────────── */}
      <Space size={4}>
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
      </Space>

      <Divider type="vertical" style={{ margin: '0 4px' }} />

      {/* ── Alignment ────────────────────────────────────────── */}
      <Space size={4}>
        <ToolBtn
          title="По левому краю"
          active={editor.isActive({ textAlign: 'left' })}
          onClick={() => editor.chain().focus().setTextAlign('left').run()}
        >
          <AlignLeftOutlined />
        </ToolBtn>
        <ToolBtn
          title="По центру"
          active={editor.isActive({ textAlign: 'center' })}
          onClick={() => editor.chain().focus().setTextAlign('center').run()}
        >
          <AlignCenterOutlined />
        </ToolBtn>
        <ToolBtn
          title="По правому краю"
          active={editor.isActive({ textAlign: 'right' })}
          onClick={() => editor.chain().focus().setTextAlign('right').run()}
        >
          <AlignRightOutlined />
        </ToolBtn>
        <ToolBtn
          title="По ширине"
          active={editor.isActive({ textAlign: 'justify' })}
          onClick={() => editor.chain().focus().setTextAlign('justify').run()}
        >
          ≡
        </ToolBtn>
      </Space>

      <Divider type="vertical" style={{ margin: '0 4px' }} />

      {/* ── Indent ───────────────────────────────────────────── */}
      <Space size={4}>
        <ToolBtn
          title="Увеличить отступ (Tab)"
          onClick={() => editor.chain().focus().indent().run()}
        >
          <Icon name="indent-r" size={14}/>
        </ToolBtn>
        <ToolBtn
          title="Уменьшить отступ (Shift+Tab)"
          onClick={() => editor.chain().focus().outdent().run()}
        >
          <Icon name="indent-l" size={14}/>
        </ToolBtn>
      </Space>

      <Divider type="vertical" style={{ margin: '0 4px' }} />

      {/* ── Lists ────────────────────────────────────────────── */}
      <Space size={4}>
        <ToolBtn
          title="Маркированный список"
          active={editor.isActive('bulletList')}
          onClick={() => editor.chain().focus().toggleBulletList().run()}
        >
          <UnorderedListOutlined />
        </ToolBtn>

        <ToolBtn
          title="Нумерованный список"
          active={editor.isActive('orderedList')}
          onClick={() => editor.chain().focus().toggleOrderedList().run()}
        >
          <OrderedListOutlined />
        </ToolBtn>
      </Space>

      <Divider type="vertical" style={{ margin: '0 4px' }} />

      {/* ── Insert ───────────────────────────────────────────── */}
      <Space size={4}>
        <ToolBtn title="Вставить таблицу" onClick={insertTable}>
          <TableOutlined />
        </ToolBtn>

        <ToolBtn title="Горизонтальная линия" onClick={insertHorizontalRule}>
          <LineOutlined />
        </ToolBtn>

        <ToolBtn title="Разрыв страницы (Ctrl+Enter)" onClick={() => editor.chain().focus().insertPageBreak().run()}>
          <Icon name="page-break" size={14}/>
        </ToolBtn>

        <ToolBtn title="Вставить изображение" onClick={() => onImageClick?.()}>
          <PictureOutlined />
        </ToolBtn>

        <Dropdown
          menu={{ items: linkMenuItems }}
          trigger={['click']}
          placement="bottomLeft"
        >
          <Button
            size="small"
            icon={<LinkOutlined />}
            type={editor.isActive('link') ? 'primary' : 'default'}
            style={{ minWidth: 32 }}
          />
        </Dropdown>
      </Space>

      <Divider type="vertical" style={{ margin: '0 4px' }} />

      {/* ── Template-specific ────────────────────────────────── */}
      <div style={{ background: '#f0f5ff', borderRadius: 4, padding: '2px 6px', display: 'flex', alignItems: 'center' }}>
        <Space size={4}>
          <Dropdown menu={{ items: paramMenuItems }} trigger={['click']} placement="bottomLeft">
            <Button size="small" icon={<Icon name="param" size={14}/>} type="dashed">
              Параметр
            </Button>
          </Dropdown>

          <Dropdown
            menu={{ items: clauseMenuItems }}
            trigger={['click']}
            placement="bottomLeft"
          >
            <Button
              size="small"
              icon={<Icon name="hash" size={14}/>}
              type="dashed"
              style={
                editor.isActive('numberedClause')
                  ? { borderColor: '#91CAFF', color: '#0958D9' }
                  : undefined
              }
            >
              Пункт
            </Button>
          </Dropdown>

          <Tooltip title={editor.state.selection.empty ? 'Выделите текст для обёртки в условие' : 'Обернуть выделение в условный текст'}>
            <Button
              size="small"
              icon={<Icon name="branch" size={14}/>}
              type="dashed"
              style={{ borderColor: '#FFD591', color: '#D46B08' }}
              disabled={editor.state.selection.empty}
              onClick={() =>
                editor
                  .chain()
                  .focus()
                  .wrapInConditionalInline({ label: 'Условный текст', condition: { type: 'simple', expression: '' } })
                  .run()
              }
            >
              Условный текст
            </Button>
          </Tooltip>

          <Dropdown
            menu={{ items: buildClauseRefItems() }}
            trigger={['click']}
            placement="bottomLeft"
          >
            <Button
              size="small"
              icon={<Icon name="link" size={14}/>}
              type="dashed"
              style={{ borderColor: '#91CAFF', color: '#0958D9' }}
            >
              Ссылка на пункт
            </Button>
          </Dropdown>

          <Dropdown
            menu={{
              items: phrasings.length === 0
                ? [{ key: 'empty', label: 'Нет формулировок (создайте в панели «Формулировки»)', disabled: true }]
                : phrasings.map(p => ({
                    key: p.id,
                    label: (
                      <span>
                        <span style={{ fontWeight: 500 }}>📝 {p.name}</span>
                        <span style={{ color: '#999', fontSize: 11, marginLeft: 8 }}>
                          {p.variants.length} вар.
                        </span>
                      </span>
                    ),
                    onClick: () => editor.chain().focus().insertPhrasingsRef({ phrasingsIds: [p.id], displayNames: [p.name], separator: ', ' }).run(),
                  })),
            }}
            trigger={['click']}
            placement="bottomLeft"
          >
            <Button
              size="small"
              type="dashed"
              style={{ borderColor: '#B7EB8F', color: '#389E0D' }}
            >
              📝 Формулировки
            </Button>
          </Dropdown>
        </Space>
      </div>
      </div>

      {/* ── Paragraph spacing panel (collapsible) ─────────────── */}
      {paragraphPanelOpen && (
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            flexWrap: 'wrap',
            gap: 8,
            padding: '6px 12px',
            borderTop: '1px solid #f0f0f0',
          }}
        >
          <span style={{ fontSize: 12, color: '#888' }}>Интервалы:</span>
          <Tooltip title="Интервал перед абзацем (пт)">
            <InputNumber
              size="small"
              min={0}
              max={200}
              style={{ width: 90 }}
              placeholder="Перед"
              addonAfter="пт"
              value={parseUnit(blockAttrs.spaceBefore)}
              onChange={(v) => applySpacing('spaceBefore', v, 'pt')}
              disabled={!currentBlockType}
            />
          </Tooltip>
          <Tooltip title="Интервал после абзаца (пт)">
            <InputNumber
              size="small"
              min={0}
              max={200}
              style={{ width: 90 }}
              placeholder="После"
              addonAfter="пт"
              value={parseUnit(blockAttrs.spaceAfter)}
              onChange={(v) => applySpacing('spaceAfter', v, 'pt')}
              disabled={!currentBlockType}
            />
          </Tooltip>

          <Divider type="vertical" />

          <span style={{ fontSize: 12, color: '#888' }}>Отступы:</span>
          <Tooltip title="Отступ слева (мм)">
            <InputNumber
              size="small"
              min={0}
              max={200}
              style={{ width: 90 }}
              placeholder="Слева"
              addonAfter="мм"
              value={parseUnit(blockAttrs.marginLeft)}
              onChange={(v) => applySpacing('marginLeft', v, 'mm')}
              disabled={!currentBlockType}
            />
          </Tooltip>
          <Tooltip title="Отступ справа (мм)">
            <InputNumber
              size="small"
              min={0}
              max={200}
              style={{ width: 90 }}
              placeholder="Справа"
              addonAfter="мм"
              value={parseUnit(blockAttrs.marginRight)}
              onChange={(v) => applySpacing('marginRight', v, 'mm')}
              disabled={!currentBlockType}
            />
          </Tooltip>

          <Divider type="vertical" />

          <span style={{ fontSize: 12, color: '#888' }}>Первая строка:</span>
          <Tooltip title="Отступ первой строки (мм)">
            <InputNumber
              size="small"
              min={0}
              max={100}
              style={{ width: 90 }}
              placeholder="1-я стр."
              addonAfter="мм"
              value={parseUnit(blockAttrs.firstLineIndent)}
              onChange={(v) => applySpacing('firstLineIndent', v, 'mm')}
              disabled={!currentBlockType}
            />
          </Tooltip>
        </div>
      )}
    </div>
  )
}
