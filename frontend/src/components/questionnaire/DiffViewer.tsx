import { useEffect, useState } from 'react'
import { EditorContent, useEditor } from '@tiptap/react'
import type { JSONContent } from '@tiptap/core'
import { Loader2 } from 'lucide-react'
import { documentsApi } from '../../api/documentsApi'
import type { RevisionDiff } from '../../types/document'
import { buildWorkspaceExtensions } from './extensions/buildWorkspaceExtensions'
import { structuralDiff } from '../../utils/structuralDiff'
import { formattingToCssVars } from '../../utils/formattingCssVars'
import type { DocumentFormatting } from '../../types/template'
import '../template-designer/TemplateDesigner.css'

type DiffBaseline = 'previous' | 'current'

interface DocumentDiffViewProps {
  documentId: string
  revisionNumber: number
  previousRevisionNumber: number | null
  onClose: () => void
  formatting?: DocumentFormatting | null
}

/**
 * Inline revision diff rendered in the document pane. The diff is computed by
 * `structuralDiff` and rendered in a read-only TipTap editor with diff
 * extensions enabled.
 */
export function DocumentDiffView({
  documentId,
  revisionNumber,
  previousRevisionNumber,
  onClose,
  formatting = null,
}: DocumentDiffViewProps) {
  const hasPrevious = previousRevisionNumber != null
  const [baseline, setBaseline] = useState<DiffBaseline>(hasPrevious ? 'previous' : 'current')
  const [loading, setLoading] = useState(true)
  const [diffData, setDiffData] = useState<RevisionDiff | null>(null)
  const [error, setError] = useState<string | null>(null)

  const editor = useEditor({
    extensions: buildWorkspaceExtensions({ diff: true }),
    editable: false,
    content: { type: 'doc', content: [] },
  })

  useEffect(() => {
    setBaseline(hasPrevious ? 'previous' : 'current')
  }, [revisionNumber, hasPrevious])

  const compareTo = baseline === 'previous' && previousRevisionNumber != null
    ? previousRevisionNumber
    : undefined

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setError(null)
    setDiffData(null)
    documentsApi
      .getRevisionDiff(documentId, revisionNumber, compareTo)
      .then((data) => {
        if (cancelled) return
        setDiffData(data)
        setLoading(false)
      })
      .catch(() => {
        if (cancelled) return
        setDiffData(null)
        setError('Не удалось загрузить изменения')
        setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [documentId, revisionNumber, compareTo])

  useEffect(() => {
    if (!editor) return
    if (!diffData) {
      editor.commands.setContent({ type: 'doc', content: [] })
      return
    }
    const content = structuralDiff(
      diffData.targetBody as JSONContent | null,
      diffData.compareBody as JSONContent | null,
    )
    editor.commands.setContent(content)
  }, [editor, diffData])

  const baselineLabel = baseline === 'previous' && previousRevisionNumber != null
    ? `Сравнение с редакцией ${previousRevisionNumber}`
    : 'Сравнение с текущим состоянием'

  const pageStyle = formatting ? formattingToCssVars(formatting) : {}

  return (
    <div style={{ flex: 1, display: 'flex', flexDirection: 'column', minHeight: 0 }}>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 10,
          padding: '8px 16px',
          background: 'var(--bg-soft)',
          borderBottom: '1px solid var(--line)',
          flexShrink: 0,
        }}
      >
        <div style={{ display: 'flex', flexDirection: 'column', minWidth: 0 }}>
          <span style={{ fontSize: 12.5, fontWeight: 550 }}>Изменения в редакции {revisionNumber}</span>
          <span style={{ fontSize: 11, color: 'var(--ink-3)' }}>{baselineLabel}</span>
        </div>
        <div style={{ marginLeft: 'auto', display: 'flex', alignItems: 'center', gap: 6 }}>
          <div
            style={{ display: 'flex', gap: 6 }}
            role="group"
            aria-label="Базовая редакция для сравнения"
          >
            <button
              type="button"
              className={'btn xs' + (baseline === 'previous' ? ' primary' : '')}
              aria-pressed={baseline === 'previous'}
              disabled={!hasPrevious}
              onClick={() => setBaseline('previous')}
            >
              С предыдущей редакцией
            </button>
            <button
              type="button"
              className={'btn xs' + (baseline === 'current' ? ' primary' : '')}
              aria-pressed={baseline === 'current'}
              onClick={() => setBaseline('current')}
            >
              С текущим состоянием
            </button>
          </div>
          <div style={{ width: 1, height: 18, background: 'var(--line)' }} />
          <button type="button" className="btn xs ghost" onClick={onClose}>
            Вернуться к документу
          </button>
        </div>
      </div>

      <div className="editor-canvas">
        <div className="tiptap-page" style={pageStyle}>
          {loading ? (
            <div className="flex items-center justify-center py-12">
              <Loader2 className="size-6 animate-spin text-muted-foreground" />
              <span className="ml-2 text-muted-foreground">Загрузка...</span>
            </div>
          ) : error ? (
            <div className="text-center py-8 text-destructive">{error}</div>
          ) : (
            <EditorContent editor={editor} />
          )}
        </div>
      </div>
    </div>
  )
}
