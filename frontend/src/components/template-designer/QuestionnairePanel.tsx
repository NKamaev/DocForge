import { useEffect, useMemo, useRef, useState } from 'react'
import {
  Button, Collapse, Form, Input, Modal, Popconfirm,
  Select, Space, Tag, Tooltip, Typography,
} from 'antd'
import {
  DndContext, DragOverlay, PointerSensor,
  closestCenter, useSensor, useSensors,
} from '@dnd-kit/core'
import type { DragEndEvent, DragOverEvent, DragStartEvent } from '@dnd-kit/core'
import {
  SortableContext, arrayMove, useSortable, verticalListSortingStrategy,
} from '@dnd-kit/sortable'
import { CSS } from '@dnd-kit/utilities'
import {
  DeleteOutlined, EyeOutlined, HolderOutlined,
  PlusOutlined, EditOutlined,
} from '@ant-design/icons'
import type { Parameter, Questionnaire } from '../../types/template'
import type { Condition, Question } from '../../types/templateModel'
import ConditionBuilder from './ConditionBuilder'

const { Text } = Typography

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

// ── Question form values ──────────────────────────────────────────────────────

interface QuestionFormValues {
  parameterCode: string
  label: string
  hint?: string
}

// ── Drag overlay card ─────────────────────────────────────────────────────────

function QuestionCard({ question, style }: {
  question: Question
  parameters?: Parameter[]
  style?: React.CSSProperties
}) {
  return (
    <div style={{
      background: '#fff',
      border: '1px solid #d9d9d9',
      borderRadius: 6,
      padding: '6px 8px',
      fontSize: 13,
      ...style,
    }}>
      <Space size={4}>
        <HolderOutlined style={{ color: '#bbb' }} />
        <Text style={{ fontSize: 13 }}>{question.label}</Text>
      </Space>
      <div>
        <Text type="secondary" style={{ fontSize: 11, fontFamily: 'monospace' }}>
          {question.parameterCode}
        </Text>
      </div>
    </div>
  )
}

// ── Sortable question row ─────────────────────────────────────────────────────

function SortableQuestion({ question, parameters, onEdit, onDelete, onConditionChange, readOnly }: {
  question: Question
  parameters: Parameter[]
  onEdit: () => void
  onDelete: () => void
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
      <div style={{
        display: 'flex',
        alignItems: 'center',
        gap: 4,
        background: '#fafafa',
        border: '1px solid #e8e8e8',
        borderRadius: 6,
        padding: '5px 8px',
      }}>
        <span {...attributes} {...listeners} style={{ cursor: 'grab', color: '#bbb', flexShrink: 0 }}>
          <HolderOutlined />
        </span>

        <div style={{ flex: 1, minWidth: 0 }}>
          <Text style={{ fontSize: 13, display: 'block' }} ellipsis>{question.label}</Text>
          <Text type="secondary" style={{ fontSize: 11, fontFamily: 'monospace' }}>
            {question.parameterCode}
          </Text>
        </div>

        {!readOnly && (
          <Space size={2} style={{ flexShrink: 0 }}>
            <Tooltip title="Настроить видимость">
              <Button
                type="text" size="small"
                icon={<EyeOutlined />}
                style={{ color: question.visibilityCondition ? '#1677ff' : '#d9d9d9' }}
                onClick={() => setCondOpen(true)}
              />
            </Tooltip>
            <Tooltip title="Редактировать">
              <Button type="text" size="small" icon={<EditOutlined />} onClick={onEdit} />
            </Tooltip>
            <Popconfirm
              title="Удалить вопрос?"
              okText="Да" cancelText="Нет"
              onConfirm={onDelete}
            >
              <Button type="text" size="small" danger icon={<DeleteOutlined />} />
            </Popconfirm>
          </Space>
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

// ── Page block (Collapse panel content) ──────────────────────────────────────

function PageContent({ page, parameters, onAddQuestion, onEditQuestion, onDeleteQuestion, onConditionChange, readOnly }: {
  page: PageInternal
  parameters: Parameter[]
  onAddQuestion: () => void
  onEditQuestion: (q: Question) => void
  onDeleteQuestion: (id: string) => void
  onConditionChange: (questionId: string, c: Condition | null) => void
  onRenamePage?: (title: string) => void
  onDeletePage?: () => void
  readOnly?: boolean
}) {
  return (
    <div>
      <SortableContext items={page.questions.map((q) => q.id)} strategy={verticalListSortingStrategy}>
        {page.questions.length === 0 && (
          <Text type="secondary" style={{ fontSize: 12, display: 'block', marginBottom: 8 }}>
            Нет вопросов
          </Text>
        )}
        {page.questions.map((q) => (
          <SortableQuestion
            key={q.id}
            question={q}
            parameters={parameters}
            onEdit={() => onEditQuestion(q)}
            onDelete={() => onDeleteQuestion(q.id)}
            onConditionChange={(c) => onConditionChange(q.id, c)}
            readOnly={readOnly}
          />
        ))}
      </SortableContext>

      {!readOnly && (
        <Button
          type="dashed" size="small" icon={<PlusOutlined />}
          onClick={onAddQuestion}
          style={{ width: '100%', marginTop: 6 }}
        >
          Добавить вопрос
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
  onSave: (values: QuestionFormValues) => void
  onClose: () => void
}

function QuestionModal({ open, initial, parameters, onSave, onClose }: QuestionModalProps) {
  const [form] = Form.useForm<QuestionFormValues>()

  useEffect(() => {
    if (open) {
      if (initial) {
        form.setFieldsValue({ parameterCode: initial.parameterCode, label: initial.label, hint: initial.hint ?? '' })
      } else {
        form.resetFields()
      }
    }
  }, [open, initial, form])

  const handleOk = async () => {
    const values = await form.validateFields()
    onSave(values)
    onClose()
  }

  const handleParamChange = (code: string) => {
    const param = parameters.find((p) => p.code === code)
    if (param && !form.getFieldValue('label')) {
      form.setFieldValue('label', param.label)
    }
  }

  return (
    <Modal
      title={initial ? 'Редактировать вопрос' : 'Новый вопрос'}
      open={open}
      onOk={handleOk}
      onCancel={onClose}
      okText="Сохранить"
      cancelText="Отмена"
      destroyOnHidden
      width={440}
    >
      <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
        <Form.Item name="parameterCode" label="Параметр" rules={[{ required: true, message: 'Выберите параметр' }]}>
          <Select
            showSearch
            placeholder="Выберите параметр"
            onChange={handleParamChange}
            options={parameters.map((p) => ({
              value: p.code,
              label: (
                <Space size={6}>
                  <Text style={{ fontSize: 13 }}>{p.label}</Text>
                  <Text type="secondary" style={{ fontSize: 11, fontFamily: 'monospace' }}>{p.code}</Text>
                </Space>
              ),
            }))}
            filterOption={(input, option) =>
              String(option?.value ?? '').toLowerCase().includes(input.toLowerCase())
            }
          />
        </Form.Item>

        <Form.Item name="label" label="Текст вопроса" rules={[{ required: true, message: 'Введите вопрос' }]}>
          <Input placeholder="Укажите ФИО или наименование клиента" />
        </Form.Item>

        <Form.Item name="hint" label="Подсказка (необязательно)">
          <Input placeholder="Для юр. лица — полное наименование с ОПФ" />
        </Form.Item>
      </Form>
    </Modal>
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

  const handleQuestionSave = (values: QuestionFormValues) => {
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

  const handleRenamePage = (pageId: string, t: string) => {
    notify(pages.map((p) => p._id === pageId ? { ...p, title: t } : p))
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
    const page = findPageByQuestionId(String(e.active.id))
    const question = page?.questions.find((q) => q.id === e.active.id)
    if (page && question) setActiveQuestion({ q: question, pageId: page._id })
  }

  const handleDragOver = (e: DragOverEvent) => {
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
    if (!over) return

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

  // ── Collapse items ────────────────────────────────────────────────────────
  const collapseItems = useMemo(() => pages.map((page) => ({
    key: page._id,
    label: (
      <Space>
        <Text strong style={{ fontSize: 13 }}>{page.title}</Text>
        <Tag style={{ fontSize: 11 }}>{page.questions.length}</Tag>
      </Space>
    ),
    extra: readOnly ? undefined : (
      <Popconfirm
        title="Удалить страницу со всеми вопросами?"
        okText="Удалить" cancelText="Нет" okButtonProps={{ danger: true }}
        onConfirm={(e) => { e?.stopPropagation(); handleDeletePage(page._id) }}
        onCancel={(e) => e?.stopPropagation()}
      >
        <Button
          type="text" size="small" danger
          icon={<DeleteOutlined />}
          onClick={(e) => e.stopPropagation()}
        />
      </Popconfirm>
    ),
    children: (
      <PageContent
        page={page}
        parameters={parameters}
        onAddQuestion={() => openAddQuestion(page._id)}
        onEditQuestion={(q) => openEditQuestion(page._id, q)}
        onDeleteQuestion={(qId) => handleDeleteQuestion(page._id, qId)}
        onConditionChange={(qId, c) => handleConditionChange(page._id, qId, c)}
        onRenamePage={(t) => handleRenamePage(page._id, t)}
        onDeletePage={() => handleDeletePage(page._id)}
        readOnly={readOnly}
      />
    ),
  })), [pages, parameters, readOnly]) // eslint-disable-line react-hooks/exhaustive-deps

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
        <Text strong>Страницы ({pages.length})</Text>
        {!readOnly && (
          <Button size="small" type="primary" icon={<PlusOutlined />} onClick={() => setAddingPage(true)}>
            Страница
          </Button>
        )}
      </div>

      {addingPage && (
        <Space style={{ marginBottom: 8, width: '100%' }}>
          <Input
            autoFocus
            size="small"
            placeholder="Название страницы"
            value={newPageTitle}
            onChange={(e) => setNewPageTitle(e.target.value)}
            onPressEnter={commitAddPage}
            style={{ flex: 1 }}
          />
          <Button size="small" type="primary" onClick={commitAddPage}>OK</Button>
          <Button size="small" onClick={() => { setAddingPage(false); setNewPageTitle('') }}>✕</Button>
        </Space>
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
            <Text type="secondary" style={{ fontSize: 13 }}>
              Нажмите «Страница» чтобы создать первую страницу опросного листа.
            </Text>
          ) : (
            <Collapse
              items={collapseItems}
              defaultActiveKey={pages.map((p) => p._id)}
              size="small"
            />
          )}

          <DragOverlay>
            {activeQuestion && (
              <QuestionCard question={activeQuestion.q} />
            )}
          </DragOverlay>
        </DndContext>
      </div>

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
