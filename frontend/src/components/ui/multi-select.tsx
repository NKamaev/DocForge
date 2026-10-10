import { useEffect, useRef, useState } from 'react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Input } from '@/components/ui/input'
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from '@/components/ui/popover'
import { cn } from '@/lib/utils'
import { X, ChevronDown } from 'lucide-react'

export interface MultiSelectOption {
  value: string
  label: string
  description?: string
}

interface MultiSelectProps {
  options: MultiSelectOption[]
  selected: string[]
  onChange: (selected: string[]) => void
  placeholder?: string
  emptyText?: string
  searchPlaceholder?: string
  className?: string
  disabled?: boolean
  maxDisplay?: number
}

export function MultiSelect({
  options,
  selected,
  onChange,
  placeholder = 'Выберите...',
  emptyText = 'Нет элементов',
  searchPlaceholder = 'Поиск...',
  className,
  disabled = false,
  maxDisplay = 3,
}: MultiSelectProps) {
  const [open, setOpen] = useState(false)
  const [search, setSearch] = useState('')
  const inputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    if (open) {
      setTimeout(() => inputRef.current?.focus(), 50)
    } else {
      setSearch('')
    }
  }, [open])

  const filtered = options.filter(
    (o) =>
      o.label.toLowerCase().includes(search.toLowerCase()) ||
      o.value.toLowerCase().includes(search.toLowerCase()),
  )

  const toggle = (value: string) => {
    onChange(
      selected.includes(value)
        ? selected.filter((v) => v !== value)
        : [...selected, value],
    )
  }

  const clearAll = () => onChange([])

  const displayed = selected.slice(0, maxDisplay)
  const remainder = selected.length - maxDisplay

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <button
          data-slot="multi-select-trigger"
          className={cn(
            'flex h-9 w-full items-center justify-between gap-1 rounded-lg border border-input bg-background px-3 py-1 text-sm shadow-xs outline-none transition-all',
            'focus-visible:border-ring focus-visible:ring-[3px] focus-visible:ring-ring/50',
            'disabled:cursor-not-allowed disabled:opacity-50',
            'data-open:bg-muted data-open:text-muted-foreground',
            className,
          )}
          disabled={disabled}
        >
          <div className="flex flex-1 flex-wrap gap-1 items-center min-w-0">
            {selected.length === 0 && (
              <span className="text-muted-foreground">{placeholder}</span>
            )}
            {displayed.map((v) => {
              const opt = options.find((o) => o.value === v)
              return (
                <Badge
                  key={v}
                  variant="secondary"
                  className="text-xs gap-1 shrink-0 max-w-[140px]"
                >
                  <span className="truncate">{opt?.label ?? v}</span>
                  <span
                    role="button"
                    tabIndex={0}
                    className="cursor-pointer hover:text-foreground shrink-0"
                    onClick={(e) => {
                      e.stopPropagation()
                      toggle(v)
                    }}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter' || e.key === ' ') {
                        e.stopPropagation()
                        toggle(v)
                      }
                    }}
                  >
                    <X className="size-3" />
                  </span>
                </Badge>
              )
            })}
            {remainder > 0 && (
              <span className="text-xs text-muted-foreground shrink-0">
                +{remainder}
              </span>
            )}
          </div>
          <ChevronDown className="size-4 shrink-0 text-muted-foreground" />
        </button>
      </PopoverTrigger>
      <PopoverContent
        className="w-[var(--radix-popover-trigger-width)] p-0"
        align="start"
      >
        <div className="p-2 pb-0">
          <Input
            ref={inputRef}
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder={searchPlaceholder}
            className="h-8 text-xs"
          />
        </div>
        <div className="max-h-56 overflow-y-auto p-1">
          {filtered.length === 0 ? (
            <p className="px-2 py-4 text-xs text-center text-muted-foreground">
              {emptyText}
            </p>
          ) : (
            filtered.map((o) => (
              <label
                key={o.value}
                className={cn(
                  'flex items-center gap-2 rounded-md px-2 py-1.5 text-sm cursor-pointer',
                  'hover:bg-muted transition-colors',
                )}
              >
                <Checkbox
                  checked={selected.includes(o.value)}
                  onCheckedChange={() => toggle(o.value)}
                />
                <div className="flex flex-col min-w-0 flex-1">
                  <span className="truncate font-medium">{o.label}</span>
                  {o.description && (
                    <span className="text-xs text-muted-foreground truncate">
                      {o.description}
                    </span>
                  )}
                </div>
              </label>
            ))
          )}
        </div>
        {selected.length > 0 && (
          <div className="border-t p-1.5 flex justify-between items-center">
            <span className="text-xs text-muted-foreground ml-1">
              Выбрано: {selected.length}
            </span>
            <Button
              variant="ghost"
              size="xs"
              onClick={clearAll}
            >
              <X className="size-3 mr-1" />
              Очистить
            </Button>
          </div>
        )}
      </PopoverContent>
    </Popover>
  )
}
