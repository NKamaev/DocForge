import { TableCell } from '@tiptap/extension-table-cell'
import { TableHeader } from '@tiptap/extension-table-header'

type TextOrientation = 'horizontal' | 'rotate-90' | 'rotate-180' | 'rotate-270' | null

function buildCellStyle(attrs: Record<string, unknown>): string {
  const parts: string[] = []

  const va = attrs.verticalAlign as string | null
  if (va) parts.push(`vertical-align: ${va}`)

  const bg = attrs.backgroundColor as string | null
  if (bg) parts.push(`background-color: ${bg}`)

  const orient = attrs.textOrientation as TextOrientation
  if (orient === 'rotate-90') parts.push('writing-mode: vertical-rl')
  else if (orient === 'rotate-270') parts.push('writing-mode: vertical-lr')
  else if (orient === 'rotate-180') parts.push('transform: rotate(180deg)')

  for (const side of ['borderTop', 'borderRight', 'borderBottom', 'borderLeft'] as const) {
    const val = attrs[side] as string | null
    const cssProp = side.replace(/([A-Z])/g, '-$1').toLowerCase()
    if (val) parts.push(`${cssProp}: ${val}`)
  }

  const mh = attrs.minHeight as number | null
  if (mh) parts.push(`height: ${mh}px`)

  return parts.join('; ')
}

function parseStyleProp(style: string, prop: string): string | null {
  const match = style.match(new RegExp(`(?:^|;)\\s*${prop}\\s*:\\s*([^;]+)`, 'i'))
  return match ? match[1].trim() : null
}

function orientationFromStyle(style: string): TextOrientation {
  const wm = parseStyleProp(style, 'writing-mode')
  const tr = parseStyleProp(style, 'transform')
  if (wm === 'vertical-rl') return 'rotate-90'
  if (wm === 'vertical-lr') return 'rotate-270'
  if (tr && tr.includes('rotate(180deg)')) return 'rotate-180'
  return null
}

const extraAttrs = {
  verticalAlign: {
    default: null as string | null,
    parseHTML: (el: HTMLElement) => parseStyleProp(el.getAttribute('style') ?? '', 'vertical-align'),
    renderHTML: () => ({}), // handled in combined style below
  },
  backgroundColor: {
    default: null as string | null,
    parseHTML: (el: HTMLElement) => parseStyleProp(el.getAttribute('style') ?? '', 'background-color'),
    renderHTML: () => ({}),
  },
  textOrientation: {
    default: null as TextOrientation,
    parseHTML: (el: HTMLElement) => orientationFromStyle(el.getAttribute('style') ?? ''),
    renderHTML: () => ({}),
  },
  borderTop: {
    default: null as string | null,
    parseHTML: (el: HTMLElement) => parseStyleProp(el.getAttribute('style') ?? '', 'border-top'),
    renderHTML: () => ({}),
  },
  borderRight: {
    default: null as string | null,
    parseHTML: (el: HTMLElement) => parseStyleProp(el.getAttribute('style') ?? '', 'border-right'),
    renderHTML: () => ({}),
  },
  borderBottom: {
    default: null as string | null,
    parseHTML: (el: HTMLElement) => parseStyleProp(el.getAttribute('style') ?? '', 'border-bottom'),
    renderHTML: () => ({}),
  },
  borderLeft: {
    default: null as string | null,
    parseHTML: (el: HTMLElement) => parseStyleProp(el.getAttribute('style') ?? '', 'border-left'),
    renderHTML: () => ({}),
  },
  minHeight: {
    default: null as number | null,
    parseHTML: (el: HTMLElement) => {
      // parse both 'height' (new) and 'min-height' (legacy) for backward compat
      const style = el.getAttribute('style') ?? ''
      const v = parseStyleProp(style, 'height') ?? parseStyleProp(style, 'min-height')
      return v ? parseFloat(v) : null
    },
    renderHTML: () => ({}),
  },
}

export const ExtendedTableCell = TableCell.extend({
  addAttributes() {
    return {
      ...this.parent?.(),
      ...extraAttrs,
    }
  },
  renderHTML({ node, HTMLAttributes }) {
    const { style: existingStyle, ...rest } = HTMLAttributes
    const customStyle = buildCellStyle(node.attrs as Record<string, unknown>)
    const combined = [existingStyle, customStyle].filter(Boolean).join('; ')
    return ['td', { ...rest, style: combined || undefined }, 0]
  },
})

export const ExtendedTableHeader = TableHeader.extend({
  addAttributes() {
    return {
      ...this.parent?.(),
      ...extraAttrs,
    }
  },
  renderHTML({ node, HTMLAttributes }) {
    const { style: existingStyle, ...rest } = HTMLAttributes
    const customStyle = buildCellStyle(node.attrs as Record<string, unknown>)
    const combined = [existingStyle, customStyle].filter(Boolean).join('; ')
    return ['th', { ...rest, style: combined || undefined }, 0]
  },
})
