import { useEffect, useState } from 'react'
import { createPortal } from 'react-dom'

type ToastType = 'success' | 'error'

interface ToastItem {
  id: number
  message: string
  type: ToastType
}

let toastId = 0

const TOAST_EVENT = 'app-toast'

export function showToast(message: string, type: ToastType = 'success') {
  // Debug: also set document title so we can verify the call happened
  document.title = 'TOAST: ' + message.slice(0, 40)

  // Use CustomEvent instead of module-level listeners array to avoid
  // HMR / module closure issues
  window.dispatchEvent(
    new CustomEvent<{ id: number; message: string; type: ToastType }>(TOAST_EVENT, {
      detail: { id: ++toastId, message, type },
    })
  )

  // Auto-dismiss after 3.5s
  setTimeout(() => {
    window.dispatchEvent(
      new CustomEvent<{ id: number; message: string; type: ToastType }>(TOAST_EVENT, {
        detail: { id: toastId, message: '', type },
      })
    )
  }, 3500)
}

export function ToastContainer() {
  const [toasts, setToasts] = useState<ToastItem[]>([])

  useEffect(() => {
    const handler = (e: Event) => {
      const t = (e as CustomEvent<{ id: number; message: string; type: ToastType }>).detail
      if (t.message === '') {
        setToasts(prev => prev.filter(x => x.id !== t.id))
      } else {
        setToasts(prev => [...prev, t])
      }
    }

    window.addEventListener(TOAST_EVENT, handler)
    return () => window.removeEventListener(TOAST_EVENT, handler)
  }, [])

  if (toasts.length === 0) return null

  return createPortal(
    <div
      data-testid="toast-container"
      style={{
        position: 'fixed',
        bottom: 20,
        right: 20,
        zIndex: 9999,
        display: 'flex',
        flexDirection: 'column',
        gap: 8,
      }}
    >
      {toasts.map(t => (
        <div
          key={t.id}
          data-testid={`toast-${t.id}`}
          role="alert"
          style={{
            padding: '10px 18px',
            borderRadius: 8,
            fontSize: 13,
            color: '#fff',
            background: t.type === 'error' ? '#e53e3e' : '#16a34a',
            boxShadow: '0 4px 12px rgba(0,0,0,0.15)',
            maxWidth: 360,
          }}
        >
          {t.message}
        </div>
      ))}
    </div>,
    document.body
  )
}
