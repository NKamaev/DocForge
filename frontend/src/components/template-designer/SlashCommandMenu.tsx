import { forwardRef, useEffect, useImperativeHandle, useState } from 'react'
import type { SlashCommand } from './extensions/SlashCommandExtension'

interface Props {
  items: SlashCommand[]
  command: (item: SlashCommand) => void
}

export interface SlashCommandMenuRef {
  onKeyDown: (event: KeyboardEvent) => boolean
}

const SlashCommandMenu = forwardRef<SlashCommandMenuRef, Props>(({ items, command }, ref) => {
  const [selectedIndex, setSelectedIndex] = useState(0)

  useEffect(() => setSelectedIndex(0), [items])

  useImperativeHandle(ref, () => ({
    onKeyDown(event: KeyboardEvent) {
      if (event.key === 'ArrowUp') {
        setSelectedIndex((i) => (i - 1 + items.length) % items.length)
        return true
      }
      if (event.key === 'ArrowDown') {
        setSelectedIndex((i) => (i + 1) % items.length)
        return true
      }
      if (event.key === 'Enter') {
        if (items[selectedIndex]) command(items[selectedIndex])
        return true
      }
      return false
    },
  }))

  if (!items.length) return null

  return (
    <div
      style={{
        background: '#fff',
        border: '1px solid #d9d9d9',
        borderRadius: 8,
        boxShadow: '0 4px 16px rgba(0,0,0,0.12)',
        padding: 4,
        minWidth: 240,
        maxHeight: 320,
        overflowY: 'auto',
        zIndex: 9999,
      }}
    >
      {items.map((item, i) => (
        <div
          key={item.title}
          onClick={() => command(item)}
          style={{
            display: 'flex',
            flexDirection: 'column',
            padding: '6px 10px',
            borderRadius: 6,
            cursor: 'pointer',
            background: i === selectedIndex ? '#f0f5ff' : 'transparent',
            transition: 'background 0.1s',
          }}
          onMouseEnter={() => setSelectedIndex(i)}
        >
          <span style={{ fontWeight: 500, fontSize: 13, color: '#262626' }}>{item.title}</span>
          <span style={{ fontSize: 11, color: '#8c8c8c' }}>{item.description}</span>
        </div>
      ))}
    </div>
  )
})

SlashCommandMenu.displayName = 'SlashCommandMenu'

export default SlashCommandMenu
