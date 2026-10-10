import { Input } from '@/components/ui/input'
import { cn } from '@/lib/utils'

interface Props {
  value: unknown
  onChange: (val: unknown) => void
  disabled?: boolean
  className?: string
}

export default function FioField({ value, onChange, disabled, className }: Props) {
  const str = typeof value === 'string' ? value : ''
  const parts = str.split(' ')
  const lastName  = parts[0] ?? ''
  const firstName = parts[1] ?? ''
  const midName   = parts.slice(2).join(' ')

  const emit = (ln: string, fn: string, mn: string) => {
    const combined = [ln, fn, mn].filter(Boolean).join(' ')
    onChange(combined || undefined)
  }

  return (
    <div className={cn('flex flex-col gap-2 w-full', className)}>
      <Input
        placeholder="Фамилия"
        value={lastName}
        onChange={e => emit(e.target.value, firstName, midName)}
        disabled={disabled}
      />
      <Input
        placeholder="Имя"
        value={firstName}
        onChange={e => emit(lastName, e.target.value, midName)}
        disabled={disabled}
      />
      <Input
        placeholder="Отчество"
        value={midName}
        onChange={e => emit(lastName, firstName, e.target.value)}
        disabled={disabled}
      />
    </div>
  )
}
