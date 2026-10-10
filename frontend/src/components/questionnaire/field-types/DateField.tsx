import { DatePicker } from '@/components/ui/date-picker'
import { Button } from '@/components/ui/button'
import { Icon } from '@/components/Icon'
import { cn } from '@/lib/utils'

interface Props {
  value: unknown
  onChange: (val: unknown) => void
  disabled?: boolean
}

export default function DateField({ value, onChange, disabled }: Props) {
  const stringValue = typeof value === 'string' && value ? value : undefined

  return (
    <div className="relative w-full">
      <DatePicker
        value={stringValue}
        onChange={date => onChange(date || null)}
        className={cn('w-full', stringValue && !disabled && 'pr-10')}
      />
      {stringValue && !disabled && (
        <Button
          type="button"
          variant="ghost"
          size="icon-sm"
          className="absolute right-1 top-1/2 -translate-y-1/2 h-6 w-6 hover:bg-muted z-10"
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
