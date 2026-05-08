import { useState } from 'react'
import { Node, mergeAttributes } from '@tiptap/core'
import { ReactNodeViewRenderer, NodeViewWrapper, NodeViewContent } from '@tiptap/react'
import type { ReactNodeViewProps } from '@tiptap/react'
import { Badge, Button, Input, Modal, Space, Tooltip } from 'antd'
import { DeleteOutlined, DownOutlined, RightOutlined, SettingOutlined } from '@ant-design/icons'
import { useDesignerContext } from '../DesignerContext'
import ConditionBuilder from '../ConditionBuilder'
import type { Condition } from '../../../types/templateModel'

// ── Types ─────────────────────────────────────────────────────────────────────

export interface BlockCondition {
  type: 'simple' | 'python'
  expression: string
  scriptId?: string
}

interface ConditionalBlockAttrs {
  condition: BlockCondition
  label: string
  collapsed: boolean
}

// Adapt between BlockCondition (internal, lowercase) and Condition (API, uppercase)
function toCondition(bc: BlockCondition): Condition {
  return { type: bc.type === 'python' ? 'PYTHON' : 'SIMPLE', expression: bc.expression, scriptId: bc.scriptId ?? null }
}
function fromCondition(c: Condition): BlockCondition {
  return { type: c.type === 'PYTHON' ? 'python' : 'simple', expression: c.expression ?? '', scriptId: c.scriptId ?? undefined }
}

// Нод оставлен зарегистрированным только для рендера/парсинга старых шаблонов.
// UI вставки (кнопка, slash-команда) убраны — новые conditionalBlock не создаются.

// ── React component ───────────────────────────────────────────────────────────

function ConditionalBlockView({ node, updateAttributes, deleteNode }: ReactNodeViewProps) {
  const [settingsOpen, setSettingsOpen] = useState(false)
  const [renamingOpen, setRenamingOpen] = useState(false)
  const [labelDraft, setLabelDraft] = useState('')
  const { parameters, published } = useDesignerContext()

  const attrs = node.attrs as ConditionalBlockAttrs
  const collapsed: boolean = attrs.collapsed ?? false

  const handleConditionSave = (next: Condition) => {
    updateAttributes({ condition: fromCondition(next) })
  }

  const openRename = () => { setLabelDraft(attrs.label); setRenamingOpen(true) }
  const commitRename = () => { if (labelDraft.trim()) updateAttributes({ label: labelDraft.trim() }); setRenamingOpen(false) }

  const conditionSummary = attrs.condition.expression
    ? `${attrs.condition.type === 'python' ? 'Python' : 'Условие'}: ${attrs.condition.expression}`
    : 'Условие не задано'

  return (
    <NodeViewWrapper as="div" style={{ margin: '8px 0' }}>
      <div style={{ borderLeft: '4px solid #AFA9EC', background: '#F7F6FE', borderRadius: '0 4px 4px 0' }}>
        {/* Header */}
        <div
          contentEditable={false}
          style={{
            display: 'flex', alignItems: 'center', justifyContent: 'space-between',
            padding: collapsed ? '4px 10px' : '6px 10px',
            borderBottom: collapsed ? 'none' : '1px solid #E8E6FA',
            userSelect: 'none',
            minHeight: collapsed ? 32 : undefined,
          }}
        >
          <Space size={8}>
            <Tooltip title="Свернуть / развернуть">
              <Button
                size="small"
                type="text"
                icon={collapsed ? <RightOutlined /> : <DownOutlined />}
                onClick={() => updateAttributes({ collapsed: !collapsed })}
                disabled={published}
                style={{ color: '#5B4EC8', padding: '0 4px' }}
              />
            </Tooltip>
            <Tooltip title={published ? conditionSummary : `Переименовать блок (двойной клик)\n${conditionSummary}`}>
              <span
                style={{ fontSize: 13, fontWeight: 600, color: '#5B4EC8', cursor: published ? 'default' : 'pointer' }}
                onDoubleClick={published ? undefined : openRename}
              >
                ⚡ {attrs.label || 'Условный блок'}
              </span>
            </Tooltip>
            <Badge
              count={attrs.condition.type === 'python' ? 'Python' : 'Условие'}
              style={{ backgroundColor: attrs.condition.type === 'python' ? '#722ED1' : '#1677FF', fontSize: 11 }}
            />
          </Space>

          {!published && (
            <Space size={4}>
              <Tooltip title="Настроить условие">
                <Button size="small" type="text" icon={<SettingOutlined />}
                  onClick={() => setSettingsOpen(true)} style={{ color: '#8C8C8C' }} />
              </Tooltip>
              <Tooltip title="Удалить блок">
                <Button size="small" type="text" danger icon={<DeleteOutlined />} onClick={deleteNode} />
              </Tooltip>
            </Space>
          )}
        </div>

        {/* Editable content — hidden when collapsed */}
        <div style={{ padding: '8px 12px', display: collapsed ? 'none' : undefined }}>
          <NodeViewContent as="div" />
        </div>
      </div>

      {/* Rename modal */}
      <Modal
        title="Переименовать блок"
        open={renamingOpen}
        onOk={commitRename}
        onCancel={() => setRenamingOpen(false)}
        okText="Сохранить"
        cancelText="Отмена"
        destroyOnHidden
        width={360}
      >
        <Input
          autoFocus
          value={labelDraft}
          onChange={(e) => setLabelDraft(e.target.value)}
          onPressEnter={commitRename}
          style={{ marginTop: 16 }}
          placeholder="Блок неустойки"
        />
      </Modal>

      {/* Condition builder */}
      <ConditionBuilder
        open={settingsOpen}
        condition={toCondition(attrs.condition)}
        parameters={parameters}
        onSave={handleConditionSave}
        onClose={() => setSettingsOpen(false)}
      />
    </NodeViewWrapper>
  )
}

// ── TipTap Node definition ────────────────────────────────────────────────────

const DEFAULT_CONDITION: BlockCondition = { type: 'simple', expression: '' }

export const ConditionalBlockNode = Node.create({
  name: 'conditionalBlock',
  group: 'block',
  content: 'block+',

  addAttributes() {
    return {
      label: {
        default: 'Условный блок',
        parseHTML: (el) => el.getAttribute('data-label') ?? 'Условный блок',
        renderHTML: (attrs) => ({ 'data-label': attrs.label }),
      },
      condition: {
        default: DEFAULT_CONDITION,
        parseHTML: (el) => {
          try { return JSON.parse(el.getAttribute('data-condition') ?? '{}') } catch { return DEFAULT_CONDITION }
        },
        renderHTML: (attrs) => ({ 'data-condition': JSON.stringify(attrs.condition) }),
      },
      collapsed: {
        default: false,
        parseHTML: (el) => el.getAttribute('data-collapsed') === 'true',
        renderHTML: (attrs) => ({ 'data-collapsed': attrs.collapsed }),
      },
    }
  },

  parseHTML() {
    return [{ tag: 'div[data-type="conditional-block"]' }]
  },

  renderHTML({ HTMLAttributes }) {
    return ['div', mergeAttributes(HTMLAttributes, { 'data-type': 'conditional-block' }), 0]
  },

  addNodeView() {
    return ReactNodeViewRenderer(ConditionalBlockView)
  },

})
