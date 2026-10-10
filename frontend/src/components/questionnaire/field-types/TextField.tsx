import { Input } from '@/components/ui/input'
import { Button } from '@/components/ui/button'
import { Icon } from '@/components/Icon'
import { cn } from '@/lib/utils'

interface Props {
  value: unknown
  onChange: (val: unknown) => void
  disabled?: boolean
  allowClear?: boolean
  className?: string
  placeholder?: string
}

export default function TextField({ 
  value, 
  onChange, 
  disabled, 
  allowClear = true,
  className,
  placeholder
}: Props) {
  const stringValue = typeof value === 'string' ? value : ''
  const showClear = allowClear && stringValue && !disabled

  return (
    <div className={cn('relative w-full', className)}>
      <Input
        value={stringValue}
        onChange={e => onChange(e.target.value)}
        disabled={disabled}
        placeholder={placeholder}
        className={cn(showClear && 'pr-8')}
      />
      {showClear && (
        <Button
          type="button"
          variant="ghost"
          size="icon-sm"
          className="absolute right-1 top-1/2 -translate-y-1/2 h-6 w-6 hover:bg-muted"
          onClick={() => onChange('')}
          tabIndex={-1}
        >
          <Icon name="x" size={14} className="text-muted-foreground" />
        </Button>
      )}
    </div>
  )
}
