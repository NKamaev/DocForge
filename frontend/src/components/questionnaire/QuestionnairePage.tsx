import { useCallback, useEffect, useRef, useState } from 'react'
import * as Y from 'yjs'
import type { JSONContent } from '@tiptap/core'
import { useParams } from 'react-router-dom'
import { Alert, List, Spin, notification } from 'antd'
import { FileWordOutlined, FilePdfOutlined, RollbackOutlined } from '@ant-design/icons'
import { Icon } from '../Icon'
import { documentsApi } from '../../api/documentsApi'
import { templatesApi } from '../../api/templatesApi'
import type { Questionnaire, QuestionnaireQuestion, RevisionMeta } from '../../types/document'
import type { DocumentFormatting, Parameter, Phrasing } from '../../types/template'
import { useCollaboration, type CollaborationUser } from '../../hooks/useCollaboration'
import { useAuthStore } from '../../store/authStore'
import { useMyDocumentRole } from '../../hooks/useMyRole'
import ParticipantsPanel from '../document-editor/ParticipantsPanel'
import QuestionRenderer from './QuestionRenderer'
import DocumentWorkspace from './DocumentWorkspace'

const DEBOUNCE_MS = 500

export default function QuestionnairePage() {
  const { id } = useParams<{ id: string }>()
  const currentUser = useAuthStore(s => s.user)
  const { ydoc, isConnected, connectedUsers, yAnswers } = useCollaboration(id ?? '')
  const myDocRole = useMyDocumentRole(id ?? null)
  const isReadOnly = myDocRole === 'VIEWER'

  const [questionnaire, setQuestionnaire] = useState<Questionnaire | null>(null)
  const [answers, setAnswers] = useState<Record<string, unknown>>({})
  const [currentPage, setCurrentPage] = useState(0)
  const [loading, setLoading] = useState(true)
  const [saveError, setSaveError] = useState<string | null>(null)
  const [exportingDocx, setExportingDocx] = useState(false)
  const [exportingPdf, setExportingPdf] = useState(false)

  const [historyOpen, setHistoryOpen] = useState(false)
  const [revisions, setRevisions] = useState<RevisionMeta[]>([])
  const [loadingRevisions, setLoadingRevisions] = useState(false)

  const [templateBody, setTemplateBody] = useState<Record<string, unknown> | null>(null)
  const [docCurrentBody, setDocCurrentBody] = useState<unknown>(null)
  const [parameters, setParameters] = useState<Parameter[]>([])
  const [phrasings, setPhrasings] = useState<Phrasing[]>([])
  const [formatting, setFormatting] = useState<DocumentFormatting | null>(null)

  // Highlighted param (document → ОЛ navigation)
  const [highlightedParam, setHighlightedParam] = useState<string | null>(null)

  const answersRef     = useRef<Record<string, unknown>>({})
  const editorBodyRef  = useRef<JSONContent | null>(null)
  const timerRef       = useRef<ReturnType<typeof setTimeout> | null>(null)
  const highlightTimer = useRef<ReturnType<typeof setTimeout> | null>(null)
  const questionRefs   = useRef<Map<string, HTMLDivElement>>(new Map())

  // ── Load data ───────────────────────────────────────────────────────────────
  useEffect(() => {
    if (!id) return

    const loadQ = documentsApi.getQuestionnaire(id).then(q => {
      setQuestionnaire(q)
      const initial: Record<string, unknown> = {}
      q.pages.forEach(p =>
        p.questions.forEach(qu => {
          if (qu.currentValue !== undefined && qu.currentValue !== null)
            initial[qu.parameterCode] = qu.currentValue
        })
      )
      setAnswers(initial)
      answersRef.current = initial
      if (yAnswers.size === 0) {
        Object.entries(initial).forEach(([k, v]) => yAnswers.set(k, v))
      }
    })

    const loadT = documentsApi.getById(id)
      .then(async doc => {
        if (doc.currentBody != null) setDocCurrentBody(doc.currentBody)
        // Use the immutable snapshot fixed at document creation time.
        // Fall back to the live template only if the document pre-dates versioning.
        const tmpl = doc.templateVersionNumber != null
          ? await templatesApi.getSnapshot(doc.templateId, doc.templateVersionNumber)
          : await templatesApi.getTemplate(doc.templateId)
        if (tmpl.body)       setTemplateBody(tmpl.body)
        if (tmpl.parameters) setParameters(tmpl.parameters)
        if (tmpl.formatting) setFormatting(tmpl.formatting)
        setPhrasings(tmpl.phrasings ?? [])
      })

    Promise.all([loadQ, loadT])
      .catch(() => notification.error({ message: 'Не удалось загрузить данные' }))
      .finally(() => setLoading(false))
  }, [id])

  // Track editor body in ref for pre-export updateBody call (no debounced REST save — body lives in Yjs)
  const handleBodyChange = useCallback((body: JSONContent) => {
    editorBodyRef.current = body
  }, [])

  // ── Debounced save ──────────────────────────────────────────────────────────
  const scheduleSave = useCallback(() => {
    if (!id) return
    if (timerRef.current) clearTimeout(timerRef.current)
    timerRef.current = setTimeout(async () => {
      try {
        const res = await documentsApi.saveAnswers(id, { answers: answersRef.current })
        setQuestionnaire(res.questionnaire)
        setSaveError(null)
        documentsApi.saveRevision(id, 'Автосохранение').catch(e => console.warn('saveRevision failed', e))
      } catch {
        setSaveError('Ошибка автосохранения')
      }
    }, DEBOUNCE_MS)
  }, [id])

  const handleAnswerChange = useCallback((paramCode: string, value: unknown) => {
    const next = { ...answersRef.current, [paramCode]: value }
    answersRef.current = next
    setAnswers(next)
    yAnswers.set(paramCode, value)
    scheduleSave()
  }, [scheduleSave, yAnswers])

  /** For directory fields: set multiple answers at once */
  const handleMultiAnswerChange = useCallback((changes: Record<string, unknown>) => {
    const next = { ...answersRef.current, ...changes }
    answersRef.current = next
    setAnswers(next)
    Object.entries(changes).forEach(([k, v]) => yAnswers.set(k, v))
    scheduleSave()
  }, [scheduleSave, yAnswers])

  // ── Yjs remote answers sync ─────────────────────────────────────────────────
  useEffect(() => {
    const handler = (event: Y.YMapEvent<unknown>) => {
      if (event.transaction.local) return
      const remote: Record<string, unknown> = {}
      event.changes.keys.forEach((_, key) => {
        remote[key] = yAnswers.get(key)
      })
      const next = { ...answersRef.current, ...remote }
      answersRef.current = next
      setAnswers(next)
      if (id) {
        documentsApi.getQuestionnaire(id)
          .then(q => setQuestionnaire(q))
          .catch(() => {})
      }
    }
    yAnswers.observe(handler)
    return () => yAnswers.unobserve(handler)
  }, [yAnswers, id])

  // ── Revision history ─────────────────────────────────────────────────────
  const openHistory = useCallback(async () => {
    if (!id) return
    setHistoryOpen(true)
    setLoadingRevisions(true)
    try {
      const data = await documentsApi.listRevisions(id)
      setRevisions(data)
    } catch {
      notification.error({ message: 'Не удалось загрузить историю ревизий' })
    } finally {
      setLoadingRevisions(false)
    }
  }, [id])

  const restoreRevision = useCallback(async (revNum: number) => {
    if (!id) return
    try {
      await documentsApi.restoreRevision(id, revNum)
      window.location.reload()
    } catch {
      notification.error({ message: 'Ошибка восстановления ревизии' })
    }
  }, [id])

  // ── Document → ОЛ: click on parameter ──────────────────────────────────────
  const handleParamFocus = useCallback((paramCode: string) => {
    if (!questionnaire) return

    const pages = questionnaire.pages ?? []
    const pageIdx = pages.findIndex(p =>
      p.questions.some(q => q.parameterCode === paramCode)
    )
    const needsSwitch = pageIdx !== -1 && pageIdx !== currentPage
    if (needsSwitch) setCurrentPage(pageIdx)

    if (highlightTimer.current) clearTimeout(highlightTimer.current)
    setHighlightedParam(paramCode)

    setTimeout(() => {
      questionRefs.current.get(paramCode)?.scrollIntoView({ behavior: 'smooth', block: 'center' })
    }, needsSwitch ? 120 : 0)

    highlightTimer.current = setTimeout(() => setHighlightedParam(null), 2500)
  }, [questionnaire, currentPage])

  // ── Render ──────────────────────────────────────────────────────────────────
  if (loading) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', padding: 64 }}>
        <Spin size="large" />
      </div>
    )
  }

  if (!questionnaire) {
    return <Alert type="error" message="Опросный лист не найден" style={{ margin: 24 }} />
  }

  const pages = questionnaire.pages ?? []
  const page  = pages[currentPage]
  const isFirst = currentPage === 0
  const isLast  = currentPage === pages.length - 1

  const visibleQuestions: QuestionnaireQuestion[] = (page?.questions ?? [])
    .filter(q => q.visible)
    .sort((a, b) => a.order - b.order)

  const filledCount = Object.values(answers).filter(v => v !== null && v !== '' && v !== undefined).length
  const totalCount  = pages.flatMap(p => p.questions).filter(q => q.visible).length
  const progress    = totalCount > 0 ? Math.round(filledCount / totalCount * 100) : 0

  return (
    <div className="workspace">
      {/* ── Left: questionnaire panel ── */}
      <div className="q-panel">
        <div className="q-head">
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
            <button className="btn xs ghost" onClick={() => window.history.back()}>
              <Icon name="chev-left" size={12} /> Документы
            </button>
            <span className="tabular muted" style={{ fontSize: 11 }}>{filledCount} / {totalCount}</span>
          </div>
          <h3>{questionnaire.title || 'Опросный лист'}</h3>
          <div className="q-progress"><div style={{ width: progress + '%' }} /></div>
          {pages.length > 1 && (
            <div className="steps" style={{ marginTop: 12 }}>
              {pages.map((p, i) => (
                <div
                  key={i}
                  className={'step' + (i === currentPage ? ' active' : i < currentPage ? ' done' : '')}
                  onClick={() => setCurrentPage(i)}
                >
                  <div className="step-i">ШАГ {i + 1}</div>
                  <div className="step-t">{p.title || `Шаг ${i + 1}`}</div>
                </div>
              ))}
            </div>
          )}
        </div>

        <div className="q-body">
          {saveError && (
            <Alert type="warning" message={saveError} closable onClose={() => setSaveError(null)} style={{ marginBottom: 12 }} />
          )}
          <div style={{ fontSize: 11, fontWeight: 600, letterSpacing: '0.06em', textTransform: 'uppercase', color: 'var(--ink-3)', marginBottom: 10 }}>
            {page?.title || `Страница ${currentPage + 1}`} · {visibleQuestions.length} вопр.
          </div>
          {visibleQuestions.length === 0 ? (
            <div style={{ color: 'var(--ink-3)', fontSize: 13 }}>Нет вопросов на этой странице</div>
          ) : (
            visibleQuestions.map(q => {
              const isActive = highlightedParam === q.parameterCode
              return (
                <div
                  key={q.id || q.parameterCode}
                  ref={el => {
                    if (el) questionRefs.current.set(q.parameterCode, el)
                    else questionRefs.current.delete(q.parameterCode)
                  }}
                  className={'q-card' + (isActive ? ' active' : '')}
                  style={{ marginBottom: 8 }}
                >
                  <QuestionRenderer
                    question={q}
                    value={answers[q.parameterCode]}
                    onChange={val => handleAnswerChange(q.parameterCode, val)}
                    onMultiChange={handleMultiAnswerChange}
                  />
                </div>
              )
            })
          )}
        </div>

        <div className="q-foot">
          <button
            className="btn sm"
            disabled={isFirst}
            onClick={() => setCurrentPage(p => p - 1)}
          >
            <Icon name="chev-left" size={12} /> Назад
          </button>
          <span className="muted tabular" style={{ fontSize: 12 }}>Страница {currentPage + 1} из {pages.length}</span>
          {isLast ? (
            <button className="btn sm success">
              Готово <Icon name="check" size={12} />
            </button>
          ) : (
            <button
              className="btn sm primary"
              onClick={() => setCurrentPage(p => p + 1)}
            >
              Далее <Icon name="chev-right" size={12} />
            </button>
          )}
        </div>
      </div>

      {/* ── Right: document editor ── */}
      <div className="editor-pane">
        <div style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '8px 16px', background: 'var(--bg-soft)', borderBottom: '1px solid var(--line)', flexShrink: 0 }}>
          <span style={{ fontSize: 12.5, color: 'var(--ink-3)', flex: 1 }}>
            Кликните на чип, чтобы перейти к вопросу. Документ обновляется в реальном времени.
          </span>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <ParticipantsPanel
              isConnected={isConnected}
              connectedUsers={connectedUsers as CollaborationUser[]}
              currentUserName={currentUser?.displayName ?? currentUser?.login}
              currentUserColor={currentUser ? loginToColor(currentUser.login) : '#888'}
            />
            <div style={{ width: 1, height: 18, background: 'var(--line)' }} />
            <button className="btn xs ghost" onClick={openHistory}>
              <Icon name="history" size={13} /> История
            </button>
            <div style={{ width: 1, height: 18, background: 'var(--line)' }} />
            <button
              className="btn xs"
              disabled={exportingDocx}
              onClick={async () => {
                if (!id) return
                setExportingDocx(true)
                try {
                  if (editorBodyRef.current) await documentsApi.updateBody(id, editorBodyRef.current)
                  const blob = await documentsApi.exportDocx(id)
                  const url = URL.createObjectURL(blob)
                  const a = document.createElement('a')
                  a.href = url; a.download = 'document.docx'; a.click()
                  URL.revokeObjectURL(url)
                } catch { notification.error({ message: 'Ошибка экспорта DOCX' }) }
                finally { setExportingDocx(false) }
              }}
            >
              <FileWordOutlined /> DOCX
            </button>
            <button
              className="btn xs primary"
              disabled={exportingPdf}
              onClick={async () => {
                if (!id) return
                setExportingPdf(true)
                try {
                  if (editorBodyRef.current) await documentsApi.updateBody(id, editorBodyRef.current)
                  const blob = await documentsApi.exportPdf(id)
                  const url = URL.createObjectURL(blob)
                  const a = document.createElement('a')
                  a.href = url; a.download = 'document.pdf'; a.click()
                  URL.revokeObjectURL(url)
                } catch { notification.error({ message: 'Ошибка экспорта PDF' }) }
                finally { setExportingPdf(false) }
              }}
            >
              <FilePdfOutlined /> PDF
            </button>
          </div>
        </div>

        <DocumentWorkspace
          ydoc={ydoc}
          templateBody={templateBody}
          answers={answers}
          parameters={parameters}
          phrasings={phrasings}
          formatting={formatting}
          questionnaire={questionnaire}
          highlightedParam={highlightedParam}
          onAnswerChange={handleAnswerChange}
          onParamFocus={handleParamFocus}
          initialBody={docCurrentBody}
          onBodyChange={handleBodyChange}
          readOnly={isReadOnly}
        />
      </div>

      {/* Revisions drawer */}
      {historyOpen && (
        <>
          <div className="drawer-veil" onClick={() => setHistoryOpen(false)} />
          <aside className="drawer">
            <div className="drawer-head">
              <h3 style={{ margin: 0, fontSize: 14, fontWeight: 550 }}>История ревизий</h3>
              <button className="icon-btn" onClick={() => setHistoryOpen(false)}><Icon name="x" size={14} /></button>
            </div>
            <div className="drawer-body">
              {loadingRevisions ? (
                <div style={{ textAlign: 'center', padding: 32 }}><Spin size="small" /></div>
              ) : revisions.length === 0 ? (
                <div style={{ color: 'var(--ink-3)', fontSize: 13 }}>Ревизий ещё нет</div>
              ) : (
                <List
                  dataSource={revisions}
                  renderItem={(rev) => (
                    <List.Item
                      actions={[
                        <button key="restore" className="btn xs" onClick={() => restoreRevision(rev.revisionNumber)}>
                          <RollbackOutlined /> Восстановить
                        </button>,
                      ]}
                    >
                      <List.Item.Meta
                        title={`Ревизия ${rev.revisionNumber}`}
                        description={
                          <>
                            <div>{new Date(rev.savedAt).toLocaleString('ru')}</div>
                            {rev.comment && <div style={{ color: 'var(--ink-3)', fontSize: 12 }}>{rev.comment}</div>}
                          </>
                        }
                      />
                    </List.Item>
                  )}
                />
              )}
            </div>
          </aside>
        </>
      )}
    </div>
  )
}

function loginToColor(login: string): string {
  let hash = 0
  for (let i = 0; i < login.length; i++) {
    hash = login.charCodeAt(i) + ((hash << 5) - hash)
  }
  return `hsl(${Math.abs(hash) % 360}, 65%, 45%)`
}
