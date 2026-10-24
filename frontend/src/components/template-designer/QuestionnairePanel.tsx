import { useEffect, useRef, useState } from 'react'
import {
  Accordion,
  AccordionContent,
  AccordionItem,
  AccordionTrigger,
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  Badge,
  Button,
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  Input,
  Label,
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from '@/components/ui'
import {
  DndContext, DragOverlay, PointerSensor,
  closestCenter, useSensor, useSensors,
} from '@dnd-kit/core'
import type { DragEndEvent, DragOverEvent, DragStartEvent } from '@dnd-kit/core'
import {
  SortableContext, arrayMove, useSortable, verticalListSortingStrategy,
} from '@dnd-kit/sortable'
import { CSS } from '@dnd-kit/utilities'
import { Eye, GripVertical, Pencil, Plus, Trash2 } from 'lucide-react'
import type { Parameter, Questionnaire } from '../../types/template'
import type { Condition, Question } from '../../types/templateModel'
import ConditionBuilder from './ConditionBuilder'

// ── Internal type: page with a stable client-side id ─────────────────────────

interface PageInternal {
  _id: string
  title: string
  questions: Question[]
}

let _pageIdCounter = 0
const newPageId = () => `page-${++_pageIdCounter}-${Date.now()}`
const newQuestionId = () => `q${Date.now()}-${Math.random().toString(36).slice(2, 6)}`

function toInternal(q: Questionnaire | null): PageInternal[] {
  return (q?.pages ?? []).map((p, i) => ({
    _id: `page-${i}-${p.title}`,
    title: p.title,
    questions: p.questions.map((qu, qi) => ({ ...qu, order: qi })),
  }))
}

function toQuestionnaire(pages: PageInternal[], title: string): Questionnaire {
  return {
    title,
    pages: pages.map((p) => ({
      title: p.title,
      questions: p.questions.map((q, i) => ({ ...q, order: i })),
    })),
  }
}

type DeleteTarget =
  | { type: 'question'; questionId: string; pageId: string }
  | { type: 'page'; pageId: string }
  | null

// ── Drag overlay card ─────────────────────────────────────────────────────────

function QuestionCard({ question }: {
  question: Question
}) {
  return (
    <div className="bg-card border rounded-md px-2 py-1.5 text-sm shadow-md">
      <div className="flex items-center gap-1">
        <GripVertical className="size-3.5 text-muted-foreground shrink-0" />
        <span className="text-sm truncate">{question.label}</span>
      </div>
      <div className="text-xs text-muted-foreground font-mono pl-5">{question.parameterCode}</div>
    </div>
  )
}

// ── Sortable question row ─────────────────────────────────────────────────────

function SortableQuestion({ question, parameters, onEdit, onDeleteRequest, onConditionChange, readOnly }: {
  question: Question
  parameters: Parameter[]
  onEdit: () => void
  onDeleteRequest: () => void
  onConditionChange: (c: Condition | null) => void
  readOnly?: boolean
}) {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } =
    useSortable({ id: question.id })
  const [condOpen, setCondOpen] = useState(false)

  return (
    <div
      ref={setNodeRef}
      style={{
        transform: CSS.Transform.toString(transform),
        transition,
        opacity: isDragging ? 0 : 1,
        marginBottom: 4,
      }}
    >
      <div className="flex items-center gap-1 bg-muted/30 border rounded-md px-2 py-1.5">
        <span
          {...attributes}
          {...(readOnly ? {} : listeners)}
          className={readOnly
            ? 'text-muted-foreground shrink-0'
            : 'cursor-grab text-muted-foreground shrink-0'}
        >
          <GripVertical className="size-3.5" />
        </span>

        <div className="flex-1 min-w-0">
          <span className="text-sm block truncate">{question.label}</span>
          <span className="text-xs text-muted-foreground font-mono">{question.parameterCode}</span>
        </div>

        {!readOnly && (
          <div className="flex gap-0.5 shrink-0">
            <TooltipProvider>
              <Tooltip>
                <TooltipTrigger asChild>
                  <Button
                    variant="ghost" size="icon-sm"
                    style={{ color: question.visibilityCondition ? 'var(--primary)' : 'var(--muted-foreground)' }}
                    onClick={() => setCondOpen(true)}
                  >
                    <Eye className="size-3.5" />
                  </Button>
                </TooltipTrigger>
                <TooltipContent>Настроить видимость</TooltipContent>
              </Tooltip>
            </TooltipProvider>

            <TooltipProvider>
              <Tooltip>
                <TooltipTrigger asChild>
                  <Button variant="ghost" size="icon-sm" onClick={onEdit}>
                    <Pencil className="size-3.5" />
                  </Button>
                </TooltipTrigger>
                <TooltipContent>Редактировать</TooltipContent>
              </Tooltip>
            </TooltipProvider>

            <Button variant="ghost" size="icon-sm" onClick={onDeleteRequest}>
              <Trash2 className="size-3.5 text-destructive" />
            </Button>
          </div>
        )}
      </div>

      <ConditionBuilder
        open={condOpen}
        condition={question.visibilityCondition ?? { type: 'SIMPLE', expression: '' }}
        parameters={parameters}
        onSave={(c) => onConditionChange(c.expression ? c : null)}
        onClose={() => setCondOpen(false)}
      />
    </div>
  )
}

// ── Page accordion item component ────────────────────────────────────────────

function PageAccordionItem({ page, onRename, onDeleteRequest, readOnly, children }: {
  page: PageInternal
  onRename: (pageId: string) => void
  onDeleteRequest: (pageId: string) => void
  readOnly?: boolean
  children: React.ReactNode
}) {
  return (
    <AccordionItem key={page._id} value={page._id} className="border rounded-md px-3">
      <AccordionTrigger className="py-2 hover:no-underline">
        <div className="flex items-center gap-2 flex-1 min-w-0 text-left" onClick={e => e.stopPropagation()}>
          <span style={{ fontWeight: 600, fontSize: 13 }} className="truncate">{page.title}</span>
          <Badge variant="outline" style={{ fontSize: 11 }}>{page.questions.length}</Badge>
        </div>
      </AccordionTrigger>
      <AccordionContent>
        {children}
        {!readOnly && (
          <div className="flex gap-1 mt-2 pt-2 border-t">
            <Button variant="ghost" size="sm" className="text-xs" onClick={() => onRename(page._id)}>
              <Pencil className="mr-1 size-3" /> Переименовать
            </Button>
            <Button variant="ghost" size="sm" className="text-xs text-destructive" onClick={() => onDeleteRequest(page._id)}>
              <Trash2 className="mr-1 size-3" /> Удалить
            </Button>
          </div>
        )}
      </AccordionContent>
    </AccordionItem>
  )
}

// ── Page content inside accordion ────────────────────────────────────────────

function PageContent({ page, parameters, onAddQuestion, onEditQuestion, onDeleteRequest, onConditionChange, readOnly }: {
  page: PageInternal
  parameters: Parameter[]
  onAddQuestion: () => void
  onEditQuestion: (q: Question) => void
  onDeleteRequest: (questionId: string) => void
  onConditionChange: (questionId: string, c: Condition | null) => void
  readOnly?: boolean
}) {
  return (
    <div>
      <SortableContext items={page.questions.map((q) => q.id)} strategy={verticalListSortingStrategy}>
        {page.questions.length === 0 && (
          <p className="text-xs text-muted-foreground mb-2">Нет вопросов</p>
        )}
        {page.questions.map((q) => (
          <SortableQuestion
            key={q.id}
            question={q}
            parameters={parameters}
            onEdit={() => onEditQuestion(q)}
            onDeleteRequest={() => onDeleteRequest(q.id)}
            onConditionChange={(c) => onConditionChange(q.id, c)}
            readOnly={readOnly}
          />
        ))}
      </SortableContext>

      {!readOnly && (
        <Button variant="outline" size="sm" className="w-full mt-1.5" onClick={onAddQuestion}>
          <Plus className="mr-1 size-3.5" /> Добавить вопрос
        </Button>
      )}
    </div>
  )
}

// ── Question modal ────────────────────────────────────────────────────────────

interface QuestionModalProps {
  open: boolean
  initial?: Question
  parameters: Parameter[]
  onSave: (values: { parameterCode: string; label: string; hint?: string }) => void
  onClose: () => void
}

function QuestionModal({ open, initial, parameters, onSave, onClose }: QuestionModalProps) {
  const [paramCode, setParamCode] = useState('')
  const [label, setLabel] = useState('')
  const [hint, setHint] = useState('')

  useEffect(() => {
    if (open) {
      if (initial) {
        setParamCode(initial.parameterCode)
        setLabel(initial.label)
        setHint(initial.hint ?? '')
      } else {
        setParamCode('')
        setLabel('')
        setHint('')
      }
    }
  }, [open, initial])

  const handleOk = () => {
    onSave({ parameterCode: paramCode, label, hint: hint || undefined })
    onClose()
  }

  const handleParamChange = (code: string) => {
    setParamCode(code)
    const param = parameters.find((p) => p.code === code)
    if (param && !label) {
      setLabel(param.label)
    }
  }

  return (
    <Dialog open={open} onOpenChange={(o) => { if (!o) onClose() }}>
      <DialogContent className="sm:max-w-[440px]">
        <DialogHeader>
          <DialogTitle>{initial ? 'Редактировать вопрос' : 'Новый вопрос'}</DialogTitle>
        </DialogHeader>

        <div className="mt-4 space-y-4">
          <div className="space-y-1.5">
            <Label>Параметр <span className="text-destructive">*</span></Label>
            <Select value={paramCode} onValueChange={handleParamChange}>
              <SelectTrigger className="w-full">
                <SelectValue placeholder="Выберите параметр" />
              </SelectTrigger>
              <SelectContent>
                {parameters.map((p) => (
                  <SelectItem key={p.code} value={p.code}>
                    <span className="flex items-center gap-1.5">
                      <span>{p.label}</span>
                      <span className="text-xs text-muted-foreground font-mono">({p.code})</span>
                    </span>
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="q-label">Текст вопроса <span className="text-destructive">*</span></Label>
            <Input
              id="q-label"
              value={label}
              onChange={e => setLabel(e.target.value)}
              placeholder="Укажите ФИО или наименование клиента"
            />
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="q-hint">Подсказка (необязательно)</Label>
            <Input
              id="q-hint"
              value={hint}
              onChange={e => setHint(e.target.value)}
              placeholder="Для юр. лица — полное наименование с ОПФ"
            />
          </div>
        </div>

        <DialogFooter className="mt-4">
          <Button variant="outline" onClick={onClose}>Отмена</Button>
          <Button onClick={handleOk} disabled={!paramCode || !label.trim()}>Сохранить</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

// ── Main component ────────────────────────────────────────────────────────────

export interface QuestionnairePanelProps {
  questionnaire: Questionnaire | null
  parameters: Parameter[]
  onChange: (q: Questionnaire) => void
  readOnly?: boolean
}

export default function QuestionnairePanel({ questionnaire, parameters, onChange, readOnly }: QuestionnairePanelProps) {
  const [pages, setPages] = useState<PageInternal[]>(() => toInternal(questionnaire))
  const [title] = useState(questionnaire?.title ?? 'Опросный лист')
  const [deleteTarget, setDeleteTarget] = useState<DeleteTarget>(null)

  // Sync when questionnaire prop changes (on template load)
  useEffect(() => {
    setPages(toInternal(questionnaire))
  }, [questionnaire])

  const notify = (next: PageInternal[]) => {
    setPages(next)
    onChange(toQuestionnaire(next, title))
  }

  // ── Add page ──────────────────────────────────────────────────────────────
  const [addingPage, setAddingPage] = useState(false)
  const [newPageTitle, setNewPageTitle] = useState('')

  const commitAddPage = () => {
    const t = newPageTitle.trim()
    if (!t) { setAddingPage(false); return }
    notify([...pages, { _id: newPageId(), title: t, questions: [] }])
    setNewPageTitle('')
    setAddingPage(false)
  }

  // ── Question modal state ──────────────────────────────────────────────────
  const [qModal, setQModal] = useState<{
    open: boolean; pageId: string; editing?: Question
  }>({ open: false, pageId: '' })

  const openAddQuestion = (pageId: string) =>
    setQModal({ open: true, pageId, editing: undefined })

  const openEditQuestion = (pageId: string, q: Question) =>
    setQModal({ open: true, pageId, editing: q })

  const handleQuestionSave = (values: { parameterCode: string; label: string; hint?: string }) => {
    notify(pages.map((p) => {
      if (p._id !== qModal.pageId) return p
      if (qModal.editing) {
        return {
          ...p,
          questions: p.questions.map((q) =>
            q.id === qModal.editing!.id ? { ...q, ...values } : q
          ),
        }
      }
      return {
        ...p,
        questions: [...p.questions, {
          id: newQuestionId(),
          parameterCode: values.parameterCode,
          label: values.label,
          hint: values.hint ?? null,
          order: p.questions.length,
          visibilityCondition: null,
        }],
      }
    }))
  }

  const handleDeleteQuestion = (pageId: string, questionId: string) => {
    notify(pages.map((p) =>
      p._id === pageId ? { ...p, questions: p.questions.filter((q) => q.id !== questionId) } : p
    ))
  }

  const handleConditionChange = (pageId: string, questionId: string, cond: Condition | null) => {
    notify(pages.map((p) =>
      p._id !== pageId ? p : {
        ...p,
        questions: p.questions.map((q) =>
          q.id === questionId ? { ...q, visibilityCondition: cond } : q
        ),
      }
    ))
  }

  const handleRenamePage = (pageId: string) => {
    const page = pages.find(p => p._id === pageId)
    if (!page) return
    const newTitle = prompt('Название страницы:', page.title)
    if (newTitle && newTitle.trim()) {
      notify(pages.map((p) => p._id === pageId ? { ...p, title: newTitle.trim() } : p))
    }
  }

  const handleDeletePage = (pageId: string) => {
    notify(pages.filter((p) => p._id !== pageId))
  }

  // ── DnD ──────────────────────────────────────────────────────────────────
  const sensors = useSensors(useSensor(PointerSensor, { activationConstraint: { distance: 5 } }))
  const [activeQuestion, setActiveQuestion] = useState<{ q: Question; pageId: string } | null>(null)

  // mutable ref to track pages during drag-over without triggering re-renders
  const pagesRef = useRef(pages)
  pagesRef.current = pages

  const findPageByQuestionId = (qId: string): PageInternal | undefined =>
    pagesRef.current.find((p) => p.questions.some((q) => q.id === qId))

  const handleDragStart = (e: DragStartEvent) => {
    if (readOnly) return
    const page = findPageByQuestionId(String(e.active.id))
    const question = page?.questions.find((q) => q.id === e.active.id)
    if (page && question) setActiveQuestion({ q: question, pageId: page._id })
  }

  const handleDragOver = (e: DragOverEvent) => {
    if (readOnly) return
    const { active, over } = e
    if (!over) return
    const activeId = String(active.id)
    const overId = String(over.id)
    if (activeId === overId) return

    const activePage = findPageByQuestionId(activeId)
    if (!activePage) return

    // Determine target page: over a question or over a page container
    const overPage = findPageByQuestionId(overId) ?? pagesRef.current.find((p) => p._id === overId)
    if (!overPage || overPage._id === activePage._id) return

    // Move question to the new page (append to end)
    const movingQ = activePage.questions.find((q) => q.id === activeId)!
    const next = pagesRef.current.map((p) => {
      if (p._id === activePage._id) return { ...p, questions: p.questions.filter((q) => q.id !== activeId) }
      if (p._id === overPage._id) return { ...p, questions: [...p.questions, movingQ] }
      return p
    })
    setPages(next)          // optimistic update without calling onChange yet
    pagesRef.current = next
  }

  const handleDragEnd = (e: DragEndEvent) => {
    const { active, over } = e
    setActiveQuestion(null)
    if (!over || readOnly) return

    const activeId = String(active.id)
    const overId = String(over.id)
    if (activeId === overId) return

    const page = findPageByQuestionId(activeId)
    if (!page) return

    const qs = page.questions
    const oldIdx = qs.findIndex((q) => q.id === activeId)
    const newIdx = qs.findIndex((q) => q.id === overId)
    if (oldIdx === -1 || newIdx === -1) { notify(pagesRef.current); return }

    notify(pagesRef.current.map((p) =>
      p._id === page._id ? { ...p, questions: arrayMove(p.questions, oldIdx, newIdx) } : p
    ))
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
        <span style={{ fontWeight: 600, fontSize: 14 }}>Страницы ({pages.length})</span>
        {!readOnly && (
          <Button size="sm" onClick={() => setAddingPage(true)}>
            <Plus className="mr-1 size-3.5" /> Страница
          </Button>
        )}
      </div>

      {addingPage && (
        <div className="flex gap-2 mb-2 w-full">
          <Input
            autoFocus
            value={newPageTitle}
            onChange={(e) => setNewPageTitle(e.target.value)}
            onKeyDown={e => { if (e.key === 'Enter') commitAddPage() }}
            placeholder="Название страницы"
            className="flex-1"
          />
          <Button size="sm" onClick={commitAddPage}>OK</Button>
          <Button size="sm" variant="outline" onClick={() => { setAddingPage(false); setNewPageTitle('') }}>✕</Button>
        </div>
      )}

      <div style={{ flex: 1, overflow: 'auto' }}>
        <DndContext
          sensors={sensors}
          collisionDetection={closestCenter}
          onDragStart={handleDragStart}
          onDragOver={handleDragOver}
          onDragEnd={handleDragEnd}
        >
          {pages.length === 0 ? (
            <p style={{ fontSize: 13, color: 'var(--ink-3)' }}>
              Нажмите «Страница» чтобы создать первую страницу опросного листа.
            </p>
          ) : (
            <Accordion type="multiple" defaultValue={pages.map((p) => p._id)} className="space-y-1">
              {pages.map((page) => (
                  <PageAccordionItem
                    key={page._id}
                    page={page}
                    onRename={handleRenamePage}
                    onDeleteRequest={(pageId) => setDeleteTarget({ type: 'page', pageId })}
                    readOnly={readOnly}
                  >
                    <PageContent
                      page={page}
                      parameters={parameters}
                      onAddQuestion={() => openAddQuestion(page._id)}
                      onEditQuestion={(q) => openEditQuestion(page._id, q)}
                      onDeleteRequest={(qId) => setDeleteTarget({ type: 'question', questionId: qId, pageId: page._id })}
                      onConditionChange={(qId, c) => handleConditionChange(page._id, qId, c)}
                      readOnly={readOnly}
                    />
                  </PageAccordionItem>
              ))}
            </Accordion>
          )}

          <DragOverlay>
            {activeQuestion && (
              <QuestionCard question={activeQuestion.q} />
            )}
          </DragOverlay>
        </DndContext>
      </div>

      <AlertDialog open={deleteTarget !== null} onOpenChange={(open) => { if (!open) setDeleteTarget(null) }}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>
              {deleteTarget?.type === 'page' ? 'Удалить страницу со всеми вопросами?' : 'Удалить вопрос?'}
            </AlertDialogTitle>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel onClick={() => setDeleteTarget(null)}>Нет</AlertDialogCancel>
            <AlertDialogAction variant="destructive" onClick={() => {
              if (!deleteTarget) return
              if (deleteTarget.type === 'page') {
                handleDeletePage(deleteTarget.pageId)
              } else {
                handleDeleteQuestion(deleteTarget.pageId, deleteTarget.questionId!)
              }
              setDeleteTarget(null)
            }}>
              Да
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      <QuestionModal
        open={qModal.open}
        initial={qModal.editing}
        parameters={parameters}
        onSave={handleQuestionSave}
        onClose={() => setQModal((s) => ({ ...s, open: false }))}
      />
    </div>
  )
}
