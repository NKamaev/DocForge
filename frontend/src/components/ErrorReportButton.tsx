import { useState, useCallback, useRef, useEffect } from 'react'
import { createPortal } from 'react-dom'
import { Icon } from './Icon'
import api from '../api/axiosInstance'

interface ElementInfo {
  selector: string
  label: string
}

function getElementSelector(el: Element): string {
  const parts: string[] = []
  let current: Element | null = el
  let depth = 0
  while (current && current !== document.body && current !== document.documentElement && depth < 8) {
    const tag = current.tagName.toLowerCase()
    if (current.id) {
      parts.unshift(`#${CSS.escape(current.id)}`)
      break
    }
    const slot = current.getAttribute('data-slot')
    if (slot) {
      parts.unshift(`${tag}[data-slot="${CSS.escape(slot)}"]`)
    } else {
      const parent = current.parentElement
      let idx = ''
      if (parent) {
        const siblings = Array.from(parent.children).filter((s) => s.tagName === current!.tagName)
        if (siblings.length > 1) {
          const pos = Array.from(parent.children).indexOf(current) + 1
          idx = `:nth-child(${pos})`
        }
      }
      parts.unshift(`${tag}${idx}`)
    }
    current = current.parentElement
    depth++
  }
  return parts.join(' > ')
}

function getElementLabel(el: Element): string {
  const tag = el.tagName.toLowerCase()

  // Button — title, aria-label, or text content
  if (tag === 'button' || tag === 'a') {
    const title = el.getAttribute('title')
    const aria = el.getAttribute('aria-label')
    const text = (el.textContent ?? '').trim().slice(0, 60)
    return title || aria || text || `<${tag}>`
  }

  // Input/textarea/select — placeholder or aria-label
  if (tag === 'input' || tag === 'textarea' || tag === 'select') {
    const placeholder = (el as HTMLInputElement).placeholder
    const aria = el.getAttribute('aria-label')
    const name = el.getAttribute('name')
    const type = (el as HTMLInputElement).type || tag
    return placeholder || aria || name || `<${type}>`
  }

  // Heading
  if (tag === 'h1' || tag === 'h2' || tag === 'h3' || tag === 'h4') {
    const text = (el.textContent ?? '').trim().slice(0, 60)
    return text || `<${tag}>`
  }

  // Image
  if (tag === 'img') {
    const alt = el.getAttribute('alt')
    const src = el.getAttribute('src')
    return alt || src?.split('/').pop() || '<img>'
  }

  // Generic — use the most distinctive class or first meaningful text
  const classes = Array.from(el.classList).filter((c) => !c.startsWith('bug-'))
  const classHint = classes.length > 0 ? `.${classes[0]}` : ''
  const text = (el.textContent ?? '').trim().slice(0, 40)
  const textHint = text ? ` «${text}»` : ''
  return `<${tag}>${classHint}${textHint}`
}

const HOVER_CLASS = 'bug-picker-hover'

export function ErrorReportButton() {
  const [open, setOpen] = useState(false)
  const [picking, setPicking] = useState(false)
  const [selectedElements, setSelectedElements] = useState<ElementInfo[]>([])
  const [description, setDescription] = useState('')
  const [sending, setSending] = useState(false)
  const [sent, setSent] = useState(false)
  const [error, setError] = useState('')

  const pickClickRef = useRef<((e: MouseEvent) => void) | null>(null)
  const pickOverRef = useRef<((e: MouseEvent) => void) | null>(null)
  const pickOutRef = useRef<((e: MouseEvent) => void) | null>(null)
  const pickKeyRef = useRef<((e: KeyboardEvent) => void) | null>(null)
  const lastHoverRef = useRef<Element | null>(null)
  const pickerBarRef = useRef<HTMLDivElement | null>(null)

  const cleanup = useCallback(() => {
    document.querySelectorAll(`.${HOVER_CLASS}, .bug-picker-highlight`).forEach((el) => {
      el.classList.remove(HOVER_CLASS, 'bug-picker-highlight')
    })
    if (pickClickRef.current) document.removeEventListener('click', pickClickRef.current, true)
    if (pickOverRef.current) document.removeEventListener('mouseover', pickOverRef.current, true)
    if (pickOutRef.current) document.removeEventListener('mouseout', pickOutRef.current, true)
    if (pickKeyRef.current) document.removeEventListener('keydown', pickKeyRef.current, true)
    document.body.style.cursor = ''
    pickClickRef.current = null
    pickOverRef.current = null
    pickOutRef.current = null
    pickKeyRef.current = null
    lastHoverRef.current = null
  }, [])

  const startPicker = useCallback(() => {
    if (pickClickRef.current) return

    setOpen(false)
    setPicking(true)
    setSelectedElements([])

    document.body.style.cursor = 'crosshair'

    const isInsidePicker = (el: Element) =>
      pickerBarRef.current && pickerBarRef.current.contains(el)

    pickOverRef.current = (e: MouseEvent) => {
      const el = e.target as Element
      if (!el || isInsidePicker(el)) return
      if (el !== lastHoverRef.current) {
        if (lastHoverRef.current) lastHoverRef.current.classList.remove(HOVER_CLASS)
        el.classList.add(HOVER_CLASS)
        lastHoverRef.current = el
      }
    }

    pickOutRef.current = (e: MouseEvent) => {
      const el = e.target as Element
      if (!el || isInsidePicker(el)) return
      el.classList.remove(HOVER_CLASS)
      if (lastHoverRef.current === el) lastHoverRef.current = null
    }

    pickClickRef.current = (e: MouseEvent) => {
      const target = e.target as Element
      if (!target || isInsidePicker(target)) return
      e.preventDefault()
      e.stopPropagation()
      const selector = getElementSelector(target)
      const label = getElementLabel(target)
      setSelectedElements((prev) => {
        if (prev.some((ei) => ei.selector === selector)) return prev
        return [...prev, { selector, label }]
      })
      target.classList.remove(HOVER_CLASS)
      target.classList.add('bug-picker-highlight')
    }

    pickKeyRef.current = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        cleanup()
        setPicking(false)
        setOpen(selectedElements.length > 0 || true)
      }
    }

    document.addEventListener('mouseover', pickOverRef.current, true)
    document.addEventListener('mouseout', pickOutRef.current, true)
    document.addEventListener('click', pickClickRef.current, true)
    document.addEventListener('keydown', pickKeyRef.current, true)
  }, [cleanup])

  const finishPicking = useCallback(() => {
    cleanup()
    setPicking(false)
    setOpen(true)
  }, [cleanup])

  useEffect(() => {
    if (!picking && !open) {
      cleanup()
      setSelectedElements([])
      setDescription('')
      setSent(false)
      setError('')
    }
  }, [picking, open, cleanup])

  const removeElement = (selector: string) => {
    setSelectedElements((prev) => prev.filter((ei) => ei.selector !== selector))
    const el = document.querySelector(selector)
    if (el) el.classList.remove('bug-picker-highlight')
  }

  const handleSubmit = async () => {
    setSending(true)
    setError('')
    try {
      const res = await api.post('/feedback', {
        elements: selectedElements,
        description,
        url: window.location.pathname,
      })
      if (res.status >= 200 && res.status < 300) {
        setSent(true)
      }
    } catch {
      setError('Ошибка при отправке. Попробуйте ещё раз.')
    } finally {
      setSending(false)
    }
  }

  return (
    <>
      <button className="icon-btn" title="Сообщить об ошибке" onClick={() => setOpen(true)}>
        <Icon name="bug" size={16} />
      </button>

      {picking && createPortal(
        <div ref={pickerBarRef} style={{
          position: 'fixed',
          bottom: 24,
          left: '50%',
          transform: 'translateX(-50%)',
          zIndex: 9999,
          background: 'var(--surface)',
          border: '1px solid var(--line)',
          borderRadius: 'var(--radius)',
          boxShadow: 'var(--shadow-pop)',
          padding: '8px 16px',
          display: 'flex',
          alignItems: 'center',
          gap: 8,
          fontSize: 13,
          color: 'var(--ink-2)',
        }}>
          <Icon name="crosshair" size={16} />
          <span>
            Кликайте по элементам, чтобы добавить их.
            {selectedElements.length > 0 && (
              <strong style={{ marginLeft: 4 }}>Выбрано: {selectedElements.length}</strong>
            )}
          </span>
          <span style={{ color: 'var(--ink-3)', fontSize: 12, marginLeft: 8 }}>Esc — отмена</span>
          <button
            className="btn xs primary"
            type="button"
            onClick={finishPicking}
            style={{ marginLeft: 8, pointerEvents: 'auto' }}
          >
            Готово
          </button>
        </div>,
        document.body
      )}

      {open && createPortal(
        <div className="modal-veil" onClick={() => sent || setOpen(false)}>
          <div className="modal" onClick={(e) => e.stopPropagation()} style={{ width: 480 }}>
            <div className="modal-head">
              <h3>{sent ? 'Отправлено' : 'Сообщить об ошибке'}</h3>
              <button className="icon-btn" onClick={() => setOpen(false)}>
                <Icon name="x" size={16} />
              </button>
            </div>

            <div className="modal-body">
              {sent ? (
                <div className="flex flex-col items-center gap-3 py-6">
                  <Icon name="check" size={32} />
                  <p style={{ color: 'var(--ink-3)', fontSize: 13 }}>Спасибо! Ваш отчёт отправлен.</p>
                </div>
              ) : (
                <>
                  <div className="field">
                    <label className="field-lbl">
                      Проблемные элементы
                      {selectedElements.length > 0 && (
                        <span className="text-xs text-muted-foreground ml-1">
                          ({selectedElements.length})
                        </span>
                      )}
                    </label>
                    <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                      {selectedElements.length === 0 ? (
                        <div style={{ display: 'flex', gap: 6 }}>
                          <input
                            readOnly
                            placeholder="Выберите элементы на странице"
                            className="input"
                            style={{ flex: 1 }}
                          />
                          <button className="btn" type="button" onClick={startPicker}>
                            <Icon name="crosshair" size={13} />
                            Выбрать
                          </button>
                        </div>
                      ) : (
                        <>
                          <div style={{ maxHeight: 180, overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: 2, border: '1px solid var(--line)', borderRadius: 8, padding: 4 }}>
                            {selectedElements.map((ei, i) => (
                              <div key={ei.selector} style={{
                                display: 'flex', alignItems: 'center', gap: 6,
                                padding: '6px 8px', borderRadius: 6,
                                background: 'var(--bg-subtle, #f8f8ff)',
                              }}>
                                <span style={{
                                  fontSize: 10, color: 'var(--ink-3)', fontFamily: 'monospace',
                                  flexShrink: 0, width: 24, textAlign: 'right' as const,
                                }}>
                                  #{i + 1}
                                </span>
                                <div style={{ flex: 1, minWidth: 0, display: 'flex', flexDirection: 'column', gap: 1 }}>
                                  <span style={{
                                    fontSize: 13, fontWeight: 500, color: 'var(--ink-1)',
                                    overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap',
                                  }} title={ei.label}>
                                    {ei.label}
                                  </span>
                                  <code style={{
                                    fontSize: 10, fontFamily: 'monospace', color: 'var(--ink-3)',
                                    overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap',
                                  }} title={ei.selector}>
                                    {ei.selector}
                                  </code>
                                </div>
                                <button
                                  style={{
                                    flexShrink: 0, width: 20, height: 20,
                                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                                    border: 'none', background: 'transparent', cursor: 'pointer',
                                    borderRadius: 4, color: 'var(--ink-3)', fontSize: 13,
                                  }}
                                  onClick={() => removeElement(ei.selector)}
                                  title="Удалить"
                                >
                                  ✕
                                </button>
                              </div>
                            ))}
                          </div>
                          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                            <span style={{ fontSize: 12, color: 'var(--ink-3)' }}>
                              Выбрано: {selectedElements.length}
                            </span>
                            <button className="btn" type="button" onClick={startPicker}>
                              <Icon name="crosshair" size={13} />
                              Добавить
                            </button>
                          </div>
                        </>
                      )}
                    </div>
                    <span className="field-help">
                      {selectedElements.length === 0
                        ? 'Нажмите «Выбрать», затем кликайте по проблемным местам на странице.'
                        : 'Нажмите «Добавить» чтобы выбрать ещё элементы, или ✕ чтобы удалить.'}
                    </span>
                  </div>

                  <div className="field">
                    <label className="field-lbl">Описание ошибки</label>
                    <textarea
                      value={description}
                      onChange={(e) => setDescription(e.target.value)}
                      className="textarea"
                      placeholder="Опишите, что пошло не так…"
                    />
                  </div>
                  {error && <p style={{ color: 'var(--danger)', fontSize: 13, margin: 0 }}>{error}</p>}
                </>
              )}
            </div>

            <div className="modal-foot">
              {!sent && (
                <>
                  <button className="btn" onClick={() => setOpen(false)}>Отмена</button>
                  <button
                    className="btn primary"
                    onClick={handleSubmit}
                    disabled={sending || !description.trim() || selectedElements.length === 0}
                  >
                    {sending ? 'Отправка…' : 'Отправить'}
                  </button>
                </>
              )}
            </div>
          </div>
        </div>,
        document.body
      )}
    </>
  )
}
