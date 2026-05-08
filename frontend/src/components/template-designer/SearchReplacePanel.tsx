import { useEffect, useRef, useState } from 'react'
import { Button, Checkbox, Input, Space, Tooltip } from 'antd'
import {
  ArrowDownOutlined,
  ArrowUpOutlined,
  CloseOutlined,
} from '@ant-design/icons'
import type { Editor } from '@tiptap/react'
import { searchReplaceKey } from './extensions/SearchReplaceExtension'
import { useEditorState } from '@tiptap/react'

interface Props {
  editor: Editor
  open: boolean
  onClose: () => void
}

export default function SearchReplacePanel({ editor, open, onClose }: Props) {
  const [searchTerm, setSearchTerm] = useState('')
  const [replacement, setReplacement] = useState('')
  const [caseSensitive, setCaseSensitive] = useState(false)
  const [wholeWord, setWholeWord] = useState(false)
  const searchInputRef = useRef<HTMLInputElement>(null)

  const pluginState = useEditorState({
    editor,
    selector: ({ editor: e }) => searchReplaceKey.getState(e.view.state),
  })

  const matchCount = pluginState?.matches.length ?? 0
  const currentIndex = pluginState?.currentIndex ?? 0

  // Focus search input when opened
  useEffect(() => {
    if (open) {
      setTimeout(() => searchInputRef.current?.focus(), 50)
    } else {
      editor.commands.clearSearch()
      setSearchTerm('')
    }
  }, [open, editor])

  // Re-run search when term or options change
  useEffect(() => {
    if (!open) return
    editor.commands.setSearch(searchTerm, { caseSensitive, wholeWord })
  }, [searchTerm, caseSensitive, wholeWord, open, editor])

  // Escape closes the panel
  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && open) {
        e.preventDefault()
        onClose()
      }
    }
    window.addEventListener('keydown', handler)
    return () => window.removeEventListener('keydown', handler)
  }, [open, onClose])

  if (!open) return null

  const counterText = matchCount === 0
    ? (searchTerm ? 'Не найдено' : '')
    : `${currentIndex + 1} из ${matchCount}`

  return (
    <div
      style={{
        padding: '8px 12px',
        borderBottom: '1px solid #d9d9d9',
        background: '#fffbe6',
        display: 'flex',
        flexDirection: 'column',
        gap: 6,
      }}
    >
      {/* Row 1: Search */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
        <Input
          ref={searchInputRef as React.Ref<never>}
          size="small"
          placeholder="Найти…"
          value={searchTerm}
          onChange={(e) => setSearchTerm(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter') {
              e.shiftKey
                ? editor.commands.prevMatch()
                : editor.commands.nextMatch()
            }
          }}
          style={{ width: 220 }}
          suffix={
            <span style={{ fontSize: 11, color: matchCount === 0 && searchTerm ? '#ff4d4f' : '#8c8c8c', minWidth: 50 }}>
              {counterText}
            </span>
          }
        />
        <Tooltip title="Предыдущее (Shift+Enter)">
          <Button
            size="small"
            icon={<ArrowUpOutlined />}
            onClick={() => editor.commands.prevMatch()}
            disabled={matchCount === 0}
          />
        </Tooltip>
        <Tooltip title="Следующее (Enter)">
          <Button
            size="small"
            icon={<ArrowDownOutlined />}
            onClick={() => editor.commands.nextMatch()}
            disabled={matchCount === 0}
          />
        </Tooltip>
        <Space size={12} style={{ marginLeft: 4 }}>
          <Checkbox
            checked={caseSensitive}
            onChange={(e) => setCaseSensitive(e.target.checked)}
            style={{ fontSize: 12 }}
          >
            Aa
          </Checkbox>
          <Checkbox
            checked={wholeWord}
            onChange={(e) => setWholeWord(e.target.checked)}
            style={{ fontSize: 12 }}
          >
            Слово
          </Checkbox>
        </Space>
        <Tooltip title="Закрыть (Escape)">
          <Button
            size="small"
            icon={<CloseOutlined />}
            type="text"
            onClick={onClose}
            style={{ marginLeft: 'auto' }}
          />
        </Tooltip>
      </div>

      {/* Row 2: Replace */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
        <Input
          size="small"
          placeholder="Заменить на…"
          value={replacement}
          onChange={(e) => setReplacement(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter') editor.commands.replaceMatch(replacement)
          }}
          style={{ width: 220 }}
        />
        <Button
          size="small"
          onClick={() => editor.commands.replaceMatch(replacement)}
          disabled={matchCount === 0}
        >
          Заменить
        </Button>
        <Button
          size="small"
          onClick={() => editor.commands.replaceAll(replacement)}
          disabled={matchCount === 0}
        >
          Заменить всё
        </Button>
      </div>
    </div>
  )
}
