import { useState } from 'react'
import { useEditor, EditorContent } from '@tiptap/react'
import StarterKit from '@tiptap/starter-kit'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from '@/components/ui/tooltip'
import { ChevronUp, ChevronDown, Trash2, Filter, Plus } from 'lucide-react'
import { ParameterFieldNode } from './extensions/ParameterFieldNode'
import ConditionBuilder from './ConditionBuilder'
import type { PhrasingVariant } from '../../types/templateModel'
import type { Condition } from '../../types/templateModel'
import type { Parameter } from '../../types/template'

interface Props {
  variant: PhrasingVariant
  index: number
  isFirst: boolean
  isLast: boolean
  isDefault: boolean
  parameters: Parameter[]
  onChange: (variant: PhrasingVariant) => void
  onDelete: () => void
  onMoveUp: () => void
  onMoveDown: () => void
}

export default function PhrasingVariantEditor({
  variant,
  index,
  isFirst,
  isLast,
  isDefault,
  parameters,
  onChange,
  onDelete,
  onMoveUp,
  onMoveDown,
}: Props) {
  const [conditionOpen, setConditionOpen] = useState(false)

  const editor = useEditor({
    extensions: [
      StarterKit.configure({
        heading: false,
        bulletList: false,
        orderedList: false,
        blockquote: false,
        codeBlock: false,
        horizontalRule: false,
      }),
      ParameterFieldNode,
    ],
    content: {
      type: 'doc',
      content: [{ type: 'paragraph', content: variant.content as never[] ?? [] }],
    },
    onUpdate: ({ editor: e }) => {
      const json = e.getJSON()
      const content = json.content?.[0]?.content ?? []
      onChange({ ...variant, content: content as PhrasingVariant['content'] })
    },
  })

  const conditionLabel = variant.condition
    ? (variant.condition.type === 'PYTHON'
        ? `Python: ${variant.condition.expression ?? ''}`
        : variant.condition.expression || '(пусто)')
    : null

  const handleConditionSave = (cond: Condition) => {
    if (!cond.expression?.trim()) {
      onChange({ ...variant, condition: null })
    } else {
      onChange({ ...variant, condition: cond })
    }
  }

  return (
    <div
      style={{
        border: `1.5px solid ${isDefault ? '#91CAFF' : '#d9d9d9'}`,
        borderRadius: 6,
        padding: '8px 10px',
        marginBottom: 8,
        background: isDefault ? '#F0F7FF' : '#FAFAFA',
      }}
    >
      <div className="flex items-center justify-between mb-1.5">
        <div className="flex items-center gap-1">
          <span className="text-xs text-muted-foreground">Вариант {index + 1}</span>
          {isDefault && <Badge variant="outline" className="text-[11px]">Умолчание</Badge>}
          {conditionLabel != null && (
            <Badge
              variant="default"
              className="text-[11px] max-w-[200px] truncate cursor-pointer"
              onClick={() => setConditionOpen(true)}
            >
              <Filter className="size-3 mr-1" />
              {conditionLabel}
            </Badge>
          )}
          {conditionLabel == null && (
            <Button
              variant="outline"
              size="sm"
              className="text-[11px]"
              onClick={() => setConditionOpen(true)}
            >
              <Filter className="size-3 mr-1" />
              Добавить условие
            </Button>
          )}
          {conditionLabel != null && (
            <Tooltip>
              <TooltipTrigger asChild>
                <Button
                  variant="ghost"
                  size="icon-sm"
                  className="h-7 w-7"
                  onClick={() => setConditionOpen(true)}
                >
                  <Filter className="size-4" />
                </Button>
              </TooltipTrigger>
              <TooltipContent>Изменить условие</TooltipContent>
            </Tooltip>
          )}
        </div>
        <div className="flex items-center gap-1">
          <Tooltip>
            <TooltipTrigger asChild>
              <Button
                variant="ghost"
                size="icon-sm"
                className="h-7 w-7"
                onClick={onMoveUp}
                disabled={isFirst}
              >
                <ChevronUp className="size-4" />
              </Button>
            </TooltipTrigger>
            <TooltipContent>Переместить вверх</TooltipContent>
          </Tooltip>

          <Tooltip>
            <TooltipTrigger asChild>
              <Button
                variant="ghost"
                size="icon-sm"
                className="h-7 w-7"
                onClick={onMoveDown}
                disabled={isLast}
              >
                <ChevronDown className="size-4" />
              </Button>
            </TooltipTrigger>
            <TooltipContent>Переместить вниз</TooltipContent>
          </Tooltip>

          <Tooltip>
            <TooltipTrigger asChild>
              <Button
                variant="ghost"
                size="icon-sm"
                className="h-7 w-7 text-destructive"
                onClick={onDelete}
              >
                <Trash2 className="size-4" />
              </Button>
            </TooltipTrigger>
            <TooltipContent>Удалить вариант</TooltipContent>
          </Tooltip>
        </div>
      </div>

      <div className="border border-border rounded-md bg-background">
        <div className="border-b border-muted px-1.5 py-1">
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button
                variant="ghost"
                size="sm"
                className="text-[12px] text-primary"
              >
                <Plus className="size-3 mr-1" />
                Параметр
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent>
              {parameters.length === 0 ? (
                <DropdownMenuItem disabled>Нет параметров в шаблоне</DropdownMenuItem>
              ) : (
                parameters.flatMap((p) => {
                  if (p.type === 'DIRECTORY' && p.directoryFields && p.directoryFields.length > 0) {
                    return p.directoryFields.map((fieldKey) => (
                      <DropdownMenuItem
                        key={`${p.code}__${fieldKey}`}
                        onClick={() => editor?.chain().focus().insertParameterField({
                          parameterCode: `${p.code}__${fieldKey}`,
                          display: `${p.label} → ${fieldKey}`,
                        }).run()}
                      >
                        <span className="font-medium">{p.label} → {fieldKey}</span>
                      </DropdownMenuItem>
                    ))
                  }
                  return [(
                    <DropdownMenuItem
                      key={p.code}
                      onClick={() => editor?.chain().focus().insertParameterField({ parameterCode: p.code, display: p.label }).run()}
                    >
                      <span className="font-medium">{p.label}</span>
                      <span className="ml-2 text-muted-foreground text-xs">{p.code}</span>
                    </DropdownMenuItem>
                  )]
                })
              )}
            </DropdownMenuContent>
          </DropdownMenu>
        </div>
        <div
          className="p-1.5 min-h-[36px] text-sm cursor-text"
          onClick={() => editor?.commands.focus()}
        >
          <EditorContent editor={editor} />
        </div>
      </div>

      {isDefault && (
        <div className="text-[11px] text-muted-foreground mt-1">
          Вариант без условия — показывается если ни один другой вариант не подошёл.
        </div>
      )}

      <ConditionBuilder
        open={conditionOpen}
        condition={variant.condition ?? { type: 'SIMPLE', expression: '', scriptId: null }}
        parameters={parameters}
        onSave={handleConditionSave}
        onClose={() => setConditionOpen(false)}
      />
    </div>
  )
}
