import React, { useEffect, useRef } from 'react'
import { EditorContent, useEditor } from '@tiptap/react'
import type { JSONContent } from '@tiptap/core'
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
import { FontSize } from '../document-editor/FontSize'
import { LineSpacingExtension } from '../template-designer/extensions/LineSpacingExtension'
import { IndentExtension } from '../template-designer/extensions/IndentExtension'
import { ParagraphSpacingExtension } from '../template-designer/extensions/ParagraphSpacingExtension'
import { PageBreakNode } from '../template-designer/extensions/PageBreakNode'
import { WorkspaceParameterFieldNode } from './WorkspaceParameterFieldNode'
import type { WorkspaceParamStorage } from './WorkspaceParameterFieldNode'
import { WorkspaceNumberedClauseNode } from './WorkspaceNumberedClauseNode'
import type { WorkspaceNumberedClauseStorage } from './WorkspaceNumberedClauseNode'
import { WorkspacePhrasingsRefNode } from './WorkspacePhrasingsRefNode'
import type { PhrasingsRefStorage } from './WorkspacePhrasingsRefNode'
import { WorkspaceConditionalBlockNode } from './WorkspaceConditionalBlockNode'
import { WorkspaceConditionalInlineNode } from './WorkspaceConditionalInlineNode'
import { ClauseRefNode } from '../template-designer/extensions/ClauseRefNode'
import type { DocumentFormatting, Parameter, Phrasing } from '../../types/template'
import type { Questionnaire } from '../../types/document'
import { renderPreview, evaluateSimple } from '../../utils/previewRenderer'
import EditorToolbar from '../template-designer/EditorToolbar'
import '../template-designer/TemplateDesigner.css'

interface Props {
  ydoc: YDoc
  templateBody: Record<string, unknown> | null
  answers: Record<string, unknown>
  parameters: Parameter[]
  phrasings: Phrasing[]
  formatting: DocumentFormatting | null
  questionnaire: Questionnaire | null
  highlightedParam: string | null
  onAnswerChange: (paramCode: string, value: unknown) => void
  onParamFocus: (paramCode: string) => void
  initialBody?: unknown
  onBodyChange?: (body: JSONContent) => void
  readOnly?: boolean
  restoreSeedKey?: number
}

export default function DocumentWorkspace({
  ydoc,
  templateBody,
  answers,
  parameters,
  phrasings,
  formatting,
  questionnaire,
  highlightedParam,
  onAnswerChange,
  onParamFocus,
  initialBody,
  onBodyChange,
  readOnly = false,
  restoreSeedKey = 0,
}: Props) {
  // Keep callbacks in refs so storage always has fresh versions
  const onAnswerChangeRef = useRef(onAnswerChange)
  const onParamFocusRef   = useRef(onParamFocus)
  const onBodyChangeRef   = useRef(onBodyChange)
  useEffect(() => { onAnswerChangeRef.current = onAnswerChange }, [onAnswerChange])
  useEffect(() => { onParamFocusRef.current   = onParamFocus   }, [onParamFocus])
  useEffect(() => { onBodyChangeRef.current   = onBodyChange   }, [onBodyChange])

  const editor = useEditor({
    extensions: [
      StarterKit.configure({ undoRedo: false, link: false, underline: false }),
      Collaboration.configure({ document: ydoc }),
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
    ],
    editable: !readOnly,
    onUpdate: ({ editor: e }) => {
      onBodyChangeRef.current?.(e.getJSON())
    },
  }, [ydoc]) // eslint-disable-line react-hooks/exhaustive-deps

  // ── Sync readOnly state into editor after mount ───────────────────────────
  useEffect(() => {
    if (!editor) return
    editor.setEditable(!readOnly)
  }, [editor, readOnly])

  // ── Wire storage callbacks once editor is ready ──────────────────────────
  useEffect(() => {
    if (!editor) return
    const s = storage(editor)
    s.onAnswerChange = (code, val) => onAnswerChangeRef.current(code, val)
    s.onParamFocus   = (code)      => onParamFocusRef.current(code)
    s.questionnaire  = questionnaire
  }, [editor]) // eslint-disable-line react-hooks/exhaustive-deps

  // ── Sync questionnaire into storage ──────────────────────────────────────
  useEffect(() => {
    if (!editor) return
    storage(editor).questionnaire = questionnaire
  }, [editor, questionnaire])

  // ── Push answers + phrasings into storage and trigger re-render ───────────
  useEffect(() => {
    if (!editor) return
    const s = storage(editor)
    s.answers    = answers
    s.parameters = parameters
    // Phrasings storage
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const ps = (editor as any).storage.phrasingsRef as PhrasingsRefStorage | undefined
    if (ps) ps.phrasings = phrasings
    updateHiddenPositions(editor, answers)
    // 'answers-updated' meta signals node views to re-read storage.
    editor.view.dispatch(editor.view.state.tr.setMeta('answers-updated', true))
  }, [editor, answers, parameters, phrasings])

  // ── Sync highlighted param into storage ───────────────────────────────────
  useEffect(() => {
    if (!editor) return
    storage(editor).highlightedParam = highlightedParam
    editor.view.dispatch(editor.view.state.tr)
  }, [editor, highlightedParam])

  // ── Seed Yjs with REST content if Yjs doc is empty ───────────────────────
  // Waits 400ms so the WebSocket sync has time to deliver server Yjs state.
  // If the server already has Yjs state the fragment will be non-empty and
  // we skip seeding — Yjs content wins over REST / rendered preview.
  const seededRef = useRef(false)
  useEffect(() => { seededRef.current = false }, [ydoc, restoreSeedKey])
  useEffect(() => {
    if (seededRef.current || !editor) return
    if (initialBody == null && !templateBody) return

    const snapAnswers = answers
    const snapParams = parameters
    const snapPhrasings = phrasings
    const timer = setTimeout(() => {
      if (seededRef.current) return
      seededRef.current = true
      if (ydoc.getXmlFragment('default').length === 0) {
        if (initialBody != null) {
          editor.commands.setContent(initialBody as JSONContent)
        } else if (templateBody) {
          editor.commands.setContent(
            renderPreview(templateBody, snapAnswers, snapParams, {
              preserveConditionalBlocks: true,
              phrasings: snapPhrasings,
            })
          )
        }
      }
    }, 400)

    return () => clearTimeout(timer)
  }, [editor, ydoc, initialBody, templateBody]) // eslint-disable-line react-hooks/exhaustive-deps

  if (!templateBody && initialBody == null) {
    return (
      <div className="editor-canvas" style={{ display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
        <span style={{ color: 'var(--ink-3)', fontSize: 13 }}>Шаблон не содержит тела документа</span>
      </div>
    )
  }

  const pageStyle = formatting ? formattingToCssVars(formatting) : {}

  return (
    <div style={{ flex: 1, display: 'flex', flexDirection: 'column', minHeight: 0 }}>
      {editor && !readOnly && <EditorToolbar editor={editor} parameters={parameters} formatting={formatting ?? undefined} />}
      <div className="editor-canvas">
        <div className="tiptap-page" style={pageStyle}>
          <EditorContent editor={editor} />
        </div>
      </div>
    </div>
  )
}

// Mirror of TemplateDesigner's formattingToCssVars so the fill page renders
// with the same default paragraph styles as the template designer.
function formattingToCssVars(f: DocumentFormatting): React.CSSProperties {
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
  } as React.CSSProperties
}

// Typed storage accessor
function storage(editor: ReturnType<typeof useEditor>): WorkspaceParamStorage {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  return (editor as any).storage.parameterField as WorkspaceParamStorage
}

// Recompute which numberedClause positions should be hidden (for autonumbering).
// Two sources:
//   1. Numbered clauses inside hidden conditionalBlock/conditionalInline nodes
//      (defensive — after renderPreview these are expanded, but kept for safety).
//   2. Numbered clauses with own condition that is false given current answers.
function updateHiddenPositions(
  editor: ReturnType<typeof useEditor>,
  answers: Record<string, unknown>,
): void {
  if (!editor) return
  const hidden = new Set<number>()

  editor.view.state.doc.descendants((node, pos) => {
    // Source 1: conditional wrappers
    if (node.type.name === 'conditionalBlock' || node.type.name === 'conditionalInline') {
      const cond = node.attrs?.condition as { type: string; expression: string } | undefined
      let show = true
      if (cond?.type === 'simple') {
        show = evaluateSimple(cond.expression, answers)
      }
      if (!show) {
        node.descendants((child, childRelPos) => {
          if (child.type.name === 'numberedClause') {
            hidden.add(pos + 1 + childRelPos)
          }
        })
        return false
      }
    }

    // Source 2: numbered clause with its own condition attribute
    if (node.type.name === 'numberedClause') {
      const cond = node.attrs?.condition as { type: string; expression: string } | null
      if (cond?.type === 'simple') {
        const show = evaluateSimple(cond.expression, answers)
        if (!show) hidden.add(pos)
      }
    }
  })

  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const s = (editor as any).storage.numberedClause as WorkspaceNumberedClauseStorage | undefined
  if (s) s.hiddenPositions = hidden
}
