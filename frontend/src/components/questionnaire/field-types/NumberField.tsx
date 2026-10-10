import { Input } from '@/components/ui/input'
import { Button } from '@/components/ui/button'
import { Icon } from '@/components/Icon'
import { cn } from '@/lib/utils'

interface Props {
  value: unknown
  onChange: (val: unknown) => void
  disabled?: boolean
}

function formatNumber(value: number | undefined): string {
  if (value === undefined || value === null) return ''
  const num = Number(value)
  if (isNaN(num)) return ''
  return num.toString().replace('.', ',')
}

function parseNumber(str: string): number | undefined {
  if (!str) return undefined
  const normalized = str.replace(',', '.')
  const num = Number(normalized)
  return isNaN(num) ? undefined : num
}

export default function NumberField({ value, onChange, disabled }: Props) {
  const numVal = typeof value === 'number' ? value
    : typeof value === 'string' && value !== '' ? Number(value)
    : undefined

  const displayValue = formatNumber(numVal)

  return (
    <div className="relative w-full">
      <Input
    type="text"
    inputMode="decimal"
    value={displayValue}
    onChange={e => {
      const val = parseNumber(e.target.value)
      onChange(val)
    }}
    disabled={disabled}
    className={cn(numVal !== undefined && numVal !== null && !disabled && 'pr-8')}
  />
  {numVal !== undefined && numVal !== null && !disabled && (
    <Button
      type="button"
      variant="ghost"
      size="icon-sm"
      className="absolute right-1 top-1/2 -translate-y-1/2 h-6 w-6 hover:bg-muted"
      onClick={() => onChange(undefined)}
      tabIndex={-1}
    >
      <Icon name="x" size={14} className="text-muted-foreground" />
    </Button>
  )}
</div>
  )
}
