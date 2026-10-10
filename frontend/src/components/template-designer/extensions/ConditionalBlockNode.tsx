import { useState, useEffect, useRef } from 'react'
import { Node, mergeAttributes } from '@tiptap/core'
import { ReactNodeViewRenderer, NodeViewWrapper, NodeViewContent } from '@tiptap/react'
import type { ReactNodeViewProps } from '@tiptap/react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Badge } from '@/components/ui/badge'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog'
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from '@/components/ui/tooltip'
import { ChevronRight, ChevronDown, Settings, Trash2 } from 'lucide-react'
import { useDesignerContext } from '../DesignerContext'
import ConditionBuilder from '../ConditionBuilder'
import type { Condition } from '../../../types/templateModel'
import { cn } from '@/lib/utils'

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

function toCondition(bc: BlockCondition): Condition {
  return { type: bc.type === 'python' ? 'PYTHON' : 'SIMPLE', expression: bc.expression, scriptId: bc.scriptId ?? null }
}
function fromCondition(c: Condition): BlockCondition {
  return { type: c.type === 'PYTHON' ? 'python' : 'simple', expression: c.expression ?? '', scriptId: c.scriptId ?? undefined }
}

function ConditionalBlockView({ node, updateAttributes, deleteNode }: ReactNodeViewProps) {
  const [settingsOpen, setSettingsOpen] = useState(false)
  const [renamingOpen, setRenamingOpen] = useState(false)
  const [labelDraft, setLabelDraft] = useState('')
  const inputRef = useRef<HTMLInputElement>(null)
  const { parameters, published } = useDesignerContext()

  const attrs = node.attrs as ConditionalBlockAttrs
  const collapsed: boolean = attrs.collapsed ?? false

  useEffect(() => {
    if (renamingOpen && inputRef.current) {
      setTimeout(() => {
        inputRef.current?.focus()
        inputRef.current?.select()
      }, 100)
    }
  }, [renamingOpen])

  const handleConditionSave = (next: Condition) => {
    updateAttributes({ condition: fromCondition(next) })
  }

  const openRename = () => { setLabelDraft(attrs.label); setRenamingOpen(true) }
  const commitRename = () => { if (labelDraft.trim()) updateAttributes({ label: labelDraft.trim() }); setRenamingOpen(false) }

  const conditionSummary = attrs.condition.expression
    ? `${attrs.condition.type === 'python' ? 'Python' : 'Условие'}: ${attrs.condition.expression}`
    : 'Условие не задано'

  return (
    <NodeViewWrapper as="div" className="my-2">
      <div className="border-l-4 border-[#AFA9EC] bg-[#F7F6FE] rounded-r-md">
        <div
          contentEditable={false}
          className={cn(
            "flex items-center justify-between",
            collapsed ? "py-1 px-2.5 border-b-0" : "py-1.5 px-2.5 border-b border-[#E8E6FA]",
            "select-none",
            collapsed ? "min-h-8" : ""
          )}
        >
          <div className="flex items-center gap-2">
            <Tooltip>
              <TooltipTrigger asChild>
                <Button
                  variant="ghost"
                  size="icon-sm"
                  className="h-7 w-7 text-[#5B4EC8] hover:text-[#5B4EC8] hover:bg-[#5B4EC8]/10"
                  onClick={() => updateAttributes({ collapsed: !collapsed })}
                  disabled={published}
                >
                  {collapsed ? <ChevronRight className="size-4" /> : <ChevronDown className="size-4" />}
                </Button>
              </TooltipTrigger>
              <TooltipContent>Свернуть / развернуть</TooltipContent>
            </Tooltip>

            <Tooltip>
              <TooltipTrigger asChild>
                <span
                  className={cn(
                    "text-sm font-semibold text-[#5B4EC8]",
                    !published && "cursor-pointer"
                  )}
                  onDoubleClick={published ? undefined : openRename}
                >
                  ⚡ {attrs.label || 'Условный блок'}
                </span>
              </TooltipTrigger>
              <TooltipContent>
                {published ? conditionSummary : `Переименовать блок (двойной клик)\n${conditionSummary}`}
              </TooltipContent>
            </Tooltip>

            <Badge
              variant="default"
              className={cn(
                "h-5 text-[11px]",
                attrs.condition.type === 'python'
                  ? "bg-[#722ED1] hover:bg-[#722ED1]"
                  : "bg-[#1677FF] hover:bg-[#1677FF]"
              )}
            >
              {attrs.condition.type === 'python' ? 'Python' : 'Условие'}
            </Badge>
          </div>

          {!published && (
            <div className="flex items-center gap-1">
              <Tooltip>
                <TooltipTrigger asChild>
                  <Button
                    variant="ghost"
                    size="icon-sm"
                    className="h-7 w-7 text-muted-foreground hover:text-foreground"
                    onClick={() => setSettingsOpen(true)}
                  >
                    <Settings className="size-4" />
                  </Button>
                </TooltipTrigger>
                <TooltipContent>Настроить условие</TooltipContent>
              </Tooltip>

              <Tooltip>
                <TooltipTrigger asChild>
                  <Button
                    variant="ghost"
                    size="icon-sm"
                    className="h-7 w-7 text-destructive hover:text-destructive hover:bg-destructive/10"
                    onClick={deleteNode}
                  >
                    <Trash2 className="size-4" />
                  </Button>
                </TooltipTrigger>
                <TooltipContent>Удалить блок</TooltipContent>
              </Tooltip>
            </div>
          )}
        </div>

        <div
          className={cn(
            "py-2 px-3",
            collapsed && "hidden"
          )}
        >
          <NodeViewContent as="div" />
        </div>
      </div>

      <Dialog open={renamingOpen} onOpenChange={setRenamingOpen}>
        <DialogContent className="sm:max-w-sm">
          <DialogHeader>
            <DialogTitle>Переименовать блок</DialogTitle>
          </DialogHeader>
          <Input
            ref={inputRef}
            value={labelDraft}
            onChange={(e) => setLabelDraft(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') commitRename()
            }}
            placeholder="Блок неустойки"
          />
          <DialogFooter>
            <Button variant="outline" onClick={() => setRenamingOpen(false)}>
              Отмена
            </Button>
            <Button onClick={commitRename}>
              Сохранить
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

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
