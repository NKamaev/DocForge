import { useCallback, useEffect, useRef, useState } from 'react'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuSub,
  DropdownMenuSubTrigger,
  DropdownMenuSubContent,
} from '@/components/ui/dropdown-menu'
import type { Editor } from '@tiptap/react'
import type { Parameter } from '../../types/template'
import type { NumberingStyle } from './extensions/NumberedClauseNode'

interface MenuItem {
  key: string
  label?: string
  onClick?: () => void
  disabled?: boolean
  danger?: boolean
  children?: MenuItem[]
  type?: 'divider' | 'group'
}

interface CtxState {
  x: number
  y: number
  items: MenuItem[]
}

function buildTextItems(editor: Editor, params: Parameter[]): MenuItem[] {
  const items: MenuItem[] = [
    {
      key: 'bold',
      label: editor.isActive('bold') ? '✓ Жирный' : 'Жирный',
      onClick: () => editor.chain().focus().toggleBold().run(),
    },
    {
      key: 'italic',
      label: editor.isActive('italic') ? '✓ Курсив' : 'Курсив',
      onClick: () => editor.chain().focus().toggleItalic().run(),
    },
    {
      key: 'underline',
      label: editor.isActive('underline') ? '✓ Подчёркнутый' : 'Подчёркнутый',
      onClick: () => editor.chain().focus().toggleUnderline().run(),
    },
    {
      key: 'strike',
      label: editor.isActive('strike') ? '✓ Зачёркнутый' : 'Зачёркнутый',
      onClick: () => editor.chain().focus().toggleStrike().run(),
    },
  ]

  if (params.length > 0) {
    items.push({ type: 'divider', key: 'div-params' })
    items.push({
      key: 'params',
      label: 'Вставить параметр',
      children: params.map((p) => ({
        key: `param-${p.code}`,
        label: p.label,
        onClick: () =>
          editor.chain().focus().insertParameterField({ parameterCode: p.code, display: p.label }).run(),
      })),
    })
  }

  if (editor.isActive('link')) {
    items.push({ type: 'divider', key: 'div-unlink' })
    items.push({
      key: 'unlink',
      label: 'Удалить ссылку',
      danger: true,
      onClick: () => editor.chain().focus().unsetLink().run(),
    })
  }

  return items
}

function buildTableItems(editor: Editor): MenuItem[] {
  return [
    {
      key: 'rows',
      type: 'group',
      label: 'Строки',
      children: [
        { key: 'row-before', label: 'Строка выше', onClick: () => editor.chain().focus().addRowBefore().run() },
        { key: 'row-after', label: 'Строка ниже', onClick: () => editor.chain().focus().addRowAfter().run() },
        { key: 'row-delete', label: 'Удалить строку', danger: true, onClick: () => editor.chain().focus().deleteRow().run() },
      ],
    },
    { type: 'divider', key: 'div1' },
    {
      key: 'cols',
      type: 'group',
      label: 'Колонки',
      children: [
        { key: 'col-before', label: 'Колонка слева', onClick: () => editor.chain().focus().addColumnBefore().run() },
        { key: 'col-after', label: 'Колонка справа', onClick: () => editor.chain().focus().addColumnAfter().run() },
        { key: 'col-delete', label: 'Удалить колонку', danger: true, onClick: () => editor.chain().focus().deleteColumn().run() },
      ],
    },
    { type: 'divider', key: 'div2' },
    {
      key: 'cells',
      type: 'group',
      label: 'Ячейки',
      children: [
        {
          key: 'merge',
          label: 'Объединить ячейки',
          disabled: !editor.can().mergeCells(),
          onClick: () => editor.chain().focus().mergeCells().run(),
        },
        {
          key: 'split',
          label: 'Разбить ячейку',
          disabled: !editor.can().splitCell(),
          onClick: () => editor.chain().focus().splitCell().run(),
        },
      ],
    },
    { type: 'divider', key: 'div3' },
    { key: 'table-delete', label: 'Удалить таблицу', danger: true, onClick: () => editor.chain().focus().deleteTable().run() },
  ]
}

function buildClauseItems(editor: Editor): MenuItem[] {
  const LEVEL_LABELS: Record<number, string> = {
    1: 'Уровень 1', 2: 'Уровень 2', 3: 'Уровень 3', 4: 'Уровень 4', 5: 'Уровень 5',
  }
  const STYLE_LABELS: Record<NumberingStyle, string> = {
    decimal: '1 2 3 (числа)', 'alpha-ru': 'а б в (рус.)', 'alpha-en': 'a b c (лат.)',
  }
  const isListStart = editor.isActive('numberedClause', { listStart: true })

  return [
    {
      key: 'clause-level',
      label: 'Изменить уровень',
      children: ([1, 2, 3, 4, 5] as const).map((level) => ({
        key: `level-${level}`,
        label: LEVEL_LABELS[level],
        onClick: () => editor.chain().focus().insertNumberedClause(level).run(),
      })),
    },
    {
      key: 'clause-style',
      label: 'Стиль нумерации',
      children: (['decimal', 'alpha-ru', 'alpha-en'] as NumberingStyle[]).map((style) => ({
        key: `style-${style}`,
        label: STYLE_LABELS[style],
        onClick: () => editor.chain().focus().setClauseNumberingStyle(style).run(),
      })),
    },
    { type: 'divider', key: 'div' },
    {
      key: 'list-start',
      label: isListStart ? '✓ Начать новый список' : 'Начать новый список',
      onClick: () => editor.chain().focus().toggleClauseListStart().run(),
    },
  ]
}

function detectContext(target: EventTarget | null, editor: Editor): 'table' | 'clause' | 'text' {
  let el = target as HTMLElement | null
  while (el) {
    if (el.tagName === 'TD' || el.tagName === 'TH' || el.tagName === 'TABLE') return 'table'
    if (el.dataset?.nodeType === 'numberedClause' || el.classList?.contains('numbered-clause')) return 'clause'
    if (el.classList?.contains('ProseMirror')) break
    el = el.parentElement
  }
  if (editor.isActive('table')) return 'table'
  if (editor.isActive('numberedClause')) return 'clause'
  return 'text'
}

interface UseEditorContextMenuResult {
  contextMenu: CtxState | null
  handleContextMenu: (e: React.MouseEvent, editor: Editor, params: Parameter[]) => void
  closeMenu: () => void
}

export function useEditorContextMenu(): UseEditorContextMenuResult {
  const [contextMenu, setContextMenu] = useState<CtxState | null>(null)

  const handleContextMenu = useCallback(
    (e: React.MouseEvent, editor: Editor, params: Parameter[]) => {
      e.preventDefault()

      const ctx = detectContext(e.target, editor)
      let items: MenuItem[]
      if (ctx === 'table') {
        items = buildTableItems(editor)
      } else if (ctx === 'clause') {
        items = buildClauseItems(editor)
      } else {
        items = buildTextItems(editor, params)
      }

      setContextMenu({ x: e.clientX, y: e.clientY, items })
    },
    [],
  )

  const closeMenu = useCallback(() => setContextMenu(null), [])

  return { contextMenu, handleContextMenu, closeMenu }
}

function MenuItemRenderer({ item, onClose }: { item: MenuItem; onClose: () => void }) {
  if (item.type === 'divider') {
    return <DropdownMenuSeparator />
  }

  if (item.children && item.children.length > 0) {
    return (
      <DropdownMenuSub>
        <DropdownMenuSubTrigger className="cursor-pointer">
          {item.label}
        </DropdownMenuSubTrigger>
        <DropdownMenuSubContent>
          {item.children.map(child => (
            <MenuItemRenderer key={child.key} item={child} onClose={onClose} />
          ))}
        </DropdownMenuSubContent>
      </DropdownMenuSub>
    )
  }

  return (
    <DropdownMenuItem
      key={item.key}
      disabled={item.disabled}
      className={item.danger ? 'text-destructive cursor-pointer' : 'cursor-pointer'}
      onClick={() => {
        item.onClick?.()
        onClose()
      }}
    >
      {item.label}
    </DropdownMenuItem>
  )
}

export function ContextMenuPortal({ ctx, onClose }: { ctx: CtxState | null; onClose: () => void }) {
  const popupRef = useRef<HTMLSpanElement>(null)

  useEffect(() => {
    if (!ctx) return

    const handleMouseDown = (e: MouseEvent) => {
      const popup = document.querySelector('[data-radix-popper-content-wrapper]')
      if (popup && popup.contains(e.target as Node)) return
      onClose()
    }

    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose()
    }

    const tid = setTimeout(() => {
      document.addEventListener('mousedown', handleMouseDown)
      document.addEventListener('keydown', handleKeyDown)
    }, 0)

    return () => {
      clearTimeout(tid)
      document.removeEventListener('mousedown', handleMouseDown)
      document.removeEventListener('keydown', handleKeyDown)
    }
  }, [ctx, onClose])

  if (!ctx) return null

  return (
    <DropdownMenu open onOpenChange={(open) => !open && onClose()}>
      <span
        ref={popupRef}
        style={{
          position: 'fixed',
          left: ctx.x,
          top: ctx.y,
          width: 0,
          height: 0,
          display: 'block',
          pointerEvents: 'none',
        }}
      />
      <DropdownMenuContent 
        style={{ 
          position: 'fixed',
          left: ctx.x,
          top: ctx.y,
        }}
        align="start"
        sideOffset={0}
      >
        {ctx.items.map(item => (
          <MenuItemRenderer key={item.key} item={item} onClose={onClose} />
        ))}
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
