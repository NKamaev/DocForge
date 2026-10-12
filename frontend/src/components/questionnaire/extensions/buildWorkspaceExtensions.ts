import type { Extensions } from '@tiptap/core'
import type { Doc as YDoc } from 'yjs'
import StarterKit from '@tiptap/starter-kit'
import { Collaboration } from '@tiptap/extension-collaboration'
import { Table } from '@tiptap/extension-table'
import { TableRow } from '@tiptap/extension-table-row'
import { TableCell } from '@tiptap/extension-table-cell'
import { TableHeader } from '@tiptap/extension-table-header'
import { Underline } from '@tiptap/extension-underline'
import { TextStyle } from '@tiptap/extension-text-style'
import { Color } from '@tiptap/extension-color'
import { FontFamily } from '@tiptap/extension-font-family'
import { TextAlign } from '@tiptap/extension-text-align'
import { Highlight } from '@tiptap/extension-highlight'
import { Subscript } from '@tiptap/extension-subscript'
import { Superscript } from '@tiptap/extension-superscript'
import { Link } from '@tiptap/extension-link'
import { FontSize } from '../../document-editor/FontSize'
import { LineSpacingExtension } from '../../template-designer/extensions/LineSpacingExtension'
import { IndentExtension } from '../../template-designer/extensions/IndentExtension'
import { ParagraphSpacingExtension } from '../../template-designer/extensions/ParagraphSpacingExtension'
import { PageBreakNode } from '../../template-designer/extensions/PageBreakNode'
import { ClauseRefNode } from '../../template-designer/extensions/ClauseRefNode'
import { WorkspaceParameterFieldNode } from '../WorkspaceParameterFieldNode'
import { WorkspaceNumberedClauseNode } from '../WorkspaceNumberedClauseNode'
import { WorkspacePhrasingsRefNode } from '../WorkspacePhrasingsRefNode'
import { WorkspaceConditionalBlockNode } from '../WorkspaceConditionalBlockNode'
import { WorkspaceConditionalInlineNode } from '../WorkspaceConditionalInlineNode'
import {
  DiffBlockNode,
  DiffDeleteMark,
  DiffInsertMark,
} from './DiffExtensions'

export interface WorkspaceExtensionOptions {
  /**
   * When provided, wires the Yjs `Collaboration` extension against this
   * document. Omit it for read-only views that do not need collaborative sync.
   */
  collaboration?: YDoc | null
  /**
   * When `true`, appends the diff marks (`diffInsert`, `diffDelete`) and the
   * `diffBlock` node used by the read-only revision diff view.
   */
  diff?: boolean
}

/**
 * Single source of truth for the TipTap extension set used by the document
 * workspace and the read-only diff view.
 *
 * Calling this with no arguments preserves the historic workspace schema minus
 * collaboration (which is opt-in), so callers that need the live editor must
 * pass `{ collaboration: ydoc }`.
 */
export function buildWorkspaceExtensions(
  options: WorkspaceExtensionOptions = {},
): Extensions {
  const extensions: Extensions = [
    StarterKit.configure({ undoRedo: false, link: false, underline: false }),
  ]

  if (options.collaboration != null) {
    extensions.push(
      Collaboration.configure({ document: options.collaboration }),
    )
  }

  extensions.push(
    Underline,
    TextStyle,
    Color,
    FontFamily,
    FontSize,
    LineSpacingExtension,
    IndentExtension,
    ParagraphSpacingExtension,
    TextAlign.configure({ types: ['heading', 'paragraph', 'numberedClause'] }),
    Highlight.configure({ multicolor: true }),
    Subscript,
    Superscript,
    Link.configure({ openOnClick: true }),
    Table.configure({ resizable: false }),
    TableRow,
    TableCell,
    TableHeader,
    PageBreakNode,
    WorkspaceParameterFieldNode,
    WorkspaceNumberedClauseNode,
    WorkspacePhrasingsRefNode,
    WorkspaceConditionalBlockNode,
    WorkspaceConditionalInlineNode,
    ClauseRefNode,
  )

  if (options.diff === true) {
    extensions.push(DiffInsertMark, DiffDeleteMark, DiffBlockNode)
  }

  return extensions
}
