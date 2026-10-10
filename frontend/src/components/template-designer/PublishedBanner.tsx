import { Lock } from 'lucide-react'

export default function PublishedBanner() {
  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 8,
        padding: '8px 16px',
        background: '#fffbe6',
        border: '1px solid #ffe58f',
        borderRadius: 0,
        flexShrink: 0,
        color: '#d46b08',
        fontSize: 13,
      }}
    >
      <Lock size={16} />
      <span>Шаблон в режиме только для чтения (опубликован)</span>
    </div>
  )
}
