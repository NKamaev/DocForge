import { useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { Button, Space, Tooltip } from 'antd'
import {
  NumberOutlined,
  OrderedListOutlined,
  PlusSquareOutlined,
  TableOutlined,
  UnorderedListOutlined,
} from '@ant-design/icons'
import type { Editor } from '@tiptap/react'
import { FloatingMenuPlugin } from '@tiptap/extension-floating-menu'
import { PluginKey } from '@tiptap/pm/state'
import type { Parameter } from '../../types/template'

const FLOATING_MENU_KEY = new PluginKey('floatingInsertMenu')

interface Props {
  editor: Editor
  parameters?: Parameter[]
}

export default function FloatingInsertMenu({ editor, parameters = [] }: Props) {
  const containerRef = useRef<HTMLDivElement>(document.createElement('div'))
  const [ready, setReady] = useState(false)

  useEffect(() => {
    const element = containerRef.current
    element.style.position = 'absolute'
    element.style.zIndex = '50'
    document.body.appendChild(element)

    const plugin = FloatingMenuPlugin({
      pluginKey: FLOATING_MENU_KEY,
      editor,
      element,
      shouldShow: ({ state, view }) => {
        if (!view.hasFocus()) return false
        const { selection } = state
        const { $anchor, empty } = selection
        if (!empty) return false
        const isRootDepth = $anchor.depth === 1
        const isEmptyTextBlock =
          $anchor.parent.isTextblock &&
          !$anchor.parent.type.spec.code &&
          !$anchor.parent.textContent
        return isRootDepth && isEmptyTextBlock
      },
    })

    editor.registerPlugin(plugin)
    setReady(true)

    return () => {
      editor.unregisterPlugin(FLOATING_MENU_KEY)
      if (document.body.contains(element)) document.body.removeChild(element)
    }
  }, [editor])

  if (!ready) return null

  const firstParam = parameters[0]

  const menu = (
    <Space size={2} style={{ userSelect: 'none' }}>
      <span style={{ fontSize: 11, color: '#bfbfbf', marginRight: 4 }}>Вставить:</span>

      <Tooltip title="Маркированный список">
        <Button
          size="small"
          type="text"
          icon={<UnorderedListOutlined />}
          onMouseDown={(e) => { e.preventDefault(); editor.chain().focus().toggleBulletList().run() }}
        />
      </Tooltip>

      <Tooltip title="Нумерованный список">
        <Button
          size="small"
          type="text"
          icon={<OrderedListOutlined />}
          onMouseDown={(e) => { e.preventDefault(); editor.chain().focus().toggleOrderedList().run() }}
        />
      </Tooltip>

      <Tooltip title="Нумерованный пункт (§)">
        <Button
          size="small"
          type="text"
          icon={<NumberOutlined style={{ color: '#0958D9' }} />}
          onMouseDown={(e) => { e.preventDefault(); editor.chain().focus().insertNumberedClause(1).run() }}
        />
      </Tooltip>

      <Tooltip title="Таблица">
        <Button
          size="small"
          type="text"
          icon={<TableOutlined />}
          onMouseDown={(e) => {
            e.preventDefault()
            editor.chain().focus().insertTable({ rows: 3, cols: 3, withHeaderRow: false }).run()
          }}
        />
      </Tooltip>

      {firstParam && (
        <Tooltip title={`Параметр: ${firstParam.label}`}>
          <Button
            size="small"
            type="text"
            icon={<PlusSquareOutlined />}
            onMouseDown={(e) => {
              e.preventDefault()
              editor.chain().focus().insertParameterField({
                parameterCode: firstParam.code,
                display: firstParam.label,
              }).run()
            }}
          />
        </Tooltip>
      )}

    </Space>
  )

  return createPortal(menu, containerRef.current)
}
