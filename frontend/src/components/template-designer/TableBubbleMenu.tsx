import { useState } from 'react'
import { useEditorState } from '@tiptap/react'
import type { Editor } from '@tiptap/react'
import {
  Button,
  ColorPicker,
  Divider,
  InputNumber,
  Select,
  Space,
  Tooltip,
} from 'antd'
import type { Color } from 'antd/es/color-picker'
import {
  DeleteColumnOutlined,
  DeleteOutlined,
  DeleteRowOutlined,
  InsertRowAboveOutlined,
  InsertRowBelowOutlined,
  InsertRowLeftOutlined,
  InsertRowRightOutlined,
  MergeCellsOutlined,
  SplitCellsOutlined,
  VerticalAlignBottomOutlined,
  VerticalAlignMiddleOutlined,
  VerticalAlignTopOutlined,
} from '@ant-design/icons'

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
  const isInTable = useEditorState({
    editor,
    selector: ({ editor: e }) => e.isActive('table'),
  })

  const [borderStyle, setBorderStyle] = useState<BorderStyle>('solid')
  const [borderWidth, setBorderWidth] = useState<number>(1)
  const [borderColor, setBorderColor] = useState<string>('#000000')

  if (!isInTable) return null

  const applyToSide = (side: BorderSide) =>
    setCellBorders(editor, [side], buildBorderValue(borderStyle, borderWidth, borderColor))

  return (
    <div style={{ borderBottom: '1px solid #bae0ff', background: '#e6f4ff' }}>

      {/* ── Строка 1: структурные операции ───────────────────────────── */}
      <div style={ROW_STYLE}>

        {/* Строки */}
        <Space size={2}>
          <Tooltip title="Строка выше">
            <Button size="small" icon={<InsertRowAboveOutlined />}
              onClick={() => editor.chain().focus().addRowBefore().run()} />
          </Tooltip>
          <Tooltip title="Строка ниже">
            <Button size="small" icon={<InsertRowBelowOutlined />}
              onClick={() => editor.chain().focus().addRowAfter().run()} />
          </Tooltip>
          <Tooltip title="Удалить строку">
            <Button size="small" icon={<DeleteRowOutlined />} danger
              onClick={() => editor.chain().focus().deleteRow().run()} />
          </Tooltip>
        </Space>

        <Divider type="vertical" style={{ margin: '0 4px' }} />

        {/* Колонки */}
        <Space size={2}>
          <Tooltip title="Колонка слева">
            <Button size="small" icon={<InsertRowLeftOutlined />}
              onClick={() => editor.chain().focus().addColumnBefore().run()} />
          </Tooltip>
          <Tooltip title="Колонка справа">
            <Button size="small" icon={<InsertRowRightOutlined />}
              onClick={() => editor.chain().focus().addColumnAfter().run()} />
          </Tooltip>
          <Tooltip title="Удалить колонку">
            <Button size="small" icon={<DeleteColumnOutlined />} danger
              onClick={() => editor.chain().focus().deleteColumn().run()} />
          </Tooltip>
        </Space>

        <Divider type="vertical" style={{ margin: '0 4px' }} />

        {/* Слияние */}
        <Space size={2}>
          <Tooltip title="Объединить ячейки">
            <Button size="small" icon={<MergeCellsOutlined />}
              onClick={() => editor.chain().focus().mergeCells().run()}
              disabled={!editor.can().mergeCells()} />
          </Tooltip>
          <Tooltip title="Разбить ячейку">
            <Button size="small" icon={<SplitCellsOutlined />}
              onClick={() => editor.chain().focus().splitCell().run()}
              disabled={!editor.can().splitCell()} />
          </Tooltip>
        </Space>

        <Divider type="vertical" style={{ margin: '0 4px' }} />

        {/* Вертикальное выравнивание */}
        <Space size={2}>
          <span style={{ fontSize: 11, color: '#595959' }}>Верт.:</span>
          <Tooltip title="Сверху">
            <Button size="small" icon={<VerticalAlignTopOutlined />}
              onClick={() => editor.chain().focus().setCellAttribute('verticalAlign', 'top').run()} />
          </Tooltip>
          <Tooltip title="По центру">
            <Button size="small" icon={<VerticalAlignMiddleOutlined />}
              onClick={() => editor.chain().focus().setCellAttribute('verticalAlign', 'middle').run()} />
          </Tooltip>
          <Tooltip title="Снизу">
            <Button size="small" icon={<VerticalAlignBottomOutlined />}
              onClick={() => editor.chain().focus().setCellAttribute('verticalAlign', 'bottom').run()} />
          </Tooltip>
        </Space>

        <Divider type="vertical" style={{ margin: '0 4px' }} />

        {/* Размер ячейки */}
        <Space size={4} align="center">
          <span style={{ fontSize: 11, color: '#595959' }}>Шир.:</span>
          <InputNumber
            size="small"
            min={20}
            max={800}
            style={{ width: 70 }}
            addonAfter="px"
            placeholder="Авто"
            onChange={v => {
              if (v) editor.chain().focus().setCellAttribute('colwidth', [v]).run()
              else editor.chain().focus().setCellAttribute('colwidth', null).run()
            }}
          />
          <span style={{ fontSize: 11, color: '#595959' }}>Выс.:</span>
          <InputNumber
            size="small"
            min={20}
            max={500}
            style={{ width: 70 }}
            addonAfter="px"
            placeholder="Авто"
            onChange={v => {
              editor.chain().focus().setCellAttribute('minHeight', v ?? null).run()
            }}
          />
        </Space>

        <Divider type="vertical" style={{ margin: '0 4px' }} />

        <Tooltip title="Удалить таблицу">
          <Button size="small" icon={<DeleteOutlined />} danger
            onClick={() => editor.chain().focus().deleteTable().run()} />
        </Tooltip>
      </div>

      {/* ── Строка 2: оформление ячейки ──────────────────────────────── */}
      <div style={{ ...ROW_STYLE, borderTop: '1px solid #bae0ff', background: '#f0f9ff' }}>

        {/* Фон ячейки */}
        <Space size={4} align="center">
          <span style={{ fontSize: 11, color: '#595959' }}>Фон:</span>
          <Tooltip title="Цвет фона ячейки">
            <ColorPicker
              size="small"
              defaultValue="#ffffff"
              onChangeComplete={(c: Color) =>
                editor.chain().focus().setCellAttribute('backgroundColor', c.toHexString()).run()
              }
            />
          </Tooltip>
          <Tooltip title="Сбросить фон">
            <Button size="small" style={{ fontSize: 11 }}
              onClick={() => editor.chain().focus().setCellAttribute('backgroundColor', null).run()}>
              ✕
            </Button>
          </Tooltip>
        </Space>

        <Divider type="vertical" style={{ margin: '0 4px' }} />

        {/* Ориентация текста */}
        <Space size={2}>
          <span style={{ fontSize: 11, color: '#595959' }}>Текст:</span>
          <Tooltip title="Горизонтально (обычный)">
            <Button size="small" style={{ fontSize: 11 }}
              onClick={() => editor.chain().focus().setCellAttribute('textOrientation', null).run()}>
              0°
            </Button>
          </Tooltip>
          <Tooltip title="Вертикально по часовой (90°)">
            <Button size="small" style={{ fontSize: 11 }}
              onClick={() => editor.chain().focus().setCellAttribute('textOrientation', 'rotate-90').run()}>
              90°
            </Button>
          </Tooltip>
          <Tooltip title="Перевёрнуто (180°)">
            <Button size="small" style={{ fontSize: 11 }}
              onClick={() => editor.chain().focus().setCellAttribute('textOrientation', 'rotate-180').run()}>
              180°
            </Button>
          </Tooltip>
          <Tooltip title="Вертикально против часовой (270°)">
            <Button size="small" style={{ fontSize: 11 }}
              onClick={() => editor.chain().focus().setCellAttribute('textOrientation', 'rotate-270').run()}>
              270°
            </Button>
          </Tooltip>
        </Space>

        <Divider type="vertical" style={{ margin: '0 4px' }} />

        {/* Границы — мгновенное применение ────────────────────────────
            Выбери стиль, ширину, цвет → нажми нужную сторону или «Все».
            Изменения применяются сразу, кнопка «Применить» не нужна.     */}
        <Space size={4} align="center" wrap>
          <span style={{ fontSize: 11, color: '#595959' }}>Граница:</span>
          <Select
            size="small"
            style={{ width: 82 }}
            value={borderStyle}
            onChange={setBorderStyle}
            options={[
              { value: 'solid', label: 'Сплошная' },
              { value: 'dashed', label: 'Пунктир' },
              { value: 'dotted', label: 'Точки' },
            ]}
          />
          <InputNumber
            size="small"
            min={1}
            max={4}
            value={borderWidth}
            onChange={v => setBorderWidth(v ?? 1)}
            style={{ width: 52 }}
            addonAfter="px"
          />
          <Tooltip title="Цвет границы">
            <ColorPicker
              size="small"
              value={borderColor}
              onChange={(c: Color) => setBorderColor(c.toHexString())}
            />
          </Tooltip>
          <span style={{ fontSize: 11, color: '#8c8c8c' }}>→</span>
          {BORDER_SIDES.map(side => (
            <Tooltip key={side} title={`Применить к: ${SIDE_LABELS[side]}`}>
              <Button
                size="small"
                style={{ fontSize: 10, minWidth: 28 }}
                onClick={() => applyToSide(side)}
              >
                {SIDE_SHORT[side]}
              </Button>
            </Tooltip>
          ))}
          <Tooltip title="Применить ко всем сторонам">
            <Button
              size="small"
              type="primary"
              ghost
              style={{ fontSize: 10 }}
              onClick={() => setCellBorders(editor, 'all', buildBorderValue(borderStyle, borderWidth, borderColor))}
            >
              Все
            </Button>
          </Tooltip>
          <Tooltip title="Убрать все границы">
            <Button
              size="small"
              danger
              style={{ fontSize: 10 }}
              onClick={() => setCellBorders(editor, 'all', null)}
            >
              Нет
            </Button>
          </Tooltip>
        </Space>
      </div>
    </div>
  )
}
