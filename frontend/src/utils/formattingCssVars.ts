import type { CSSProperties } from 'react'
import type { DocumentFormatting } from '../types/template'

/**
 * Maps template `formatting` to the CSS custom properties consumed by
 * `.tiptap-page` (page size, margins, default font / size / line-height).
 *
 * Shared by the live workspace editor (`DocumentWorkspace`) and the read-only
 * revision diff view so both render with identical page geometry.
 */
export function formattingToCssVars(f: DocumentFormatting): CSSProperties {
  const PAGE_W: Record<string, number> = { A4: 210, A3: 297, Letter: 216 }
  const PAGE_H: Record<string, number> = { A4: 297, A3: 420, Letter: 279 }
  const landscape = f.orientation === 'landscape'
  const w = landscape ? (PAGE_H[f.pageSize] ?? 297) : (PAGE_W[f.pageSize] ?? 210)
  const h = landscape ? (PAGE_W[f.pageSize] ?? 210) : (PAGE_H[f.pageSize] ?? 297)
  return {
    '--page-width': `${w}mm`,
    '--page-min-height': `${h}mm`,
    '--margin-top':    `${f.margins.top}mm`,
    '--margin-right':  `${f.margins.right}mm`,
    '--margin-bottom': `${f.margins.bottom}mm`,
    '--margin-left':   `${f.margins.left}mm`,
    '--default-font':      f.defaultFont,
    '--default-font-size': `${f.defaultFontSize}pt`,
    '--default-line-height': String(f.lineSpacing),
    '--default-space-before':      f.paragraphDefaults?.spaceBefore     ? `${f.paragraphDefaults.spaceBefore}pt`     : '0',
    '--default-space-after':       f.paragraphDefaults?.spaceAfter      ? `${f.paragraphDefaults.spaceAfter}pt`      : '0',
    '--default-margin-left':       f.paragraphDefaults?.marginLeft      ? `${f.paragraphDefaults.marginLeft}mm`      : '0',
    '--default-margin-right':      f.paragraphDefaults?.marginRight     ? `${f.paragraphDefaults.marginRight}mm`     : '0',
    '--default-first-line-indent': f.paragraphDefaults?.firstLineIndent ? `${f.paragraphDefaults.firstLineIndent}mm` : '0',
  } as CSSProperties
}
