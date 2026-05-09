import { useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { Button } from '@/components/ui/button'
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from '@/components/ui/tooltip'
import { List, ListOrdered, Table, Plus, Hash } from 'lucide-react'
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
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 1,
        background: '#fff',
        border: '1px solid #d9d9d9',
        borderRadius: 6,
        padding: '2px 4px',
        boxShadow: '0 4px 12px rgba(0,0,0,0.15)',
        userSelect: 'none',
      }}
    >
      <span
        style={{
          fontSize: 12.5,
          color: '#999',
          marginRight: 2,
          marginLeft: 2,
        }}
      >
        Вставить:
      </span>

      <Tooltip>
        <TooltipTrigger asChild>
          <Button
            variant="ghost"
            size="icon-xs"
            onMouseDown={(e) => { e.preventDefault(); editor.chain().focus().toggleBulletList().run() }}
          >
            <List className="size-2.5" />
          </Button>
        </TooltipTrigger>
        <TooltipContent>Маркированный список</TooltipContent>
      </Tooltip>

      <Tooltip>
        <TooltipTrigger asChild>
          <Button
            variant="ghost"
            size="icon-xs"
            onMouseDown={(e) => { e.preventDefault(); editor.chain().focus().toggleOrderedList().run() }}
          >
            <ListOrdered className="size-2.5" />
          </Button>
        </TooltipTrigger>
        <TooltipContent>Нумерованный список</TooltipContent>
      </Tooltip>

      <Tooltip>
        <TooltipTrigger asChild>
          <Button
            variant="ghost"
            size="icon-xs"
            onMouseDown={(e) => { e.preventDefault(); editor.chain().focus().insertNumberedClause(1).run() }}
          >
            <Hash className="size-2.5" />
          </Button>
        </TooltipTrigger>
        <TooltipContent>Нумерованный пункт (§)</TooltipContent>
      </Tooltip>

      <Tooltip>
        <TooltipTrigger asChild>
          <Button
            variant="ghost"
            size="icon-xs"
            onMouseDown={(e) => {
              e.preventDefault()
              editor.chain().focus().insertTable({ rows: 3, cols: 3, withHeaderRow: false }).run()
            }}
          >
            <Table className="size-2.5" />
          </Button>
        </TooltipTrigger>
        <TooltipContent>Таблица</TooltipContent>
      </Tooltip>

      {firstParam && (
        <Tooltip>
          <TooltipTrigger asChild>
            <Button
              variant="ghost"
              size="icon-xs"
              onMouseDown={(e) => {
                e.preventDefault()
                editor.chain().focus().insertParameterField({
                  parameterCode: firstParam.code,
                  display: firstParam.label,
                }).run()
              }}
            >
            <Plus className="size-2.5" />
          </Button>
        </TooltipTrigger>
        <TooltipContent>Параметр: {firstParam.label}</TooltipContent>
        </Tooltip>
      )}
    </div>
  )

  return createPortal(menu, containerRef.current)
}
