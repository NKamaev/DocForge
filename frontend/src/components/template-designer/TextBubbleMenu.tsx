import { useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import type { Editor } from '@tiptap/react'
import { useEditorState } from '@tiptap/react'
import { BubbleMenuPlugin } from '@tiptap/extension-bubble-menu'
import { PluginKey } from '@tiptap/pm/state'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { ColorPicker } from '@/components/ui/color-picker'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from '@/components/ui/tooltip'
import {
  Bold,
  Italic,
  Link,
  Plus,
  Strikethrough,
  Underline,
} from 'lucide-react'
import type { Parameter } from '../../types/template'

const BUBBLE_MENU_KEY = new PluginKey('textBubbleMenu')

interface Props {
  editor: Editor
  parameters?: Parameter[]
}

export default function TextBubbleMenu({ editor, parameters = [] }: Props) {
  const containerRef = useRef<HTMLDivElement>(document.createElement('div'))
  const [ready, setReady] = useState(false)
  const [linkUrl, setLinkUrl] = useState('')

  useEffect(() => {
    const element = containerRef.current
    element.style.visibility = 'hidden'
    element.style.position = 'absolute'
    element.style.zIndex = '100'
    document.body.appendChild(element)

    const plugin = BubbleMenuPlugin({
      pluginKey: BUBBLE_MENU_KEY,
      editor,
      element,
      shouldShow: ({ state, from, to }) => {
        if (from === to) return false
        const { doc } = state
        const resolvedFrom = doc.resolve(from)
        for (let d = resolvedFrom.depth; d > 0; d--) {
          const node = resolvedFrom.node(d)
          if (node.type.name === 'table') return false
        }
        return true
      },
    })

    editor.registerPlugin(plugin)
    setReady(true)

    return () => {
      editor.unregisterPlugin(BUBBLE_MENU_KEY)
      if (document.body.contains(element)) {
        document.body.removeChild(element)
      }
    }
  }, [editor])

  const editorState = useEditorState({
    editor,
    selector: ({ editor: e }) => ({
      isBold: e.isActive('bold'),
      isItalic: e.isActive('italic'),
      isUnderline: e.isActive('underline'),
      isStrike: e.isActive('strike'),
      isLink: e.isActive('link'),
      selectionEmpty: e.state.selection.empty,
      textColor: e.getAttributes('textStyle').color as string | undefined,
    }),
  })

  const setLink = () => {
    const href = linkUrl.startsWith('http') ? linkUrl : `https://${linkUrl}`
    editor.chain().focus().setLink({ href, target: '_blank' }).run()
    setLinkUrl('')
  }

  if (!ready) return null

  const menu = (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 2,
        background: '#fff',
        border: '1px solid #d9d9d9',
        borderRadius: 6,
        padding: '4px 6px',
        boxShadow: '0 4px 12px rgba(0,0,0,0.15)',
        userSelect: 'none',
      }}
    >
      <div className="flex items-center gap-0.5">
        <Tooltip>
          <TooltipTrigger asChild>
            <Button
              variant={editorState.isBold ? 'default' : 'ghost'}
              size="icon-sm"
              className="h-8 w-8"
              onMouseDown={(e) => { e.preventDefault(); editor.chain().focus().toggleBold().run() }}
              data-testid="bubble-bold"
            >
              <Bold className="size-4" />
            </Button>
          </TooltipTrigger>
          <TooltipContent>Жирный</TooltipContent>
        </Tooltip>

        <Tooltip>
          <TooltipTrigger asChild>
            <Button
              variant={editorState.isItalic ? 'default' : 'ghost'}
              size="icon-sm"
              className="h-8 w-8"
              onMouseDown={(e) => { e.preventDefault(); editor.chain().focus().toggleItalic().run() }}
              data-testid="bubble-italic"
            >
              <Italic className="size-4" />
            </Button>
          </TooltipTrigger>
          <TooltipContent>Курсив</TooltipContent>
        </Tooltip>

        <Tooltip>
          <TooltipTrigger asChild>
            <Button
              variant={editorState.isUnderline ? 'default' : 'ghost'}
              size="icon-sm"
              className="h-8 w-8"
              onMouseDown={(e) => { e.preventDefault(); editor.chain().focus().toggleUnderline().run() }}
              data-testid="bubble-underline"
            >
              <Underline className="size-4" />
            </Button>
          </TooltipTrigger>
          <TooltipContent>Подчёркнутый</TooltipContent>
        </Tooltip>

        <Tooltip>
          <TooltipTrigger asChild>
            <Button
              variant={editorState.isStrike ? 'default' : 'ghost'}
              size="icon-sm"
              className="h-8 w-8"
              onMouseDown={(e) => { e.preventDefault(); editor.chain().focus().toggleStrike().run() }}
              data-testid="bubble-strike"
            >
              <Strikethrough className="size-4" />
            </Button>
          </TooltipTrigger>
          <TooltipContent>Зачёркнутый</TooltipContent>
        </Tooltip>
      </div>

      <div className="w-px h-5 bg-border mx-1" />

      <ColorPicker
        value={editorState.textColor}
        onChange={(color) => editor.chain().focus().setColor(color).run()}
        onClear={() => editor.chain().focus().unsetColor().run()}
        allowClear
        data-testid="bubble-color-text"
      />

      <div className="w-px h-5 bg-border mx-1" />

      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <Tooltip>
            <TooltipTrigger asChild>
              <Button
                variant={editorState.isLink ? 'default' : 'ghost'}
                size="icon-sm"
                className="h-8 w-8"
                data-testid="bubble-link"
              >
                <Link className="size-4" />
              </Button>
            </TooltipTrigger>
            <TooltipContent>Ссылка</TooltipContent>
          </Tooltip>
        </DropdownMenuTrigger>
        <DropdownMenuContent className="w-80 p-2">
          <div className="flex items-center gap-2">
            <Input
              value={linkUrl}
              onChange={(e) => setLinkUrl(e.target.value)}
              placeholder="https://..."
              className="h-8 text-sm"
              onKeyDown={(e) => {
                if (e.key === 'Enter') {
                  setLink()
                }
              }}
            />
            <Button
              size="sm"
              onClick={setLink}
            >
              ОК
            </Button>
          </div>
          {editorState.isLink && (
            <DropdownMenuItem
              variant="destructive"
              className="mt-1 cursor-pointer"
              onClick={() => editor.chain().focus().unsetLink().run()}
            >
              Удалить ссылку
            </DropdownMenuItem>
          )}
        </DropdownMenuContent>
      </DropdownMenu>

      {parameters.length > 0 && (
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Tooltip>
              <TooltipTrigger asChild>
                <Button
                  variant="ghost"
                  size="icon-sm"
                  className="h-8 w-8"
                  data-testid="bubble-insert-param"
                >
                  <Plus className="size-4" />
                </Button>
              </TooltipTrigger>
              <TooltipContent>Вставить параметр</TooltipContent>
            </Tooltip>
          </DropdownMenuTrigger>
          <DropdownMenuContent>
            {parameters.flatMap((p) => {
              if (p.type === 'DIRECTORY' && p.directoryFields?.length) {
                return p.directoryFields.map((fieldKey) => (
                  <DropdownMenuItem
                    key={`${p.code}__${fieldKey}`}
                    onClick={() =>
                      editor.chain().focus().insertParameterField({
                        parameterCode: `${p.code}__${fieldKey}`,
                        display: `${p.label} → ${fieldKey}`,
                      }).run()
                    }
                  >
                    {p.label} → {fieldKey}
                  </DropdownMenuItem>
                ))
              }
              return [(
                <DropdownMenuItem
                  key={p.code}
                  onClick={() =>
                    editor.chain().focus().insertParameterField({ parameterCode: p.code, display: p.label }).run()
                  }
                >
                  {p.label}
                </DropdownMenuItem>
              )]
            })}
          </DropdownMenuContent>
        </DropdownMenu>
      )}

      <Tooltip>
        <TooltipTrigger asChild>
          <Button
            variant="ghost"
            size="sm"
            className="text-[11px] h-8 px-2"
            disabled={editorState.selectionEmpty}
            onMouseDown={(e) => {
              e.preventDefault()
              editor.chain().focus()
                .wrapInConditionalInline({ label: 'Условный текст', condition: { type: 'simple', expression: '' } })
                .run()
            }}
            data-testid="bubble-conditional"
          >
            [если]
          </Button>
        </TooltipTrigger>
        <TooltipContent>
          {editorState.selectionEmpty ? 'Выделите текст' : 'Обернуть в условный текст'}
        </TooltipContent>
      </Tooltip>
    </div>
  )

  return createPortal(menu, containerRef.current)
}
