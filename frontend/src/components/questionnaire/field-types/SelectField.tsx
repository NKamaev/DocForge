import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Icon } from '@/components/Icon'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'

interface Props {
  value: unknown
  onChange: (val: unknown) => void
  options: string[] | null
  disabled?: boolean
}

export default function SelectField({ value, onChange, options, disabled }: Props) {
  const stringValue = typeof value === 'string' ? value : ''
  const optionsList = options ?? []

  return (
    <div className="relative w-full">
      <Select
        value={stringValue}
        onValueChange={v => onChange(v || null)}
        disabled={disabled}
      >
        <SelectTrigger
          className={cn('w-full', stringValue && !disabled && 'pr-10')}
        >
          <SelectValue placeholder="Выберите значение" />
        </SelectTrigger>
        <SelectContent>
          {optionsList.length === 0 ? (
            <SelectItem value="__empty__" disabled>Нет доступных значений</SelectItem>
          ) : (
            optionsList.map(opt => (
              <SelectItem key={opt} value={opt}>{opt}</SelectItem>
            ))
          )}
        </SelectContent>
      </Select>
      {stringValue && !disabled && (
        <Button
          type="button"
          variant="ghost"
          size="icon-sm"
          className="absolute right-7 top-1/2 -translate-y-1/2 h-6 w-6 hover:bg-muted z-10"
          onClick={e => {
            e.preventDefault()
            e.stopPropagation()
            onChange(null)
          }}
          tabIndex={-1}
        >
          <Icon name="x" size={14} className="text-muted-foreground" />
        </Button>
      )}
    </div>
  )
}
