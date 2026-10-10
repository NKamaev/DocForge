import { useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { Button } from '@/components/ui/button'
import { MultiSelect } from '@/components/ui/multi-select'
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from '@/components/ui/popover'
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from '@/components/ui/tooltip'
import { Icon } from '../Icon'
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
  const [paramPopover, setParamPopover] = useState(false)
  const [selectedParams, setSelectedParams] = useState<string[]>([])

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

  useEffect(() => {
    if (!paramPopover) setSelectedParams([])
  }, [paramPopover])

  const insertSelectedParams = () => {
    const chain = editor.chain().focus()
    selectedParams.forEach((code) => {
      const p = parameters.find((par) => par.code === code)
      if (p) {
        if (p.type === 'DIRECTORY' && p.directoryFields && p.directoryFields.length > 0) {
          p.directoryFields.forEach((fieldKey) => {
            chain.insertParameterField({
              parameterCode: `${p.code}__${fieldKey}`,
              display: `${p.label} → ${fieldKey}`,
            })
          })
        } else {
          chain.insertParameterField({
            parameterCode: p.code,
            display: p.label,
          })
        }
      }
    })
    chain.run()
    setParamPopover(false)
  }

  const paramOptions = parameters.map((p) => ({
    value: p.code,
    label: p.label,
    description: `${p.code} (${p.type})`,
  }))

  if (!ready) return null

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
            <Icon name="list-ul" size={12} />
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
            <Icon name="list-ol" size={12} />
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
            <Icon name="hash" size={12} />
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
            <Icon name="table" size={12} />
          </Button>
        </TooltipTrigger>
        <TooltipContent>Таблица</TooltipContent>
      </Tooltip>

      {parameters.length > 0 && (
        <Popover open={paramPopover} onOpenChange={setParamPopover}>
          <PopoverTrigger asChild>
            <Button
              variant="ghost"
              size="icon-xs"
              onMouseDown={(e) => e.preventDefault()}
              title="Вставить параметры"
            >
              <Icon name="param" size={12} />
            </Button>
          </PopoverTrigger>
          <PopoverContent className="w-72 p-3" align="start">
            <div className="space-y-3">
              <p className="text-xs font-medium text-muted-foreground">
                Выберите параметры для вставки
              </p>
              <MultiSelect
                options={paramOptions}
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
                  onClick={() => setParamPopover(false)}
                >
                  Отмена
                </Button>
                <Button
                  size="sm"
                  onClick={insertSelectedParams}
                  disabled={selectedParams.length === 0}
                >
                  Вставить ({selectedParams.length})
                </Button>
              </div>
            </div>
          </PopoverContent>
        </Popover>
      )}
    </div>
  )

  return createPortal(menu, containerRef.current)
}
