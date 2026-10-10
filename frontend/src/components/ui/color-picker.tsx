import { useState, useEffect, useRef } from 'react'
import { Button } from '@/components/ui/button'
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from '@/components/ui/popover'
import { Eraser } from 'lucide-react'
import { cn } from '@/lib/utils'

interface ColorPickerProps {
  value?: string
  onChange?: (color: string) => void
  onClear?: () => void
  allowClear?: boolean
  className?: string
  'data-testid'?: string
}

export function ColorPicker({
  value,
  onChange,
  onClear,
  allowClear = false,
  className,
  'data-testid': testId,
  ...rest
}: ColorPickerProps) {
  const [open, setOpen] = useState(false)
  const [localColor, setLocalColor] = useState(value || '#000000')
  const inputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    if (value) {
      setLocalColor(value)
    }
  }, [value])

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const color = e.target.value
    setLocalColor(color)
    onChange?.(color)
  }

  const handleClear = () => {
    setLocalColor('#000000')
    onClear?.()
    setOpen(false)
  }

  return (
    <Popover open={open} onOpenChange={setOpen} {...rest}>
      <PopoverTrigger asChild>
        <Button
          variant="ghost"
          size="icon-sm"
          className={cn("h-8 w-8", className)}
          data-testid={testId}
        >
          <div
            className="size-4 rounded-sm border border-border"
            style={{ backgroundColor: value || 'transparent' }}
          />
        </Button>
      </PopoverTrigger>
      <PopoverContent className="w-64 p-3" align="start">
        <div className="space-y-3" data-theme="light">
          <input
            ref={inputRef}
            type="color"
            value={localColor}
            onChange={handleChange}
            className="w-full h-24 rounded-md cursor-pointer border-0 p-0"
            style={{ colorScheme: 'light' }}
          />
          {allowClear && value && (
            <Button
              variant="ghost"
              size="sm"
              className="w-full text-muted-foreground"
              onClick={handleClear}
            >
              <Eraser className="size-4 mr-2" />
              Сбросить
            </Button>
          )}
        </div>
      </PopoverContent>
    </Popover>
  )
}
