import { useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { Button, ColorPicker, Dropdown, Space, Tooltip } from 'antd'
import type { MenuProps } from 'antd'
import type { Editor } from '@tiptap/react'
import { useEditorState } from '@tiptap/react'
import { BubbleMenuPlugin } from '@tiptap/extension-bubble-menu'
import { PluginKey } from '@tiptap/pm/state'
import {
  BoldOutlined,
  ItalicOutlined,
  LinkOutlined,
  PlusSquareOutlined,
  StrikethroughOutlined,
  UnderlineOutlined,
} from '@ant-design/icons'
import type { Parameter } from '../../types/template'

// Keep a reference to the BubbleMenuView for cleanup
// (the plugin holds it internally — just need to unregister via key)
const BUBBLE_MENU_KEY = new PluginKey('textBubbleMenu')

interface Props {
  editor: Editor
  parameters?: Parameter[]
}

export default function TextBubbleMenu({ editor, parameters = [] }: Props) {
  const containerRef = useRef<HTMLDivElement>(document.createElement('div'))
  const [ready, setReady] = useState(false)
  const [linkUrl, setLinkUrl] = useState('')

  // Register BubbleMenuPlugin once on mount
  useEffect(() => {
    const element = containerRef.current
    // Make the element invisible until placed (plugin controls visibility)
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
        // Don't show over table selections
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

  // Observe editor state to re-render menu content
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

  const paramMenuItems: MenuProps['items'] = parameters.flatMap((p) => {
    if (p.type === 'DIRECTORY' && p.directoryFields?.length) {
      return p.directoryFields.map((fieldKey) => ({
        key: `${p.code}__${fieldKey}`,
        label: `${p.label} → ${fieldKey}`,
        onClick: () =>
          editor.chain().focus().insertParameterField({
            parameterCode: `${p.code}__${fieldKey}`,
            display: `${p.label} → ${fieldKey}`,
          }).run(),
      }))
    }
    return [{
      key: p.code,
      label: p.label,
      onClick: () =>
        editor.chain().focus().insertParameterField({ parameterCode: p.code, display: p.label }).run(),
    }]
  })

  const linkMenuItems: MenuProps['items'] = [
    {
      key: 'link-input',
      label: (
        <div style={{ display: 'flex', gap: 6 }} onClick={(e) => e.stopPropagation()}>
          <input
            value={linkUrl}
            onChange={(e) => setLinkUrl(e.target.value)}
            placeholder="https://..."
            style={{ flex: 1, border: '1px solid #d9d9d9', borderRadius: 4, padding: '2px 6px', outline: 'none', fontSize: 12 }}
            onKeyDown={(e) => {
              if (e.key === 'Enter') {
                const href = linkUrl.startsWith('http') ? linkUrl : `https://${linkUrl}`
                editor.chain().focus().setLink({ href, target: '_blank' }).run()
                setLinkUrl('')
              }
            }}
          />
          <Button
            size="small"
            type="primary"
            onClick={() => {
              const href = linkUrl.startsWith('http') ? linkUrl : `https://${linkUrl}`
              editor.chain().focus().setLink({ href, target: '_blank' }).run()
              setLinkUrl('')
            }}
          >ОК</Button>
        </div>
      ),
    },
    ...(editorState.isLink ? [{
      key: 'remove-link',
      label: 'Удалить ссылку',
      danger: true,
      onClick: () => editor.chain().focus().unsetLink().run(),
    }] : []),
  ]

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
      <Space size={2}>
        <Tooltip title="Жирный">
          <Button
            size="small"
            type={editorState.isBold ? 'primary' : 'default'}
            icon={<BoldOutlined />}
            onMouseDown={(e) => { e.preventDefault(); editor.chain().focus().toggleBold().run() }}
          />
        </Tooltip>
        <Tooltip title="Курсив">
          <Button
            size="small"
            type={editorState.isItalic ? 'primary' : 'default'}
            icon={<ItalicOutlined />}
            onMouseDown={(e) => { e.preventDefault(); editor.chain().focus().toggleItalic().run() }}
          />
        </Tooltip>
        <Tooltip title="Подчёркнутый">
          <Button
            size="small"
            type={editorState.isUnderline ? 'primary' : 'default'}
            icon={<UnderlineOutlined />}
            onMouseDown={(e) => { e.preventDefault(); editor.chain().focus().toggleUnderline().run() }}
          />
        </Tooltip>
        <Tooltip title="Зачёркнутый">
          <Button
            size="small"
            type={editorState.isStrike ? 'primary' : 'default'}
            icon={<StrikethroughOutlined />}
            onMouseDown={(e) => { e.preventDefault(); editor.chain().focus().toggleStrike().run() }}
          />
        </Tooltip>
      </Space>

      <div style={{ width: 1, background: '#d9d9d9', height: 20, margin: '0 4px' }} />

      <Tooltip title="Цвет текста">
        <ColorPicker
          size="small"
          value={editorState.textColor}
          onChange={(color) => editor.chain().focus().setColor(color.toHexString()).run()}
          onClear={() => editor.chain().focus().unsetColor().run()}
          allowClear
        />
      </Tooltip>

      <div style={{ width: 1, background: '#d9d9d9', height: 20, margin: '0 4px' }} />

      <Dropdown menu={{ items: linkMenuItems }} trigger={['click']} placement="bottom">
        <Tooltip title="Ссылка">
          <Button
            size="small"
            type={editorState.isLink ? 'primary' : 'default'}
            icon={<LinkOutlined />}
          />
        </Tooltip>
      </Dropdown>

      {parameters.length > 0 && (
        <Dropdown menu={{ items: paramMenuItems }} trigger={['click']} placement="bottom">
          <Tooltip title="Вставить параметр">
            <Button size="small" icon={<PlusSquareOutlined />} />
          </Tooltip>
        </Dropdown>
      )}

      <Tooltip title={editorState.selectionEmpty ? 'Выделите текст' : 'Обернуть в условный текст'}>
        <Button
          size="small"
          disabled={editorState.selectionEmpty}
          style={{ fontSize: 11, padding: '0 6px' }}
          onMouseDown={(e) => {
            e.preventDefault()
            editor.chain().focus()
              .wrapInConditionalInline({ label: 'Условный текст', condition: { type: 'simple', expression: '' } })
              .run()
          }}
        >
          [если]
        </Button>
      </Tooltip>
    </div>
  )

  return createPortal(menu, containerRef.current)
}
