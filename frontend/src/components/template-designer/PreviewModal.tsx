import { EditorContent, useEditor } from '@tiptap/react'
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui'
import StarterKit from '@tiptap/starter-kit'
import { Table } from '@tiptap/extension-table'
import { TableRow } from '@tiptap/extension-table-row'
import { ExtendedTableCell, ExtendedTableHeader } from './extensions/TableCellExtension'
import { Strike } from '@tiptap/extension-strike'
import { Subscript } from '@tiptap/extension-subscript'
import { Superscript } from '@tiptap/extension-superscript'
import { Color } from '@tiptap/extension-color'
import { Highlight } from '@tiptap/extension-highlight'
import { FontFamily } from '@tiptap/extension-font-family'
import { TextStyle } from '@tiptap/extension-text-style'
import { TextAlign } from '@tiptap/extension-text-align'
import { Link } from '@tiptap/extension-link'
import { HorizontalRule } from '@tiptap/extension-horizontal-rule'
import { ParameterFieldNode } from './extensions/ParameterFieldNode'
import { ConditionalBlockNode } from './extensions/ConditionalBlockNode'
import { NumberedClauseNode } from './extensions/NumberedClauseNode'
import { ConditionalInlineNode } from './extensions/ConditionalInlineNode'
import { ClauseRefNode } from './extensions/ClauseRefNode'
import { IndentExtension } from './extensions/IndentExtension'
import { FontSizeExtension } from './extensions/FontSizeExtension'
import { LineSpacingExtension } from './extensions/LineSpacingExtension'
import { PageBreakNode } from './extensions/PageBreakNode'
import type { JSONContent } from '@tiptap/core'
import type { DocumentFormatting } from '../../types/template'
import { useEffect } from 'react'
import './TemplateDesigner.css'

const PREVIEW_EXTENSIONS = [
  StarterKit.configure({ link: false }),
  Table.configure({ resizable: false }),
  TableRow,
  ExtendedTableCell,
  ExtendedTableHeader,
  Strike,
  Subscript,
  Superscript,
  TextStyle,
  Color,
  Highlight.configure({ multicolor: true }),
  FontFamily,
  FontSizeExtension,
  LineSpacingExtension,
  TextAlign.configure({ types: ['heading', 'paragraph', 'numberedClause'] }),
  Link.configure({ openOnClick: true }),
  HorizontalRule,
  IndentExtension,
  PageBreakNode,
  ParameterFieldNode,
  ConditionalBlockNode,
  NumberedClauseNode,
  ConditionalInlineNode,
  ClauseRefNode,
]

interface Props {
  open: boolean
  onClose: () => void
  content: JSONContent
  formatting: DocumentFormatting
}

const PAGE_DIMENSIONS: Record<string, { w: number; h: number }> = {
  A4: { w: 210, h: 297 },
  A3: { w: 297, h: 420 },
  Letter: { w: 216, h: 279 },
}

export default function PreviewModal({ open, onClose, content, formatting }: Props) {
  const editor = useEditor({
    extensions: PREVIEW_EXTENSIONS,
    editable: false,
    content,
  })

  // Sync content when modal opens
  useEffect(() => {
    if (editor && open) {
      editor.commands.setContent(content)
    }
  }, [open, content, editor])

  const size = PAGE_DIMENSIONS[formatting.pageSize] ?? PAGE_DIMENSIONS['A4']
  const landscape = formatting.orientation === 'landscape'
  const w = landscape ? size.h : size.w
  const h = landscape ? size.w : size.h

  const pageStyle: React.CSSProperties = {
    '--page-width': `${w}mm`,
    '--page-min-height': `${h}mm`,
    '--margin-top': `${formatting.margins.top}mm`,
    '--margin-right': `${formatting.margins.right}mm`,
    '--margin-bottom': `${formatting.margins.bottom}mm`,
    '--margin-left': `${formatting.margins.left}mm`,
    '--default-font': formatting.defaultFont,
    '--default-font-size': `${formatting.defaultFontSize}pt`,
    '--default-line-height': String(formatting.lineSpacing),
    cursor: 'default',
  } as React.CSSProperties

  const dialogWidth = `${w + 120}mm`

  return (
    <Dialog open={open} onOpenChange={(isOpen) => !isOpen && onClose()}>
      <DialogContent 
        className="sm:max-w-none p-0"
        style={{ width: dialogWidth, maxWidth: '95vw' }}
      >
        <DialogHeader className="px-6 pt-4 pb-2">
          <DialogTitle>Предпросмотр шаблона</DialogTitle>
        </DialogHeader>
        <div className="px-6 pb-6 overflow-y-auto" style={{ maxHeight: '75vh', background: '#f5f5f5' }}>
          <div className="tiptap-page" style={pageStyle}>
            <EditorContent editor={editor} />
          </div>
        </div>
      </DialogContent>
    </Dialog>
  )
}
