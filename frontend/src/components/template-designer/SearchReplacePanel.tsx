import { useEffect, useRef, useState } from 'react'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Input } from '@/components/ui/input'
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from '@/components/ui/tooltip'
import { X, ChevronUp, ChevronDown } from 'lucide-react'
import type { Editor } from '@tiptap/react'
import { searchReplaceKey } from './extensions/SearchReplaceExtension'
import { useEditorState } from '@tiptap/react'
import { cn } from '@/lib/utils'

interface Props {
  editor: Editor
  open: boolean
  onClose: () => void
}

export default function SearchReplacePanel({ editor, open, onClose }: Props) {
  const [searchTerm, setSearchTerm] = useState('')
  const [replacement, setReplacement] = useState('')
  const [caseSensitive, setCaseSensitive] = useState(false)
  const [wholeWord, setWholeWord] = useState(false)
  const searchInputRef = useRef<HTMLInputElement>(null)

  const pluginState = useEditorState({
    editor,
    selector: ({ editor: e }) => searchReplaceKey.getState(e.view.state),
  })

  const matchCount = pluginState?.matches.length ?? 0
  const currentIndex = pluginState?.currentIndex ?? 0

  useEffect(() => {
    if (open) {
      setTimeout(() => searchInputRef.current?.focus(), 50)
    } else {
      editor.commands.clearSearch()
      setSearchTerm('')
    }
  }, [open, editor])

  useEffect(() => {
    if (!open) return
    editor.commands.setSearch(searchTerm, { caseSensitive, wholeWord })
  }, [searchTerm, caseSensitive, wholeWord, open, editor])

  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && open) {
        e.preventDefault()
        onClose()
      }
    }
    window.addEventListener('keydown', handler)
    return () => window.removeEventListener('keydown', handler)
  }, [open, onClose])

  if (!open) return null

  const counterText = matchCount === 0
    ? (searchTerm ? 'Не найдено' : '')
    : `${currentIndex + 1} из ${matchCount}`

  return (
    <div className="px-3 py-2 border-b border-line bg-[#fffbe6] flex flex-col gap-1.5">
      <div className="flex items-center gap-1.5">
        <div className="relative" style={{ width: 220 }}>
          <Input
            ref={searchInputRef}
            placeholder="Найти…"
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') {
                e.shiftKey
                  ? editor.commands.prevMatch()
                  : editor.commands.nextMatch()
              }
            }}
            className="h-7 text-xs pr-12"
          />
          <span
            className={cn(
              "absolute right-2 top-1/2 -translate-y-1/2 text-[11px] min-w-12",
              matchCount === 0 && searchTerm ? "text-destructive" : "text-muted-foreground"
            )}
          >
            {counterText}
          </span>
        </div>

        <Tooltip>
          <TooltipTrigger asChild>
            <Button
              variant="outline"
              size="sm"
              onClick={() => editor.commands.prevMatch()}
              disabled={matchCount === 0}
              className="h-7 px-2"
            >
              <ChevronUp className="size-3.5" />
            </Button>
          </TooltipTrigger>
          <TooltipContent>Предыдущее (Shift+Enter)</TooltipContent>
        </Tooltip>

        <Tooltip>
          <TooltipTrigger asChild>
            <Button
              variant="outline"
              size="sm"
              onClick={() => editor.commands.nextMatch()}
              disabled={matchCount === 0}
              className="h-7 px-2"
            >
              <ChevronDown className="size-3.5" />
            </Button>
          </TooltipTrigger>
          <TooltipContent>Следующее (Enter)</TooltipContent>
        </Tooltip>

        <div className="flex items-center gap-4 ml-1">
          <label className="flex items-center gap-1.5 cursor-pointer select-none">
            <Checkbox
              checked={caseSensitive}
              onCheckedChange={(checked) => setCaseSensitive(checked === true)}
              className="size-3.5"
            />
            <span className="text-xs">Aa</span>
          </label>
          <label className="flex items-center gap-1.5 cursor-pointer select-none">
            <Checkbox
              checked={wholeWord}
              onCheckedChange={(checked) => setWholeWord(checked === true)}
              className="size-3.5"
            />
            <span className="text-xs">Слово</span>
          </label>
        </div>

        <Tooltip>
          <TooltipTrigger asChild>
            <Button
              variant="ghost"
              size="icon-sm"
              onClick={onClose}
              className="h-7 w-7 ml-auto hover:bg-muted/50"
            >
              <X className="size-3.5" />
            </Button>
          </TooltipTrigger>
          <TooltipContent>Закрыть (Escape)</TooltipContent>
        </Tooltip>
      </div>

      <div className="flex items-center gap-1.5">
        <Input
          placeholder="Заменить на…"
          value={replacement}
          onChange={(e) => setReplacement(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter') editor.commands.replaceMatch(replacement)
          }}
          className="h-7 text-xs"
          style={{ width: 220 }}
        />
        <Button
          variant="outline"
          size="sm"
          onClick={() => editor.commands.replaceMatch(replacement)}
          disabled={matchCount === 0}
          className="h-7 text-xs"
        >
          Заменить
        </Button>
        <Button
          variant="outline"
          size="sm"
          onClick={() => editor.commands.replaceAll(replacement)}
          disabled={matchCount === 0}
          className="h-7 text-xs"
        >
          Заменить всё
        </Button>
      </div>
    </div>
  )
}
