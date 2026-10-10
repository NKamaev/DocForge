import { Switch } from '@/components/ui/switch'
import { cn } from '@/lib/utils'

interface Props {
  value: unknown
  onChange: (val: unknown) => void
  disabled?: boolean
}

export default function BooleanField({ value, onChange, disabled }: Props) {
  const checked = value === true || value === 'true'

  return (
    <div className="flex items-center gap-3">
      <Switch
        checked={checked}
        onCheckedChange={v => onChange(v)}
        disabled={disabled}
      />
      <span className={cn(
        'text-sm',
        checked ? 'text-foreground' : 'text-muted-foreground'
      )}>
        {checked ? 'Да' : 'Нет'}
      </span>
    </div>
  )
}
